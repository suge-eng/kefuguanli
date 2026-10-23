package com.aftersales.dispatch.strategy;

import com.aftersales.dispatch.entity.CustomerService;
import com.aftersales.dispatch.entity.DispatchStrategyConfig;
import com.aftersales.dispatch.mapper.DispatchStrategyConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 综合加权派单（默认策略）：技能匹配度40% + 当前负载30% + 历史解决率20% + 在线时长10%
 * 设计思路：
 * 1. 各因子归一化到 0~1 再加权求和，避免量纲差异导致某一因子失真
 * 2. 权重从策略配置表 weights 字段读取（JSON），运营调权不用改代码；
 *    每次派单实时查库，演示项目派单 QPS 极低可接受，生产可加本地缓存定时刷新
 */
@Slf4j
@Component("WEIGHTED")
@RequiredArgsConstructor
public class WeightedDispatchStrategy implements DispatchStrategy {

    /** 默认权重：配置表缺失/解析失败时兜底 */
    private static final double DEFAULT_SKILL = 0.4;
    private static final double DEFAULT_LOAD = 0.3;
    private static final double DEFAULT_RESOLVE_RATE = 0.2;
    private static final double DEFAULT_ONLINE_HOURS = 0.1;

    private final DispatchStrategyConfigMapper configMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String code() {
        return "WEIGHTED";
    }

    @Override
    public Long select(List<CustomerService> candidates, DispatchContext ctx) {
        Map<String, Double> weights = loadWeights();
        String text = buildMatchText(ctx);

        CustomerService best = null;
        double bestScore = -1;
        for (CustomerService cs : candidates) {
            double score = weights.get("skill") * skillScore(cs, text)
                    + weights.get("load") * loadScore(cs, ctx)
                    + weights.get("resolveRate") * resolveRateScore(cs)
                    + weights.get("onlineHours") * onlineHoursScore(cs);
            if (score > bestScore) {
                bestScore = score;
                best = cs;
            }
        }
        return best.getId();
    }

    /** 技能分：命中标签数 / 客服标签总数（0~1） */
    private double skillScore(CustomerService cs, String text) {
        if (!StringUtils.hasText(cs.getSkillTags())) {
            return 0;
        }
        List<String> tags = Arrays.stream(cs.getSkillTags().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        if (tags.isEmpty()) {
            return 0;
        }
        long hits = tags.stream().filter(text::contains).count();
        return (double) hits / tags.size();
    }

    /** 负载分：1 - 当前负载/最大负载，越空闲分越高 */
    private double loadScore(CustomerService cs, DispatchContext ctx) {
        if (cs.getMaxLoad() == null || cs.getMaxLoad() <= 0) {
            return 0;
        }
        int load = ctx.getLoads().getOrDefault(cs.getId(), 0);
        return 1.0 - (double) load / cs.getMaxLoad();
    }

    /** 解决率分：历史解决率本身就是 0~1 的小数，直接用 */
    private double resolveRateScore(CustomerService cs) {
        return cs.getResolveRate() == null ? 0 : cs.getResolveRate().doubleValue();
    }

    /**
     * 在线时长分：固定 1.0
     * 说明：真实在线时长需要客服端心跳采集统计，演示项目简化处理；
     * 预留该因子是为了权重配置结构与生产方案对齐，面试可讲演进路径
     */
    private double onlineHoursScore(CustomerService cs) {
        return 1.0;
    }

    private String buildMatchText(DispatchContext ctx) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(ctx.getWorkType())) {
            sb.append(ctx.getWorkType());
        }
        if (StringUtils.hasText(ctx.getTitle())) {
            sb.append(' ').append(ctx.getTitle());
        }
        if (StringUtils.hasText(ctx.getContent())) {
            sb.append(' ').append(ctx.getContent());
        }
        return sb.toString();
    }

    /** 从配置表读权重 JSON，缺失/异常时用默认权重兜底 */
    private Map<String, Double> loadWeights() {
        Map<String, Double> defaults = Map.of(
                "skill", DEFAULT_SKILL,
                "load", DEFAULT_LOAD,
                "resolveRate", DEFAULT_RESOLVE_RATE,
                "onlineHours", DEFAULT_ONLINE_HOURS);
        try {
            DispatchStrategyConfig config = configMapper.selectOne(
                    new LambdaQueryWrapper<DispatchStrategyConfig>()
                            .eq(DispatchStrategyConfig::getStrategyCode, code()));
            if (config == null || !StringUtils.hasText(config.getWeights())) {
                return defaults;
            }
            JsonNode node = objectMapper.readTree(config.getWeights());
            return Map.of(
                    "skill", node.path("skill").asDouble(DEFAULT_SKILL),
                    "load", node.path("load").asDouble(DEFAULT_LOAD),
                    "resolveRate", node.path("resolveRate").asDouble(DEFAULT_RESOLVE_RATE),
                    "onlineHours", node.path("onlineHours").asDouble(DEFAULT_ONLINE_HOURS));
        } catch (Exception e) {
            log.warn("加权策略权重解析失败，使用默认权重: {}", e.getMessage());
            return defaults;
        }
    }
}
