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

import java.util.Map;

/**
 * 状态变更事件消费者：累加解决量（含解决时长）与升级量
 * 设计思路：只认 action=RESOLVE / WARNING，其余动作（派单/领取/转交/关闭）不产出看板指标；
 * action 放在 payload 里，与工单中心广播的事件结构保持一致
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_WORK_ORDER_STATUS,
        consumerGroup = "stats_status_consumer_group"
)
public class StatusEventConsumer implements RocketMQListener<String> {

    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        try {
            WorkOrderEvent event = objectMapper.readValue(message, WorkOrderEvent.class);
            String action = null;
            if (event.getPayload() instanceof Map<?, ?> payload) {
                Object value = payload.get("action");
                action = value == null ? null : value.toString();
            }
            metricsService.onStatusChanged(event, action);
        } catch (Exception e) {
            log.error("状态事件统计消费失败: {}", message, e);
            throw new RuntimeException("状态事件统计消费失败", e);
        }
    }
}
