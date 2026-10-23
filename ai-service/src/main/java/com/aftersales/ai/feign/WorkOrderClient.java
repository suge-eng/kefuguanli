package com.aftersales.ai.feign;

import com.aftersales.ai.feign.dto.AiDraftRequestDto;
import com.aftersales.ai.feign.dto.StructureRequestDto;
import com.aftersales.ai.feign.dto.WorkOrderDetailVOView;
import com.aftersales.ai.feign.dto.WorkOrderView;
import com.aftersales.common.result.PageResult;
import com.aftersales.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 工单中心 Feign 客户端
 * 设计思路：AI 各 Agent 的提取/草稿/升级结果用 Feign 同步回写——
 * 需要立刻知道回写是否成功（失败抛异常让 MQ 重试），同步调用结果确定性更合适
 */
@FeignClient(name = "work-order-service", path = "/work-order",
        fallbackFactory = WorkOrderClientFallbackFactory.class)
public interface WorkOrderClient {

    /** 查询工单详情（提取/生成草稿/评估预警前取工单与诉求原文） */
    @GetMapping("/{id}")
    Result<WorkOrderDetailVOView> detail(@PathVariable("id") Long id);

    /** AI 结构化提取结果回写 */
    @PutMapping("/{id}/structure")
    Result<Void> structure(@PathVariable("id") Long id, @RequestBody StructureRequestDto dto);

    /** AI 回复草稿回写 */
    @PutMapping("/{id}/ai-draft")
    Result<Void> aiDraft(@PathVariable("id") Long id, @RequestBody AiDraftRequestDto dto);

    /** 工单升级为 URGENT（预警 Agent 命中 ESCALATE 动作时调用） */
    @PutMapping("/{id}/escalate")
    Result<Void> escalate(@PathVariable("id") Long id,
                          @RequestParam("operatorId") String operatorId,
                          @RequestParam("reason") String reason);

    /** 分页查询工单（REPEAT 重复诉求预警统计用户近期工单数用） */
    @GetMapping("/page")
    Result<PageResult<WorkOrderView>> page(@RequestParam("pageNum") Long pageNum,
                                           @RequestParam("pageSize") Long pageSize,
                                           @RequestParam(value = "status", required = false) String status,
                                           @RequestParam(value = "workType", required = false) String workType,
                                           @RequestParam(value = "handlerId", required = false) Long handlerId,
                                           @RequestParam(value = "userId", required = false) Long userId);
}
