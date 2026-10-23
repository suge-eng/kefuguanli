package com.aftersales.workorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单操作日志实体
 * 设计思路：每次状态变更/人工操作都留痕（谁、何时、从什么状态到什么状态、做了什么），
 * 满足审计要求，也是排查工单流转问题的第一手资料
 */
@Data
@TableName("work_order_log")
public class WorkOrderLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workOrderId;

    /** 操作人ID（用户/客服/系统/AI） */
    private String operatorId;

    /** 操作人类型：USER/CS/SYSTEM/AI */
    private String operatorType;

    /** 操作类型：CREATE/STRUCTURE/DISPATCH/CLAIM/REPLY/TRANSFER/CLOSE/ESCALATE */
    private String action;

    private String beforeStatus;

    private String afterStatus;

    /** 变更内容说明 */
    private String content;

    private LocalDateTime createTime;
}
