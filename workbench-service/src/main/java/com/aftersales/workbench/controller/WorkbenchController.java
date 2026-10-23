package com.aftersales.workbench.controller;

import com.aftersales.common.exception.BizException;
import com.aftersales.common.result.PageResult;
import com.aftersales.common.result.Result;
import com.aftersales.workbench.feign.AiClient;
import com.aftersales.workbench.feign.WorkOrderClient;
import com.aftersales.workbench.feign.dto.AttachmentAppendRequestDto;
import com.aftersales.workbench.feign.dto.HandlerRequestDto;
import com.aftersales.workbench.feign.dto.ReplyRequestDto;
import com.aftersales.workbench.feign.dto.TransferRequestDto;
import com.aftersales.workbench.feign.dto.WorkOrderDetailVOView;
import com.aftersales.workbench.feign.dto.WorkOrderView;
import com.aftersales.workbench.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客服工作台接口
 * 设计思路：工作台只做参数组装和结果透传，业务规则（状态机/归属校验/留痕）
 * 全部在工单中心落地；Feign 返回的业务错误（code!=200）转成异常抛出，
 * 由全局异常处理器统一返回，客服能看到工单中心的具体拒绝原因
 */
@Tag(name = "客服工作台")
@RestController
@RequestMapping("/workbench")
@RequiredArgsConstructor
public class WorkbenchController {

    private final WorkOrderClient workOrderClient;
    private final AttachmentService attachmentService;
    private final AiClient aiClient;

    @Operation(summary = "我的工单分页")
    @GetMapping("/my-page")
    public Result<PageResult<WorkOrderView>> myPage(@RequestParam Long handlerId,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(defaultValue = "1") Long pageNum,
                                                    @RequestParam(defaultValue = "10") Long pageSize) {
        return workOrderClient.page(pageNum, pageSize, status, handlerId);
    }

    @Operation(summary = "工单详情（含回复记录）")
    @GetMapping("/detail/{id}")
    public Result<WorkOrderDetailVOView> detail(@PathVariable Long id) {
        return workOrderClient.detail(id);
    }

    @Operation(summary = "领取工单")
    @PutMapping("/claim/{id}")
    public Result<Void> claim(@PathVariable Long id, @RequestParam Long handlerId) {
        return check(workOrderClient.claim(id, new HandlerRequestDto(handlerId)));
    }

    @Operation(summary = "回复工单")
    @PostMapping("/reply/{id}")
    public Result<Void> reply(@PathVariable Long id, @Valid @RequestBody WorkbenchReplyRequest request) {
        return check(workOrderClient.reply(id, new ReplyRequestDto(request.getHandlerId(), request.getContent())));
    }

    @Operation(summary = "标记已解决")
    @PutMapping("/resolve/{id}")
    public Result<Void> resolve(@PathVariable Long id, @RequestParam Long handlerId) {
        return check(workOrderClient.resolve(id, new HandlerRequestDto(handlerId)));
    }

    @Operation(summary = "转交工单")
    @PutMapping("/transfer/{id}")
    public Result<Void> transfer(@PathVariable Long id, @Valid @RequestBody TransferRequestDto request) {
        return check(workOrderClient.transfer(id, request));
    }

    @Operation(summary = "关闭工单")
    @PutMapping("/close/{id}")
    public Result<Void> close(@PathVariable Long id,
                              @RequestParam(defaultValue = "system") String operatorId,
                              @RequestParam(defaultValue = "人工关闭") String reason) {
        return check(workOrderClient.close(id, operatorId, reason));
    }

    @Operation(summary = "批量领取")
    @PostMapping("/batch-claim")
    public Result<Map<String, Object>> batchClaim(@Valid @RequestBody BatchClaimRequest request) {
        // 逐单调用工单中心领取，单失败不影响其他单，成败明细返回给前端提示
        List<Long> successIds = new ArrayList<>();
        Map<Long, String> failures = new LinkedHashMap<>();
        for (Long id : request.getIds()) {
            try {
                check(workOrderClient.claim(id, new HandlerRequestDto(request.getHandlerId())));
                successIds.add(id);
            } catch (Exception e) {
                failures.put(id, e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("successIds", successIds);
        result.put("failures", failures);
        return Result.success(result);
    }

    @Operation(summary = "上传工单附件")
    @PostMapping("/attachment/upload")
    public Result<String> uploadAttachment(@RequestParam Long workOrderId,
                                           @RequestParam(defaultValue = "unknown") String operatorId,
                                           @RequestPart("file") MultipartFile file) {
        // 1. 文件存 MinIO；2. 对象名回写工单详情（附件与工单关联由工单中心维护）
        String objectName = attachmentService.upload(workOrderId, file);
        check(workOrderClient.appendAttachments(workOrderId,
                new AttachmentAppendRequestDto(List.of(objectName), operatorId)));
        return Result.success(objectName);
    }

    @Operation(summary = "生成AI回复草稿（RAG召回+大模型生成，客服审核后再发送）")
    @PostMapping("/reply-draft/{id}")
    public Result<String> replyDraft(@PathVariable Long id) {
        // AI 生成失败/降级时 ai-service 会返回具体原因，透传给客服
        Result<String> result = aiClient.draft(id);
        if (result == null || result.getCode() == null || result.getCode() != 200) {
            throw new BizException(result == null ? "AI服务无响应" : result.getMessage());
        }
        return result;
    }

    /** Feign 返回的业务错误码转成异常，让客服看到工单中心的具体拒绝原因 */
    private Result<Void> check(Result<Void> result) {
        if (result == null || result.getCode() == null || result.getCode() != 200) {
            throw new BizException(result == null ? "工单中心无响应" : result.getMessage());
        }
        return result;
    }

    /** 工作台回复请求 */
    @Data
    public static class WorkbenchReplyRequest {
        @NotNull(message = "处理人ID不能为空")
        private Long handlerId;
        @NotNull(message = "回复内容不能为空")
        private String content;
    }

    /** 批量领取请求 */
    @Data
    public static class BatchClaimRequest {
        @NotEmpty(message = "工单ID列表不能为空")
        private List<Long> ids;
        @NotNull(message = "处理人ID不能为空")
        private Long handlerId;
    }
}
