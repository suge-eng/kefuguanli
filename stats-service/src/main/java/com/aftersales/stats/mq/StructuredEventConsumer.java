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
 * 结构化完成事件消费者：累加工单类型维度总量
 * 设计思路：创建事件里没有工单类型（AI 尚未提取），类型维度只能在结构化完成时补记，
 * 这样类型分布与全量总量最终一致，又不需要事件回查业务库
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_WORK_ORDER_STRUCTURED,
        consumerGroup = "stats_structured_consumer_group"
)
public class StructuredEventConsumer implements RocketMQListener<String> {

    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        try {
            metricsService.onStructured(objectMapper.readValue(message, WorkOrderEvent.class));
        } catch (Exception e) {
            log.error("结构化事件统计消费失败: {}", message, e);
            throw new RuntimeException("结构化事件统计消费失败", e);
        }
    }
}
