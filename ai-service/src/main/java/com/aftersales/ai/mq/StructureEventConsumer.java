package com.aftersales.ai.mq;

import com.aftersales.ai.service.StructureExtractService;
import com.aftersales.common.event.WorkOrderEvent;
import com.aftersales.common.mq.MqConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 「工单创建事件」消费者：触发结构化提取
 * 设计思路：创建 -> 结构化用 MQ 事件驱动而非同步调用，
 * AI 提取耗时不稳定（秒级~分钟级），异步解耦后创建接口无需等待
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_WORK_ORDER_CREATE,
        consumerGroup = "ai_structure_consumer_group"
)
public class StructureEventConsumer implements RocketMQListener<String> {

    private final StructureExtractService structureExtractService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        try {
            WorkOrderEvent event = objectMapper.readValue(message, WorkOrderEvent.class);
            log.info("收到工单创建事件，开始结构化提取: workOrderId={}", event.getWorkOrderId());
            structureExtractService.extract(event.getWorkOrderId());
        } catch (Exception e) {
            // 抛异常让 RocketMQ 重试；提取服务内部有状态幂等保护，重复消费无副作用
            log.error("工单创建事件消费失败: {}", message, e);
            throw new RuntimeException("工单创建事件消费失败", e);
        }
    }
}
