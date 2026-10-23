package com.aftersales.workorder.dto;

import lombok.Data;

/**
 * AI 结构化提取结果回写请求（ai-service 结构化 Agent 调用）
 * 设计思路：字段与 AI 提取的 JSON Schema 一一对应；
 * rawJson 保留原始提取结果整体落 detail.structured_fields，
 * 后续预警/统计可以直接消费，字段演进不用改表结构
 */
@Data
public class StructureRequest {

    /** 工单类型：REFUND/EXCHANGE/LOGISTICS/INVOICE/COMPLAINT/OTHER */
    private String workType;

    /** 诉求等级：LOW/NORMAL/HIGH/URGENT */
    private String appealLevel;

    /** 是否包含投诉意向（AI 语义判断） */
    private Boolean complaintIntent;

    /** AI 生成的一句话标题 */
    private String title;

    /** 提取到的平台订单号（校验不通过时为 null） */
    private String relatedOrderId;

    /** 涉及的订单金额（提到才提取，用于高价值预警） */
    private java.math.BigDecimal amount;

    /** AI 提取结果的完整 JSON（含兜底标记），原样落库 */
    private String rawJson;
}
