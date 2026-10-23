package com.aftersales.dispatch.feign.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单主表视图（Feign 反序列化用）
 * 设计思路：只声明派单关心的字段，工单中心返回的多余字段由 Jackson 忽略，
 * 两侧字段解耦，工单中心加字段不影响派单服务
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkOrderView {

    private Long id;

    private String orderNo;

    private Long userId;

    private String userName;

    private String workType;

    private String priority;

    private String status;

    private Long handlerId;

    private String handlerName;

    private String title;

    private LocalDateTime createTime;
}
