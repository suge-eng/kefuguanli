package com.aftersales.ai.mq;

import com.aftersales.ai.service.WarningService;
import com.aftersales.common.event.WorkOrderEvent;
import com.aftersales.common.mq.MqConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 「结构化完成事件」消费者（预警视角）：评估依赖结构化字段的规则（高金额/投诉情绪）
 * 设计思路：HIGH_VALUE/SENTIMENT 依赖 AI 提取的 amount/complaintIntent，
 * 必须在结构化完成后评估，因此订阅结构化事件而非创建事件
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_WORK_ORDER_STRUCTURED,
        consumerGroup = "ai_warning_structured_group"
)
public class WarningStructuredConsumer implements RocketMQListener<String> {

    private final WarningService warningService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        try {
            WorkOrderEvent event = objectMapper.readValue(message, WorkOrderEvent.class);
            log.info("收到结构化完成事件，评估预警规则: workOrderId={}", event.getWorkOrderId());
            warningService.evaluateOnStructured(event.getWorkOrderId());
        } catch (Exception e) {
            log.error("结构化事件预警评估失败: {}", message, e);
            throw new RuntimeException("结构化事件预警评估失败", e);
        }
    }
}
