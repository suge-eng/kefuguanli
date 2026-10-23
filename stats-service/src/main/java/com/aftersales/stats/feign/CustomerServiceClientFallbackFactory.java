package com.aftersales.stats.feign;

import com.aftersales.common.result.PageResult;
import com.aftersales.common.result.Result;
import com.aftersales.stats.feign.dto.CustomerServiceView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 派单服务 Feign 降级工厂
 * 设计思路：看板客服负载是四个区块之一，降级返回空列表让其他区块照常展示，
 * 不因为一个依赖故障整页 500
 */
@Slf4j
@Component
public class CustomerServiceClientFallbackFactory implements FallbackFactory<CustomerServiceClient> {

    @Override
    public CustomerServiceClient create(Throwable cause) {
        return (pageNum, pageSize) -> {
            log.warn("客服列表读取被熔断降级: err={}", cause.getMessage());
            return Result.<PageResult<CustomerServiceView>>fail("派单服务暂不可用，客服负载数据暂缺");
        };
    }
}
