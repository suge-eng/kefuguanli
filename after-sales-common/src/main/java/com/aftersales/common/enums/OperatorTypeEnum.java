package com.aftersales.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 操作人类型枚举（工单操作日志用）
 */
@Getter
@AllArgsConstructor
public enum OperatorTypeEnum {

    USER("用户"),
    CS("客服"),
    SYSTEM("系统"),
    AI("AI智能体");

    private final String desc;
}
