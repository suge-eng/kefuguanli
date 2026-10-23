package com.aftersales.ai.mq;

import com.aftersales.common.event.WorkOrderEvent;
import com.aftersales.common.mq.MqConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 预警事件生产者
 * 与工单中心/派单服务同一套约定：事件体 Jackson 序列化为 JSON 字符串；
 * 预警是旁路增强能力，发送失败只记 error 不抛异常，绝不阻塞主链路
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WarningEventProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    /** 广播「预警事件」（通知/工单中心等下游消费） */
    public void sendWarningEvent(WorkOrderEvent event) {
        try {
            rocketMQTemplate.syncSend(MqConstants.TOPIC_WORK_ORDER_WARNING,
                    objectMapper.writeValueAsString(event), 5000);
            log.info("预警事件发送成功: workOrderId={}, eventType={}", event.getWorkOrderId(), event.getEventType());
        } catch (Exception e) {
            log.error("预警事件发送失败: workOrderId={}", event.getWorkOrderId(), e);
        }
    }

    /**
     * 发送超时未处理检查延时消息
     * 设计思路：用 RocketMQ 延时消息替代定时任务扫描——到期才触发、只查这一条工单；
     * 消息体就是 workOrderId 字符串，消费端无需再解信封
     *
     * @param delayLevel RocketMQ 固定延时档位（16=30分钟）
     */
    public void sendTimeoutCheck(Long workOrderId, int delayLevel) {
        try {
            rocketMQTemplate.syncSend(MqConstants.TOPIC_WARNING_TIMEOUT,
                    MessageBuilder.withPayload(String.valueOf(workOrderId)).build(), 5000, delayLevel);
            log.info("超时检查延时消息发送成功: workOrderId={}, delayLevel={}", workOrderId, delayLevel);
        } catch (Exception e) {
            log.error("超时检查延时消息发送失败: workOrderId={}", workOrderId, e);
        }
    }
}
