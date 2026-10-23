package com.aftersales.dispatch.strategy;

import com.aftersales.dispatch.entity.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 技能匹配派单：客服技能标签在工单文本（类型+标题+诉求原文）中出现次数最多者胜出
 * 设计思路：售后问题专业化程度高（退货/物流/发票），
 * 分给懂对应业务的客服能显著提升一次解决率；
 * 命中数相同走最小负载兜底，全员零命中（新业务未配置标签）也走最小负载
 */
@Component("SKILL")
@RequiredArgsConstructor
public class SkillDispatchStrategy implements DispatchStrategy {

    private final LeastLoadDispatchStrategy leastLoadStrategy;

    @Override
    public String code() {
        return "SKILL";
    }

    @Override
    public Long select(List<CustomerService> candidates, DispatchContext ctx) {
        String text = buildMatchText(ctx);

        CustomerService best = null;
        int bestHits = 0;
        for (CustomerService cs : candidates) {
            int hits = countHits(cs, text);
            // 命中数更高，或命中数相同但负载更低者胜出
            if (best == null
                    || hits > bestHits
                    || (hits == bestHits && load(ctx, cs) < load(ctx, best))) {
                best = cs;
                bestHits = hits;
            }
        }

        // 全员零命中：说明工单内容没匹配到任何技能标签，退化为最小负载
        if (bestHits == 0) {
            return leastLoadStrategy.select(candidates, ctx);
        }
        return best.getId();
    }

    /** 拼接匹配文本：工单类型 + 标题 + 诉求原文 */
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

    /** 统计客服技能标签在工单文本中的命中次数（出现多个标签累计） */
    private int countHits(CustomerService cs, String text) {
        if (!StringUtils.hasText(cs.getSkillTags()) || !StringUtils.hasText(text)) {
            return 0;
        }
        return (int) Arrays.stream(cs.getSkillTags().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .filter(text::contains)
                .count();
    }

    private int load(DispatchContext ctx, CustomerService cs) {
        return ctx.getLoads().getOrDefault(cs.getId(), 0);
    }
}
