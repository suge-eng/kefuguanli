package com.aftersales.workbench.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回复请求体（Feign 调用工单中心 reply 用），对应工单中心 ReplyRequest
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReplyRequestDto {

    private Long handlerId;

    private String content;
}
