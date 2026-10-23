package com.aftersales.ai.feign.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * AI 结构化提取结果回写请求
 * 设计思路：字段与工单中心 StructureRequest 一一对应；
 * rawJson 保留提取结果整体落 detail.structured_fields，
 * 后续预警/统计可以直接消费，字段演进不用改表结构
 */
@Data
public class StructureRequestDto {

    /** 工单类型：REFUND/EXCHANGE/LOGISTICS/INVOICE/COMPLAINT/OTHER */
    private String workType;

    /** 诉求等级：LOW/NORMAL/HIGH/URGENT */
    private String appealLevel;

    /** 是否包含投诉意向 */
    private Boolean complaintIntent;

    /** AI 生成的一句话标题 */
    private String title;

    /** 提取到的平台订单号（校验不通过时为 null） */
    private String relatedOrderId;

    /** 涉及的订单金额（用于高价值预警） */
    private BigDecimal amount;

    /** 提取结果的完整 JSON（含兜底标记），原样落库 */
    private String rawJson;
}
