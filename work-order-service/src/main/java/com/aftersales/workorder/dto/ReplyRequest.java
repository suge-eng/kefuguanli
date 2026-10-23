package com.aftersales.workorder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 客服回复请求
 * 设计思路：设计文档未单设回复表，回复内容记入工单操作日志（action=REPLY），
 * 天然满足留痕审计，详情接口随日志列表一起返回
 */
@Data
public class ReplyRequest {

    @NotNull(message = "客服ID不能为空")
    private Long handlerId;

    /** 回复内容 */
    @NotBlank(message = "回复内容不能为空")
    private String content;
}
