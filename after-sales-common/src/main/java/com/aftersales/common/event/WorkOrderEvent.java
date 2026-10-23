package com.aftersales.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工单领域事件（MQ 消息体）
 * 设计思路：所有工单相关事件使用统一信封，eventId 保证幂等去重，
 * eventType 区分业务含义，payload 承载各事件自定义数据；
 * 生产/消费两侧用 Jackson 序列化为 JSON 字符串传输，避免序列化器配置差异
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderEvent implements Serializable {

    /** 事件唯一ID（UUID），消费端用于幂等判断 */
    private String eventId;

    /** 事件类型：CREATE / STRUCTURED / DISPATCH / STATUS_CHANGE / WARNING */
    private String eventType;

    /** 工单ID */
    private Long workOrderId;

    /** 工单编号 */
    private String orderNo;

    /** 事件携带的业务数据（JSON 结构，随事件类型变化） */
    private Object payload;

    /** 事件发生时间 */
    private LocalDateTime occurTime;
}
