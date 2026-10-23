package com.aftersales.ai.feign;

import com.aftersales.ai.feign.dto.AiDraftRequestDto;
import com.aftersales.ai.feign.dto.StructureRequestDto;
import com.aftersales.ai.feign.dto.WorkOrderDetailVOView;
import com.aftersales.ai.feign.dto.WorkOrderView;
import com.aftersales.common.result.PageResult;
import com.aftersales.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 工单中心 Feign 降级工厂
 * 设计思路：熔断打开或调用异常时返回失败 Result 而不是抛原始异常，
 * 让业务方沿用既有的「code != 200」判断路径，Fallback 只负责兜底不改变业务分支
 */
@Slf4j
@Component
public class WorkOrderClientFallbackFactory implements FallbackFactory<WorkOrderClient> {

    @Override
    public WorkOrderClient create(Throwable cause) {
        return new WorkOrderClient() {

            @Override
            public Result<WorkOrderDetailVOView> detail(Long id) {
                return fail("查询工单详情", cause);
            }

            @Override
            public Result<Void> structure(Long id, StructureRequestDto dto) {
                return fail("结构化回写", cause);
            }

            @Override
            public Result<Void> aiDraft(Long id, AiDraftRequestDto dto) {
                return fail("AI草稿回写", cause);
            }

            @Override
            public Result<Void> escalate(Long id, String operatorId, String reason) {
                return fail("工单升级", cause);
            }

            @Override
            public Result<PageResult<WorkOrderView>> page(Long pageNum, Long pageSize, String status,
                                                          String workType, Long handlerId, Long userId) {
                return fail("工单分页查询", cause);
            }
        };
    }

    private <T> Result<T> fail(String action, Throwable cause) {
        log.warn("工单中心调用被熔断降级: action={}, err={}", action, cause.getMessage());
        return Result.fail("工单中心暂不可用（" + action + "），请稍后重试");
    }
}
