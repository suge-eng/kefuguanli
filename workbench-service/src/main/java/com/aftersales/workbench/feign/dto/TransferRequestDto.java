package com.aftersales.workbench.feign.dto;

import lombok.Data;

/**
 * 转交请求体（Feign 调用工单中心 transfer 用），对应工单中心 TransferRequest
 */
@Data
public class TransferRequestDto {

    /** 操作人（当前处理人）ID */
    private Long operatorId;

    private Long newHandlerId;

    private String newHandlerName;

    private String reason;
}
