package com.aftersales.stats.service;

import com.aftersales.common.event.WorkOrderEvent;
import com.aftersales.stats.mapper.WorkOrderMetricsDailyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * 指标累加服务
 * 设计思路：
 * 1. 统计只依赖事件流，不反查业务库——服务解耦，工单库压力为零；
 * 2. MQ 至少一次投递，按 eventId 在 Redis 做 24h 幂等去重，避免重复计数；
 * 3. 全量维度（ALL）与类型维度分开累加：总量在创建时计 ALL，类型总量在结构化完成时计，
 *    这样每次事件只加自己知道的信息，不需要事件里带全所有字段
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    /** 事件幂等 key 前缀 */
    private static final String EVENT_IDEMPOTENT_KEY = "stats:event:";
    /** 工单级动作幂等 key 前缀（同一天同一工单的解决/升级只计一次） */
    private static final String ACTION_ONCE_KEY = "stats:action:";
    /** 工单创建时间缓存 key 前缀（解决时长统计用，48h 过期） */
    private static final String WO_CREATE_TIME_KEY = "stats:wo:create:";
    /** 全量维度标识 */
    public static final String TYPE_ALL = "ALL";
    /** 类型未知兜底 */
    private static final String TYPE_OTHER = "OTHER";

    private final WorkOrderMetricsDailyMapper metricsMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 工单创建：全量总量 +1，并缓存创建时间供解决时长计算
     */
    public void onCreated(WorkOrderEvent event) {
        if (!acquireIdempotent(event)) {
            return;
        }
        LocalDate statDate = statDate(event);
        metricsMapper.accumulate(statDate, TYPE_ALL, 1, 0, 0, 0);
        // 记录创建时刻：解决事件到达时用它算耗时，不依赖工单服务查询
        stringRedisTemplate.opsForValue().set(WO_CREATE_TIME_KEY + event.getWorkOrderId(),
                String.valueOf(occurEpoch(event)), Duration.ofHours(48));
        log.info("指标累加-创建: workOrderId={}, statDate={}", event.getWorkOrderId(), statDate);
    }

    /**
     * 结构化完成：按工单类型累加类型维度总量
     */
    public void onStructured(WorkOrderEvent event) {
        if (!acquireIdempotent(event)) {
            return;
        }
        String workType = readWorkType(event);
        metricsMapper.accumulate(statDate(event), workType, 1, 0, 0, 0);
        log.info("指标累加-结构化: workOrderId={}, workType={}", event.getWorkOrderId(), workType);
    }

    /**
     * 状态变更：RESOLVE 累加解决量与解决时长，WARNING 累加升级量
     * 其他动作（DISPATCH/CLAIM/TRANSFER/CLOSE等）不影响看板指标
     */
    public void onStatusChanged(WorkOrderEvent event, String action) {
        if (!"RESOLVE".equals(action) && !"WARNING".equals(action)) {
            return;
        }
        if (!acquireIdempotent(event)) {
            return;
        }
        LocalDate statDate = statDate(event);
        // 二次去重：预警 Agent 每 30 分钟重复评估同一工单会重复发 WARNING，
        // 若只按 eventId 去重，同一天同一工单的升级会被重复计数（升级数可能超过总数），
        // 因此再按「动作+工单+日期」做一次去重，保证解决/升级数量与工单一一对应
        if (!acquireActionOnce(event, action, statDate)) {
            return;
        }
        String workType = readWorkType(event);
        if ("RESOLVE".equals(action)) {
            int minutes = resolveMinutes(event);
            metricsMapper.accumulate(statDate, TYPE_ALL, 0, 1, 0, minutes);
            metricsMapper.accumulate(statDate, workType, 0, 1, 0, minutes);
        } else {
            metricsMapper.accumulate(statDate, TYPE_ALL, 0, 0, 1, 0);
            metricsMapper.accumulate(statDate, workType, 0, 0, 1, 0);
        }
        log.info("指标累加-{}: workOrderId={}, workType={}", action, event.getWorkOrderId(), workType);
    }

    /** 事件幂等：同一 eventId 只处理一次；Redis 故障时放行（宁可重复也不漏统计） */
    private boolean acquireIdempotent(WorkOrderEvent event) {
        String eventId = event.getEventId();
        if (eventId == null || eventId.isBlank()) {
            return true;
        }
        try {
            Boolean ok = stringRedisTemplate.opsForValue()
                    .setIfAbsent(EVENT_IDEMPOTENT_KEY + eventId, "1", Duration.ofHours(24));
            if (!Boolean.TRUE.equals(ok)) {
                log.info("统计事件已处理过，跳过: eventId={}", eventId);
                return false;
            }
        } catch (Exception e) {
            log.warn("统计幂等写入Redis失败，放行: eventId={}, err={}", eventId, e.getMessage());
        }
        return true;
    }

    /** 事件发生日期作为统计日期（避免跨零点事件记到错误日期） */
    private LocalDate statDate(WorkOrderEvent event) {
        return event.getOccurTime() == null ? LocalDate.now() : event.getOccurTime().toLocalDate();
    }

    /**
     * 工单级动作幂等：同一工单在同一天的同一动作只统计一次
     * Redis 故障时放行（宁可重复也不漏统计）
     */
    private boolean acquireActionOnce(WorkOrderEvent event, String action, LocalDate statDate) {
        if (event.getWorkOrderId() == null) {
            return true;
        }
        String key = ACTION_ONCE_KEY + action + ":" + event.getWorkOrderId() + ":" + statDate;
        try {
            Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofHours(48));
            if (!Boolean.TRUE.equals(ok)) {
                log.info("工单当日该动作已统计过，跳过: workOrderId={}, action={}, statDate={}",
                        event.getWorkOrderId(), action, statDate);
                return false;
            }
        } catch (Exception e) {
            log.warn("动作幂等写入Redis失败，放行: workOrderId={}, action={}, err={}",
                    event.getWorkOrderId(), action, e.getMessage());
        }
        return true;
    }

    private long occurEpoch(WorkOrderEvent event) {
        LocalDateTime occur = event.getOccurTime() == null ? LocalDateTime.now() : event.getOccurTime();
        return occur.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
    }

    /** 解决耗时（分钟）：解决时刻 - 创建缓存时刻；取不到记录按 0 处理 */
    private int resolveMinutes(WorkOrderEvent event) {
        try {
            String created = stringRedisTemplate.opsForValue().get(WO_CREATE_TIME_KEY + event.getWorkOrderId());
            if (created == null) {
                return 0;
            }
            long createdEpoch = Long.parseLong(created);
            long minutes = ChronoUnit.MINUTES.between(
                    LocalDateTime.ofEpochSecond(createdEpoch, 0, java.time.ZoneOffset.ofHours(8)),
                    event.getOccurTime() == null ? LocalDateTime.now() : event.getOccurTime());
            return (int) Math.max(0, minutes);
        } catch (Exception e) {
            log.warn("解决时长计算失败，按0处理: workOrderId={}, err={}", event.getWorkOrderId(), e.getMessage());
            return 0;
        }
    }

    /** 从事件载荷读取工单类型（4B 已补充该字段），缺失按 OTHER 归类 */
    private String readWorkType(WorkOrderEvent event) {
        if (!(event.getPayload() instanceof Map<?, ?> payload)) {
            return TYPE_OTHER;
        }
        Object workType = payload.get("workType");
        if (workType == null || workType.toString().isBlank()) {
            return TYPE_OTHER;
        }
        return workType.toString();
    }
}
