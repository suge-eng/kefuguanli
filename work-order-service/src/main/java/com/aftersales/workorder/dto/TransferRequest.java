package com.aftersales.workorder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 工单转交请求
 * 设计思路：转交只更换处理人、不改变工单状态（区别于超时转派的 RE_DISPATCH），
 * 负载增减由派单服务消费状态事件时按 beforeHandlerId/handlerId 调整
 */
@Data
public class TransferRequest {

    /** 操作人客服ID（必须是当前处理人） */
    @NotNull(message = "操作人ID不能为空")
    private Long operatorId;

    @NotNull(message = "新处理人ID不能为空")
    private Long newHandlerId;

    @NotBlank(message = "新处理人姓名不能为空")
    private String newHandlerName;

    /** 转交原因（留痕用） */
    private String reason;
}
