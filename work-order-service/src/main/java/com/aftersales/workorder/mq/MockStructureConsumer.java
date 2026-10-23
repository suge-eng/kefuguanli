package com.aftersales.workorder.mq;

import com.aftersales.common.event.WorkOrderEvent;
import com.aftersales.common.mq.MqConstants;
import com.aftersales.workorder.service.WorkOrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 模拟结构化消费者（第一阶段占位）
 * 设计思路：第一阶段 AI 服务未就绪，用本消费者验证
 * 「创建事件 -> 消费 -> 状态推进 -> 结构化完成事件」的 MQ 全链路；
 * 第三阶段 ai-service 的结构化提取 Agent 上线后，
 * 通过配置 workorder.mock-structure-enabled=false 关闭本消费者，
 * 由 AI 服务接管同一个 Topic 的消费，业务零改动
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "workorder.mock-structure-enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_WORK_ORDER_CREATE,
        consumerGroup = "mock_structure_consumer_group"
)
public class MockStructureConsumer implements RocketMQListener<String> {

    private final WorkOrderService workOrderService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        try {
            WorkOrderEvent event = objectMapper.readValue(message, WorkOrderEvent.class);
            log.info("模拟结构化消费者收到工单创建事件: workOrderId={}", event.getWorkOrderId());
            workOrderService.mockStructured(event.getWorkOrderId());
        } catch (Exception e) {
            // 抛异常让 RocketMQ 重试；服务内部已做幂等保护，重复消费无副作用
            log.error("模拟结构化消费失败: {}", message, e);
            throw new RuntimeException("模拟结构化消费失败", e);
        }
    }
}
