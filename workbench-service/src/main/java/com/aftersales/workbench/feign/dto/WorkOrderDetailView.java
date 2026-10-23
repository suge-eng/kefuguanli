package com.aftersales.workbench.feign.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 工单详情视图（Feign 反序列化用）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkOrderDetailView {

    /** 用户诉求原文 */
    private String content;

    /** 关联订单号 */
    private String relatedOrderId;

    /** 附件对象名列表（JSON 数组字符串） */
    private String attachments;

    /** AI 结构化提取结果（JSON 字符串） */
    private String structuredFields;
}
