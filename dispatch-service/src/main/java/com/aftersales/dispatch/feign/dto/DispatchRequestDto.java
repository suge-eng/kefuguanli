package com.aftersales.dispatch.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 派单回写请求（Feign 调用工单中心 /work-order/{id}/dispatch）
 * 字段与工单中心 DispatchRequest 保持一致
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DispatchRequestDto {

    /** 目标客服ID */
    private Long handlerId;

    /** 目标客服姓名 */
    private String handlerName;
}
