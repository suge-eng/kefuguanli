package com.aftersales.workorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单主表实体
 * 设计思路：主表只放流转核心字段，非结构化内容拆到 detail 表，
 * 避免列表查询时扫描大字段拖慢分页
 */
@Data
@TableName("work_order")
public class WorkOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单编号（业务唯一标识） */
    private String orderNo;

    private Long userId;

    private String userName;

    /** 来源渠道：WEB/API/PLATFORM */
    private String sourceChannel;

    /** 工单类型（AI结构化后回填） */
    private String workType;

    /** 优先级：LOW/NORMAL/HIGH/URGENT */
    private String priority;

    /** 状态：取值见 WorkOrderStatusEnum */
    private String status;

    private Long handlerId;

    private String handlerName;

    private String title;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
