package com.aftersales.ai.feign.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 工单详情聚合视图（Feign 反序列化用）
 * 对应工单中心 WorkOrderDetailVO，logs 字段 AI 用不到，直接忽略
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkOrderDetailVOView {

    private WorkOrderView workOrder;

    private WorkOrderDetailView detail;
}
