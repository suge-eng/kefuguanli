package com.aftersales.workorder.statemachine;

import com.aftersales.common.enums.WorkOrderStatusEnum;
import com.aftersales.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 工单状态机
 * 设计思路：用「当前状态 -> 允许到达的状态集合」的有向图管理流转，
 * 所有状态变更必须经过这里校验，从代码层面杜绝非法跳转
 * （例如：已关闭的工单不能被重新分派，待结构化的工单不能直接变已解决）。
 * 相比散落在各处的 if-else，流转规则集中、一目了然、好维护。
 */
@Component
public class WorkOrderStateMachine {

    /**
     * 合法流转关系表
     * 主流程：待结构化 -> 待分派 -> 待处理 -> 处理中 -> 待用户确认 -> 已解决 -> 已关闭
     * 支路：处理中可升级（ESCALATED），升级后回到处理中；已解决可关闭
     */
    private static final Map<WorkOrderStatusEnum, Set<WorkOrderStatusEnum>> TRANSITIONS = Map.of(
            WorkOrderStatusEnum.PENDING_STRUCTURE, Set.of(WorkOrderStatusEnum.PENDING_DISPATCH, WorkOrderStatusEnum.CLOSED),
            WorkOrderStatusEnum.PENDING_DISPATCH, Set.of(WorkOrderStatusEnum.PENDING_HANDLE, WorkOrderStatusEnum.CLOSED),
            WorkOrderStatusEnum.PENDING_HANDLE, Set.of(WorkOrderStatusEnum.PROCESSING, WorkOrderStatusEnum.ESCALATED, WorkOrderStatusEnum.CLOSED),
            WorkOrderStatusEnum.PROCESSING, Set.of(WorkOrderStatusEnum.PENDING_CONFIRM, WorkOrderStatusEnum.RESOLVED, WorkOrderStatusEnum.ESCALATED),
            WorkOrderStatusEnum.PENDING_CONFIRM, Set.of(WorkOrderStatusEnum.RESOLVED, WorkOrderStatusEnum.PROCESSING, WorkOrderStatusEnum.CLOSED),
            WorkOrderStatusEnum.ESCALATED, Set.of(WorkOrderStatusEnum.PROCESSING, WorkOrderStatusEnum.RESOLVED),
            WorkOrderStatusEnum.RESOLVED, Set.of(WorkOrderStatusEnum.CLOSED),
            WorkOrderStatusEnum.CLOSED, Set.of()
    );

    /**
     * 校验状态流转是否合法，非法直接抛业务异常
     *
     * @param from 当前状态
     * @param to   目标状态
     */
    public void checkTransition(String from, String to) {
        WorkOrderStatusEnum fromEnum = parse(from);
        WorkOrderStatusEnum toEnum = parse(to);
        Set<WorkOrderStatusEnum> allowedTargets = TRANSITIONS.get(fromEnum);
        if (allowedTargets == null || !allowedTargets.contains(toEnum)) {
            throw new BizException(String.format("非法状态流转：%s(%s) 不能变更为 %s(%s)",
                    fromEnum.name(), fromEnum.getDesc(), toEnum.name(), toEnum.getDesc()));
        }
    }

    private WorkOrderStatusEnum parse(String status) {
        try {
            return WorkOrderStatusEnum.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BizException("未知的工单状态: " + status);
        }
    }
}
