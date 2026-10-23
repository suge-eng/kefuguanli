package com.aftersales.workbench.feign.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单操作日志视图（Feign 反序列化用）
 * 工作台详情页展示操作轨迹和回复记录（action=REPLY 的日志即回复内容）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkOrderLogView {

    private String operatorId;

    private String operatorType;

    private String action;

    private String beforeStatus;

    private String afterStatus;

    private String content;

    private LocalDateTime createTime;
}
