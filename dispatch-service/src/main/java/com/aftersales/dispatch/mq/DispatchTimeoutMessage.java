package com.aftersales.dispatch.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 派单超时检查消息体（延时消息）
 * 设计思路：消息里固化「派单时的处理人」，到期消费时与工单当前处理人比对，
 * 只有仍是原处理人且未领取才转派——防止客服已领取后误触发
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DispatchTimeoutMessage implements Serializable {

    private Long workOrderId;

    /** 本次派单选中的客服ID */
    private Long handlerId;
}
