package com.aftersales.ai.mq;

import com.aftersales.ai.service.WarningService;
import com.aftersales.common.mq.MqConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 「预警超时检查事件」消费者：延时消息到期后检查工单是否超时未处理
 * 消息体为 workOrderId 字符串（生产端约定，见 WarningEventProducer）
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_WARNING_TIMEOUT,
        consumerGroup = "ai_warning_timeout_group"
)
public class WarningTimeoutConsumer implements RocketMQListener<String> {

    private final WarningService warningService;

    @Override
    public void onMessage(String message) {
        try {
            Long workOrderId = Long.parseLong(message.trim());
            log.info("收到超时检查事件: workOrderId={}", workOrderId);
            warningService.onTimeoutCheck(workOrderId);
        } catch (Exception e) {
            log.error("超时检查事件消费失败: {}", message, e);
            throw new RuntimeException("超时检查事件消费失败", e);
        }
    }
}
