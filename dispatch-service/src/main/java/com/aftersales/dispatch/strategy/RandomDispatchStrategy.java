package com.aftersales.dispatch.strategy;

import com.aftersales.dispatch.entity.CustomerService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机派单：兜底策略
 * 任何策略异常/无配置时用它兜底，保证派单链路永不断
 */
@Component("RANDOM")
public class RandomDispatchStrategy implements DispatchStrategy {

    @Override
    public String code() {
        return "RANDOM";
    }

    @Override
    public Long select(List<CustomerService> candidates, DispatchContext ctx) {
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())).getId();
    }
}
