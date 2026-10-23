package com.aftersales.ai.feign.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 工单详情视图（Feign 反序列化用）
 * AI 提取/生成草稿需要诉求原文，structuredFields 供预警 Agent 做二次判断
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkOrderDetailView {

    /** 用户诉求原文 */
    private String content;

    /** AI 结构化提取结果（JSON 字符串） */
    private String structuredFields;

    /** 附件对象名列表（JSON 数组字符串） */
    private String attachments;

    /** 关联订单号 */
    private String relatedOrderId;

    /** AI 回复草稿 */
    private String aiDraftReply;
}
