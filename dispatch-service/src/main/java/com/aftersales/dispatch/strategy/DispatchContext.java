package com.aftersales.dispatch.strategy;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 派单上下文：策略计算所需的输入集合
 * 设计思路：策略入参收敛成一个上下文对象，
 * 后续策略需要新因子（如用户等级、工单金额）只加字段，不改策略接口签名
 */
@Data
@Builder
public class DispatchContext {

    /** 工单类型（结构化提取结果） */
    private String workType;

    /** 工单标题 */
    private String title;

    /** 用户诉求原文（技能匹配的数据源） */
    private String content;

    /** 客服ID -> 当前在办工单数（来自 Redis 实时负载） */
    private Map<Long, Integer> loads;
}
