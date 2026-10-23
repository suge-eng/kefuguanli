package com.aftersales.workorder.mq;

import com.aftersales.common.event.WorkOrderEvent;
import com.aftersales.common.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

/**
 * 工单事件生产者
 * 设计思路：事件体统一用 Jackson 序列化成 JSON 字符串发送，
 * 跨服务序列化规则一致（rocketmq-spring 默认转换器对复杂对象支持不稳定）；
 * 发送失败抛异常让调用方感知，配合事务后置发送保证不丢事件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkOrderEventProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 同步发送工单事件
     *
     * @param topic 目标 Topic（见 MqConstants）
     * @param event 事件体
     */
    public void sendEvent(String topic, WorkOrderEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            rocketMQTemplate.convertAndSend(topic, message);
            log.info("工单事件发送成功, topic={}, eventType={}, workOrderId={}",
                    topic, event.getEventType(), event.getWorkOrderId());
        } catch (Exception e) {
            log.error("工单事件发送失败, topic={}, event={}", topic, event, e);
            throw new BizException("事件消息发送失败: " + e.getMessage());
        }
    }
}
