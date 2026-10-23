package com.aftersales.dispatch.mq;

import com.aftersales.common.enums.WorkOrderStatusEnum;
import com.aftersales.common.mq.MqConstants;
import com.aftersales.common.result.Result;
import com.aftersales.dispatch.feign.WorkOrderClient;
import com.aftersales.dispatch.feign.dto.WorkOrderDetailVOView;
import com.aftersales.dispatch.feign.dto.WorkOrderView;
import com.aftersales.dispatch.service.DispatchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 派单超时检查消费者（延时消息到期触发）
 * 设计思路：到期后回查工单，只有「仍挂在原客服名下且未领取（待处理）」才转派，
 * 并把原客服加入排除集合防止派回同一人；
 * 客服已领取/工单已流转则消息自然作废
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_DISPATCH_TIMEOUT,
        consumerGroup = "dispatch_timeout_consumer_group"
)
public class TimeoutCheckConsumer implements RocketMQListener<String> {

    private final WorkOrderClient workOrderClient;
    private final DispatchService dispatchService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        try {
            DispatchTimeoutMessage timeoutMsg = objectMapper.readValue(message, DispatchTimeoutMessage.class);
            Long workOrderId = timeoutMsg.getWorkOrderId();

            Result<WorkOrderDetailVOView> result = workOrderClient.detail(workOrderId);
            if (result == null || result.getData() == null || result.getData().getWorkOrder() == null) {
                throw new RuntimeException("超时检查查询工单详情失败, workOrderId=" + workOrderId);
            }
            WorkOrderView workOrder = result.getData().getWorkOrder();

            // 只有「仍是原处理人 + 仍在待处理」才说明超时未领取，需要转派
            boolean stillPending = WorkOrderStatusEnum.PENDING_HANDLE.name().equals(workOrder.getStatus())
                    && timeoutMsg.getHandlerId().equals(workOrder.getHandlerId());
            if (!stillPending) {
                log.info("工单{}已领取或已流转（status={}, handlerId={}），超时检查作废",
                        workOrderId, workOrder.getStatus(), workOrder.getHandlerId());
                return;
            }

            log.info("工单{}超时未领取，触发自动转派（排除客服{}）", workOrderId, timeoutMsg.getHandlerId());
            dispatchService.dispatchWorkOrder(workOrderId, Set.of(timeoutMsg.getHandlerId()));
        } catch (Exception e) {
            log.error("超时检查消息消费失败: {}", message, e);
            throw new RuntimeException("超时检查消息消费失败", e);
        }
    }
}
