package com.aftersales.dispatch.feign;

import com.aftersales.common.result.Result;
import com.aftersales.dispatch.feign.dto.DispatchRequestDto;
import com.aftersales.dispatch.feign.dto.WorkOrderDetailVOView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 工单中心 Feign 客户端
 * 设计思路：派单结果用 Feign 同步回写而非 MQ 事件——派单需要立刻知道
 * 回写是否成功（失败则本次派单作废可重试），同步调用的结果确定性更合适
 */
@FeignClient(name = "work-order-service", path = "/work-order",
        fallbackFactory = WorkOrderClientFallbackFactory.class)
public interface WorkOrderClient {

    /** 查询工单详情（派单前校验状态 + 技能匹配取内容） */
    @GetMapping("/{id}")
    Result<WorkOrderDetailVOView> detail(@PathVariable("id") Long id);

    /** 派单回写（首派/超时转派共用，工单中心按当前状态区分动作） */
    @PutMapping("/{id}/dispatch")
    Result<Void> dispatch(@PathVariable("id") Long id, @RequestBody DispatchRequestDto request);
}
