package com.aftersales.dispatch.strategy;

import com.aftersales.dispatch.entity.CustomerService;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 最小负载派单：优先分给当前在办工单最少的客服
 * 大促峰值时各客服技能被打满，负载均衡比技能匹配更能防堆积
 */
@Component("LEAST_LOAD")
public class LeastLoadDispatchStrategy implements DispatchStrategy {

    @Override
    public String code() {
        return "LEAST_LOAD";
    }

    @Override
    public Long select(List<CustomerService> candidates, DispatchContext ctx) {
        return candidates.stream()
                .min(Comparator.comparingInt(cs -> ctx.getLoads().getOrDefault(cs.getId(), 0)))
                .orElseThrow(() -> new IllegalStateException("候选客服为空"))
                .getId();
    }
}
