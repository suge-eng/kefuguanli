package com.aftersales.stats.mq;

import com.aftersales.common.event.WorkOrderEvent;
import com.aftersales.common.mq.MqConstants;
import com.aftersales.stats.service.MetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 工单创建事件消费者：累加全量总量
 * 设计思路：独立消费组订阅同一 Topic，与 AI 结构化组的消费互不影响（RocketMQ 按组广播）
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_WORK_ORDER_CREATE,
        consumerGroup = "stats_create_consumer_group"
)
public class CreateEventConsumer implements RocketMQListener<String> {

    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        try {
            metricsService.onCreated(objectMapper.readValue(message, WorkOrderEvent.class));
        } catch (Exception e) {
            log.error("创建事件统计消费失败: {}", message, e);
            throw new RuntimeException("创建事件统计消费失败", e);
        }
    }
}
