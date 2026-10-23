package com.aftersales.workbench.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 仅含处理人ID的请求体（Feign 调用工单中心 claim/resolve 用）
 * 对应工单中心 ClaimRequest
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HandlerRequestDto {

    private Long handlerId;
}
