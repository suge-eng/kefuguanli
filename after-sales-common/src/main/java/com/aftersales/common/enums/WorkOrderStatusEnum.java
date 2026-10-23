package com.aftersales.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工单状态枚举
 * 设计思路：状态值全局统一，流转合法性由工单中心的状态机校验；
 * 数据库中直接存枚举名（可读性强，面试演示/排查问题时直观）
 */
@Getter
@AllArgsConstructor
public enum WorkOrderStatusEnum {

    PENDING_STRUCTURE("待结构化"),
    PENDING_DISPATCH("待分派"),
    PENDING_HANDLE("待处理"),
    PROCESSING("处理中"),
    PENDING_CONFIRM("待用户确认"),
    RESOLVED("已解决"),
    CLOSED("已关闭"),
    ESCALATED("已升级");

    private final String desc;

    public static boolean contains(String value) {
        for (WorkOrderStatusEnum statusEnum : values()) {
            if (statusEnum.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
