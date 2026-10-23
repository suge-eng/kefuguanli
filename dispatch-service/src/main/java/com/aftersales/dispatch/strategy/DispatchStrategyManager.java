package com.aftersales.dispatch.strategy;

import com.aftersales.dispatch.entity.DispatchStrategyConfig;
import com.aftersales.dispatch.mapper.DispatchStrategyConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 派单策略管理器
 * 设计思路：Spring 把所有 DispatchStrategy 实现按 Bean 名注入 Map，
 * 策略编码即 Bean 名，查配置表拿到当前启用的编码即可路由到对应实现；
 * 每次派单实时查库（派单 QPS 低，可接受；生产可加缓存 + 配置变更广播），
 * 配置缺失时兜底 RANDOM，保证派单链路永不断
 */
@Component
@RequiredArgsConstructor
public class DispatchStrategyManager {

    private final DispatchStrategyConfigMapper configMapper;

    /** key = 策略实现类的 Bean 名（即策略编码） */
    private final Map<String, DispatchStrategy> strategyMap;

    /** 获取当前启用的派单策略 */
    public DispatchStrategy currentStrategy() {
        DispatchStrategyConfig config = configMapper.selectOne(
                new LambdaQueryWrapper<DispatchStrategyConfig>()
                        .eq(DispatchStrategyConfig::getEnabled, 1));
        if (config != null && strategyMap.containsKey(config.getStrategyCode())) {
            return strategyMap.get(config.getStrategyCode());
        }
        return strategyMap.get("RANDOM");
    }
}
