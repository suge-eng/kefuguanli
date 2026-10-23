package com.aftersales.dispatch.mq;

import com.aftersales.common.event.WorkOrderEvent;
import com.aftersales.common.exception.BizException;
import com.aftersales.common.mq.MqConstants;
import com.aftersales.dispatch.entity.CustomerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 派单事件生产者
 * 与工单中心 WorkOrderEventProducer 同一套约定：事件体 Jackson 序列化为 JSON 字符串
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchEventProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    /** 广播「工单分派事件」（工作台通知等下游消费） */
    public void sendDispatchEvent(Long workOrderId, String orderNo, CustomerService handler, String strategyCode) {
        WorkOrderEvent event = WorkOrderEvent.builder()
                .eventId(UUID.randomUUID().toString().replace("-", ""))
                .eventType("DISPATCH")
                .workOrderId(workOrderId)
                .orderNo(orderNo)
                .payload(Map.of(
                        "handlerId", handler.getId(),
                        "handlerName", handler.getName(),
                        "strategy", strategyCode))
                .occurTime(LocalDateTime.now())
                .build();
        try {
            rocketMQTemplate.convertAndSend(MqConstants.TOPIC_WORK_ORDER_DISPATCH,
                    objectMapper.writeValueAsString(event));
            log.info("派单事件发送成功: workOrderId={}, handlerId={}", workOrderId, handler.getId());
        } catch (Exception e) {
            // 派单结果已回写成功，事件发送失败只影响通知类下游，不阻断主流程
            log.error("派单事件发送失败: workOrderId={}", workOrderId, e);
        }
    }

    /**
     * 发送派单超时检查延时消息
     * 设计思路：用 RocketMQ 延时消息替代定时任务扫描——
     * 到期才触发、只查这一条工单，比全表轮询「待处理且超时」的工单省资源得多
     *
     * @param delayLevel RocketMQ 固定延时档位（16=30分钟，5=1分钟）
     */
    public void sendTimeoutCheck(Long workOrderId, Long handlerId, int delayLevel) {
        try {
            String message = objectMapper.writeValueAsString(new DispatchTimeoutMessage(workOrderId, handlerId));
            rocketMQTemplate.syncSend(MqConstants.TOPIC_DISPATCH_TIMEOUT,
                    MessageBuilder.withPayload(message).build(), 5000, delayLevel);
            log.info("超时检查延时消息发送成功: workOrderId={}, handlerId={}, delayLevel={}",
                    workOrderId, handlerId, delayLevel);
        } catch (Exception e) {
            // 超时检查失败只意味着失去自动转派能力，工单本身已派成功，不阻断主流程
            log.error("超时检查延时消息发送失败: workOrderId={}", workOrderId, e);
        }
    }
}
