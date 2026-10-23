package com.aftersales.common.mq;

/**
 * RocketMQ 常量：Topic / Tag / 消费组
 * 设计思路：Topic 统一定义在公共模块，生产者和消费者引用同一常量，
 * 避免字符串硬编码写错导致消息对不上
 */
public class MqConstants {

    /** 工单创建事件（生产者：工单中心；消费者：结构化提取Agent） */
    public static final String TOPIC_WORK_ORDER_CREATE = "work_order_create_topic";

    /** 工单结构化完成事件（生产者：AI服务；消费者：派单服务） */
    public static final String TOPIC_WORK_ORDER_STRUCTURED = "work_order_structured_topic";

    /** 工单分派事件（生产者：派单服务；消费者：工作台/通知） */
    public static final String TOPIC_WORK_ORDER_DISPATCH = "work_order_dispatch_topic";

    /** 派单超时检查事件（延时消息；生产者/消费者均为派单服务，用于超时自动转派） */
    public static final String TOPIC_DISPATCH_TIMEOUT = "dispatch_timeout_topic";

    /** 工单状态变更事件（生产者：工单中心；消费者：统计分析服务） */
    public static final String TOPIC_WORK_ORDER_STATUS = "work_order_status_topic";

    /** 预警事件（生产者：AI预警Agent；消费者：通知/工单中心） */
    public static final String TOPIC_WORK_ORDER_WARNING = "work_order_warning_topic";

    /** 预警超时检查事件（延时消息；生产者/消费者均为AI预警Agent，用于超时未处理自动升级） */
    public static final String TOPIC_WARNING_TIMEOUT = "warning_timeout_topic";

    /** 延时消息等级：30分钟（RocketMQ 4.x 固定档位），用于超时自动转派 */
    public static final int DELAY_LEVEL_30_MINUTES = 16;

    private MqConstants() {
    }
}
