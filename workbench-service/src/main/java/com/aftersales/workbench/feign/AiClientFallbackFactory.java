package com.aftersales.workbench.feign;

import com.aftersales.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * AI 服务 Feign 降级工厂
 * 设计思路：AI 草稿是辅助能力，不可用时不能让客服无法处理工单——
 * 降级返回明确提示，客服可直接手工编写回复，主流程不受影响
 */
@Slf4j
@Component
public class AiClientFallbackFactory implements FallbackFactory<AiClient> {

    @Override
    public AiClient create(Throwable cause) {
        return workOrderId -> {
            log.warn("AI草稿生成被熔断降级: workOrderId={}, err={}", workOrderId, cause.getMessage());
            return Result.<String>fail("AI 服务暂不可用，请手工编写回复");
        };
    }
}
