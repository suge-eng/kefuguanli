package com.aftersales.workorder.controller;

import com.aftersales.common.enums.OperatorTypeEnum;
import com.aftersales.common.result.PageResult;
import com.aftersales.common.result.Result;
import com.aftersales.workorder.dto.AiDraftRequest;
import com.aftersales.workorder.dto.AttachmentAppendRequest;
import com.aftersales.workorder.dto.ClaimRequest;
import com.aftersales.workorder.dto.DispatchRequest;
import com.aftersales.workorder.dto.ReplyRequest;
import com.aftersales.workorder.dto.StructureRequest;
import com.aftersales.workorder.dto.TransferRequest;
import com.aftersales.workorder.dto.WorkOrderCreateRequest;
import com.aftersales.workorder.entity.WorkOrder;
import com.aftersales.workorder.service.WorkOrderService;
import com.aftersales.workorder.vo.WorkOrderDetailVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 工单中心接口
 */
@Tag(name = "工单中心")
@RestController
@RequestMapping("/work-order")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    @Operation(summary = "创建工单")
    @PostMapping("/create")
    public Result<WorkOrder> create(@Valid @RequestBody WorkOrderCreateRequest request) {
        return Result.success(workOrderService.create(request));
    }

    @Operation(summary = "分页查询工单列表")
    @GetMapping("/page")
    public Result<PageResult<WorkOrder>> page(@RequestParam(defaultValue = "1") Long pageNum,
                                              @RequestParam(defaultValue = "10") Long pageSize,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String workType,
                                              @RequestParam(required = false) Long handlerId,
                                              @RequestParam(required = false) Long userId) {
        return Result.success(workOrderService.pageQuery(pageNum, pageSize, status, workType, handlerId, userId));
    }

    @Operation(summary = "查询工单详情")
    @GetMapping("/{id}")
    public Result<WorkOrderDetailVO> detail(@PathVariable Long id) {
        return Result.success(workOrderService.getDetail(id));
    }

    @Operation(summary = "变更工单状态（状态机校验）")
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id,
                                     @RequestParam String targetStatus,
                                     @RequestParam(required = false, defaultValue = "system") String operatorId,
                                     @RequestParam(required = false, defaultValue = "STATUS_CHANGE") String remark) {
        workOrderService.changeStatus(id, targetStatus, operatorId,
                OperatorTypeEnum.SYSTEM.name(), "STATUS_CHANGE", remark);
        return Result.success();
    }

    // ==================== 第二阶段：业务操作接口（派单回写 + 工作台操作） ====================

    @Operation(summary = "分派回写（派单服务调用，首派/超时转派共用）")
    @PutMapping("/{id}/dispatch")
    public Result<Void> dispatch(@PathVariable Long id, @Valid @RequestBody DispatchRequest request) {
        workOrderService.dispatch(id, request);
        return Result.success();
    }

    @Operation(summary = "客服领取工单")
    @PutMapping("/{id}/claim")
    public Result<Void> claim(@PathVariable Long id, @Valid @RequestBody ClaimRequest request) {
        workOrderService.claim(id, request.getHandlerId());
        return Result.success();
    }

    @Operation(summary = "客服回复工单")
    @PostMapping("/{id}/reply")
    public Result<Void> reply(@PathVariable Long id, @Valid @RequestBody ReplyRequest request) {
        workOrderService.reply(id, request);
        return Result.success();
    }

    @Operation(summary = "客服标记已解决")
    @PutMapping("/{id}/resolve")
    public Result<Void> resolve(@PathVariable Long id, @Valid @RequestBody ClaimRequest request) {
        workOrderService.resolve(id, request.getHandlerId());
        return Result.success();
    }

    @Operation(summary = "工单转交")
    @PutMapping("/{id}/transfer")
    public Result<Void> transfer(@PathVariable Long id, @Valid @RequestBody TransferRequest request) {
        workOrderService.transfer(id, request);
        return Result.success();
    }

    @Operation(summary = "关闭工单")
    @PutMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id,
                              @RequestParam(required = false, defaultValue = "system") String operatorId,
                              @RequestParam(required = false, defaultValue = "人工关闭") String reason) {
        workOrderService.close(id, operatorId, reason);
        return Result.success();
    }

    @Operation(summary = "追加工单附件")
    @PostMapping("/{id}/attachments")
    public Result<Void> appendAttachments(@PathVariable Long id, @Valid @RequestBody AttachmentAppendRequest request) {
        workOrderService.appendAttachments(id, request);
        return Result.success();
    }

    // ==================== 第三阶段：AI Agent 回写接口 ====================

    @Operation(summary = "AI结构化结果回写（ai-service 结构化Agent调用）")
    @PutMapping("/{id}/structure")
    public Result<Void> applyStructured(@PathVariable Long id, @RequestBody StructureRequest request) {
        workOrderService.applyStructured(id, request);
        return Result.success();
    }

    @Operation(summary = "AI回复草稿回写（ai-service 回复Agent调用）")
    @PutMapping("/{id}/ai-draft")
    public Result<Void> saveAiDraft(@PathVariable Long id, @Valid @RequestBody AiDraftRequest request) {
        workOrderService.saveAiDraft(id, request);
        return Result.success();
    }

    @Operation(summary = "工单升级为URGENT（预警Agent调用）")
    @PutMapping("/{id}/escalate")
    public Result<Void> escalate(@PathVariable Long id,
                                 @RequestParam(defaultValue = "ai-warning-agent") String operatorId,
                                 @RequestParam String reason) {
        workOrderService.escalate(id, operatorId, reason);
        return Result.success();
    }
}
