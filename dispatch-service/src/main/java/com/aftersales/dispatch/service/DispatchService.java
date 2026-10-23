package com.aftersales.dispatch.service;

import com.aftersales.common.enums.WorkOrderStatusEnum;
import com.aftersales.common.exception.BizException;
import com.aftersales.common.result.Result;
import com.aftersales.dispatch.entity.CustomerService;
import com.aftersales.dispatch.feign.WorkOrderClient;
import com.aftersales.dispatch.feign.dto.DispatchRequestDto;
import com.aftersales.dispatch.feign.dto.WorkOrderDetailVOView;
import com.aftersales.dispatch.feign.dto.WorkOrderView;
import com.aftersales.dispatch.mapper.CustomerServiceMapper;
import com.aftersales.dispatch.mq.DispatchEventProducer;
import com.aftersales.dispatch.strategy.DispatchContext;
import com.aftersales.dispatch.strategy.DispatchStrategy;
import com.aftersales.dispatch.strategy.DispatchStrategyManager;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 派单主流程服务
 * 设计思路：
 * 1. 派单结果用 Feign 同步回写工单中心——回写失败立即感知、可重试，
 *    避免「派单成功但工单没更新」的不一致窗口
 * 2. 全流程幂等：重复消费时工单已不在待分派状态，直接跳过
 * 3. 客服负载增减不在本类做，由 LoadAdjustConsumer 消费工单状态事件统一调整，
 *    保证「工单实际归属」与「负载计数」由同一数据源（工单中心事件）驱动
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchService {

    private final WorkOrderClient workOrderClient;
    private final CustomerServiceMapper customerServiceMapper;
    private final LoadService loadService;
    private final DispatchStrategyManager strategyManager;
    private final DispatchEventProducer eventProducer;

    /** 超时检查延时消息等级（16=30分钟，见 application.yml） */
    @Value("${dispatch.timeout-check-delay-level:16}")
    private int timeoutCheckDelayLevel;

    /**
     * 为工单计算最优客服并完成分派
     *
     * @param workOrderId   工单ID
     * @param excludeCsIds  排除的客服ID集合（超时转派时排除原客服，防止派回同一人）
     */
    public void dispatchWorkOrder(Long workOrderId, Set<Long> excludeCsIds) {
        // 1. 查询工单详情（状态校验 + 技能匹配取文本）
        Result<WorkOrderDetailVOView> result = workOrderClient.detail(workOrderId);
        if (result == null || result.getData() == null || result.getData().getWorkOrder() == null) {
            // 查询失败抛异常让 MQ 重试（可能是工单中心暂时不可用）
            throw new BizException("查询工单详情失败, workOrderId=" + workOrderId);
        }
        WorkOrderView workOrder = result.getData().getWorkOrder();

        // 2. 幂等保护：只有「待分派/待处理」才派单，重复消费直接跳过
        String status = workOrder.getStatus();
        if (!WorkOrderStatusEnum.PENDING_DISPATCH.name().equals(status)
                && !WorkOrderStatusEnum.PENDING_HANDLE.name().equals(status)) {
            log.info("工单{}当前状态[{}]，无需派单，跳过（幂等）", workOrderId, status);
            return;
        }

        // 3. 圈定候选客服：在线 && 未达负载上限 && 不在排除集合
        List<CustomerService> onlineList = customerServiceMapper.selectList(
                new LambdaQueryWrapper<CustomerService>()
                        .eq(CustomerService::getOnlineStatus, "ONLINE"));
        Map<Long, Integer> loads = new HashMap<>();
        List<CustomerService> candidates = new ArrayList<>();
        for (CustomerService cs : onlineList) {
            if (excludeCsIds != null && excludeCsIds.contains(cs.getId())) {
                continue;
            }
            int load = loadService.get(cs.getId());
            loads.put(cs.getId(), load);
            if (load < cs.getMaxLoad()) {
                candidates.add(cs);
            }
        }
        if (candidates.isEmpty()) {
            // 无可派客服不抛异常（避免 MQ 无限重试）：工单保持待分派，
            // 等有客服上线/释放负载后可通过人工或后续策略补充触发
            log.warn("工单{}暂无可派客服（全部离线或满载），保持待分派状态", workOrderId);
            return;
        }

        // 4. 策略引擎选人
        DispatchStrategy strategy = strategyManager.currentStrategy();
        DispatchContext ctx = DispatchContext.builder()
                .workType(workOrder.getWorkType())
                .title(workOrder.getTitle())
                .content(result.getData().getDetail() == null ? null : result.getData().getDetail().getContent())
                .loads(loads)
                .build();
        Long selectedId = strategy.select(candidates, ctx);
        CustomerService selected = candidates.stream()
                .filter(cs -> cs.getId().equals(selectedId))
                .findFirst()
                .orElse(candidates.get(0));

        // 5. Feign 同步回写工单中心（首派/转派由工单中心按当前状态区分动作）
        Result<Void> dispatchResult = workOrderClient.dispatch(workOrderId,
                new DispatchRequestDto(selected.getId(), selected.getName()));
        if (dispatchResult == null || dispatchResult.getCode() == null || dispatchResult.getCode() != 200) {
            // 回写失败抛异常让 MQ 重试；工单中心侧有状态机校验，重复回写安全
            throw new BizException("派单回写工单中心失败: "
                    + (dispatchResult == null ? "无响应" : dispatchResult.getMessage()));
        }

        // 6. 发送延时检查消息：到期后若工单仍未被领取，自动转派
        eventProducer.sendTimeoutCheck(workOrderId, selected.getId(), timeoutCheckDelayLevel);

        // 7. 广播派单事件（工作台通知等下游消费）
        eventProducer.sendDispatchEvent(workOrderId, workOrder.getOrderNo(), selected, strategy.code());

        log.info("工单{}派单成功: 客服[{}({})], 策略={}", workOrderId, selected.getName(), selected.getId(), strategy.code());
    }
}
