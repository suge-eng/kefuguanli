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
 * 「工单创建事件」消费者（预警视角）：评估 KEYWORD/REPEAT 规则并发起超时检查
 * 设计思路：预警与结构化用独立消费组订阅同一 Topic，互不阻塞、互不影响重试
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_WORK_ORDER_CREATE,
        consumerGroup = "ai_warning_create_group"
)
public class WarningCreateConsumer implements RocketMQListener<String> {

    private final WarningService warningService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        try {
            WorkOrderEvent event = objectMapper.readValue(message, WorkOrderEvent.class);
            log.info("收到工单创建事件，评估预警规则: workOrderId={}", event.getWorkOrderId());
            warningService.evaluateOnCreate(event.getWorkOrderId());
        } catch (Exception e) {
            log.error("创建事件预警评估失败: {}", message, e);
            throw new RuntimeException("创建事件预警评估失败", e);
        }
    }
}
