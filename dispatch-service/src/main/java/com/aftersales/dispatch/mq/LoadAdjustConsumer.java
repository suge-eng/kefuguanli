package com.aftersales.dispatch.mq;

import com.aftersales.common.event.WorkOrderEvent;
import com.aftersales.common.mq.MqConstants;
import com.aftersales.dispatch.service.LoadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 客服负载调整消费者
 * 设计思路：负载增减由工单中心的状态事件统一驱动，派单主流程不直接改负载，
 * 保证负载计数与「工单实际归属」源自同一数据（工单中心事件），避免双写不一致；
 * 动作规则：
 * - DISPATCH：新处理人 +1
 * - RE_DISPATCH / TRANSFER：新处理人 +1，原处理人 -1
 * - RESOLVE / CLOSE：处理人 -1
 * - CLAIM 等其他动作不动负载
 *
 * 注意：MQ 至少一次投递，极端情况重复消费会导致负载抖动；
 * 生产应基于 eventId 做消费幂等（Redis SETNX 去重），演示项目从简
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_WORK_ORDER_STATUS,
        consumerGroup = "dispatch_load_consumer_group"
)
public class LoadAdjustConsumer implements RocketMQListener<String> {

    private final LoadService loadService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        try {
            WorkOrderEvent event = objectMapper.readValue(message, WorkOrderEvent.class);
            if (!(event.getPayload() instanceof Map)) {
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) event.getPayload();
            String action = (String) payload.get("action");
            Long handlerId = toLong(payload.get("handlerId"));
            Long beforeHandlerId = toLong(payload.get("beforeHandlerId"));

            if ("DISPATCH".equals(action)) {
                incrIfPresent(handlerId);
            } else if ("RE_DISPATCH".equals(action) || "TRANSFER".equals(action)) {
                incrIfPresent(handlerId);
                decrIfPresent(beforeHandlerId);
            } else if ("RESOLVE".equals(action) || "CLOSE".equals(action)) {
                decrIfPresent(handlerId);
            }
            log.debug("负载调整完成: action={}, handlerId={}, beforeHandlerId={}", action, handlerId, beforeHandlerId);
        } catch (Exception e) {
            log.error("负载调整消息消费失败: {}", message, e);
            throw new RuntimeException("负载调整消息消费失败", e);
        }
    }

    private void incrIfPresent(Long csId) {
        if (csId != null) {
            loadService.incr(csId);
        }
    }

    private void decrIfPresent(Long csId) {
        if (csId != null) {
            loadService.decr(csId);
        }
    }

    /** Jackson 反序列化数字默认是 Integer，统一转 Long */
    private Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }
}
