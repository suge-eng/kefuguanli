package com.aftersales.workbench.feign;

import com.aftersales.common.result.PageResult;
import com.aftersales.common.result.Result;
import com.aftersales.workbench.feign.dto.AttachmentAppendRequestDto;
import com.aftersales.workbench.feign.dto.HandlerRequestDto;
import com.aftersales.workbench.feign.dto.ReplyRequestDto;
import com.aftersales.workbench.feign.dto.TransferRequestDto;
import com.aftersales.workbench.feign.dto.WorkOrderDetailVOView;
import com.aftersales.workbench.feign.dto.WorkOrderView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 工单中心 Feign 客户端（工作台全套操作）
 * 设计思路：工作台不直接读工单库，所有操作走工单中心接口，
 * 状态机校验、操作留痕、事件广播只在工单中心一处实现，避免双写不一致
 */
@FeignClient(name = "work-order-service", path = "/work-order",
        fallbackFactory = WorkOrderClientFallbackFactory.class)
public interface WorkOrderClient {

    /** 分页查询（我的工单：按 handlerId 过滤） */
    @GetMapping("/page")
    Result<PageResult<WorkOrderView>> page(@RequestParam("pageNum") Long pageNum,
                                           @RequestParam("pageSize") Long pageSize,
                                           @RequestParam(value = "status", required = false) String status,
                                           @RequestParam(value = "handlerId", required = false) Long handlerId);

    /** 工单详情（含操作日志/回复记录） */
    @GetMapping("/{id}")
    Result<WorkOrderDetailVOView> detail(@PathVariable("id") Long id);

    /** 领取工单 */
    @PutMapping("/{id}/claim")
    Result<Void> claim(@PathVariable("id") Long id, @RequestBody HandlerRequestDto request);

    /** 回复工单 */
    @PostMapping("/{id}/reply")
    Result<Void> reply(@PathVariable("id") Long id, @RequestBody ReplyRequestDto request);

    /** 标记已解决 */
    @PutMapping("/{id}/resolve")
    Result<Void> resolve(@PathVariable("id") Long id, @RequestBody HandlerRequestDto request);

    /** 转交工单 */
    @PutMapping("/{id}/transfer")
    Result<Void> transfer(@PathVariable("id") Long id, @RequestBody TransferRequestDto request);

    /** 关闭工单 */
    @PutMapping("/{id}/close")
    Result<Void> close(@PathVariable("id") Long id,
                       @RequestParam("operatorId") String operatorId,
                       @RequestParam("reason") String reason);

    /** 追加工单附件 */
    @PostMapping("/{id}/attachments")
    Result<Void> appendAttachments(@PathVariable("id") Long id, @RequestBody AttachmentAppendRequestDto request);
}
