package com.aftersales.dispatch.strategy;

import com.aftersales.dispatch.entity.CustomerService;

import java.util.List;

/**
 * 派单策略顶层接口（策略模式）
 * 设计思路：派单规则是高频迭代点（日常综合加权、大促优先负载均衡、活动专属技能），
 * 每种规则一个实现类，新增/修改规则不动主流程，符合开闭原则；
 * 实现类的 Bean 名即策略编码，与 dispatch_strategy_config.strategy_code 一一对应，
 * 通过配置即可热切换
 */
public interface DispatchStrategy {

    /** 策略编码：RANDOM / SKILL / LEAST_LOAD / WEIGHTED */
    String code();

    /**
     * 从候选客服中选出最优处理人
     *
     * @param candidates 已通过「在线 + 未满载」过滤的候选客服
     * @param ctx        派单上下文
     * @return 选中的客服ID
     */
    Long select(List<CustomerService> candidates, DispatchContext ctx);
}
