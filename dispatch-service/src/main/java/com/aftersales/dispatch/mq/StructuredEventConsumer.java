package com.aftersales.dispatch.mq;

import com.aftersales.common.event.WorkOrderEvent;
import com.aftersales.common.mq.MqConstants;
import com.aftersales.dispatch.service.DispatchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 「结构化完成事件」消费者：触发自动派单
 * 设计思路：结构化完成 -> 派单是 MQ 事件驱动而非同步调用，
 * AI 结构化耗时不稳定（秒级~分钟级），异步解耦后工单中心无需等待
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_WORK_ORDER_STRUCTURED,
        consumerGroup = "dispatch_structured_consumer_group"
)
public class StructuredEventConsumer implements RocketMQListener<String> {

    private final DispatchService dispatchService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        try {
            WorkOrderEvent event = objectMapper.readValue(message, WorkOrderEvent.class);
            log.info("收到结构化完成事件，开始派单: workOrderId={}", event.getWorkOrderId());
            // 首派无排除集合
            dispatchService.dispatchWorkOrder(event.getWorkOrderId(), Collections.emptySet());
        } catch (Exception e) {
            // 抛异常让 RocketMQ 重试；派单服务内部有状态幂等保护，重复消费无副作用
            log.error("结构化事件消费失败: {}", message, e);
            throw new RuntimeException("结构化事件消费失败", e);
        }
    }
}
