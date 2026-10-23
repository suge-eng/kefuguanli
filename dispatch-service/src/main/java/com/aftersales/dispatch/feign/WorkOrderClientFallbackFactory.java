package com.aftersales.dispatch.feign;

import com.aftersales.common.result.Result;
import com.aftersales.dispatch.feign.dto.DispatchRequestDto;
import com.aftersales.dispatch.feign.dto.WorkOrderDetailVOView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 工单中心 Feign 降级工厂
 * 设计思路：派单回写是同步强依赖（回写失败本次派单就无效），
 * 降级统一返回失败，由调用方抛异常交给 MQ 重投，避免「派了但没落库」的静默不一致
 */
@Slf4j
@Component
public class WorkOrderClientFallbackFactory implements FallbackFactory<WorkOrderClient> {

    @Override
    public WorkOrderClient create(Throwable cause) {
        return new WorkOrderClient() {

            @Override
            public Result<WorkOrderDetailVOView> detail(Long id) {
                log.warn("工单中心详情调用被熔断降级: id={}, err={}", id, cause.getMessage());
                return Result.fail("工单中心暂不可用（查询详情），请稍后重试");
            }

            @Override
            public Result<Void> dispatch(Long id, DispatchRequestDto request) {
                log.warn("工单中心派单回写被熔断降级: id={}, err={}", id, cause.getMessage());
                return Result.fail("工单中心暂不可用（派单回写），稍后重试");
            }
        };
    }
}
