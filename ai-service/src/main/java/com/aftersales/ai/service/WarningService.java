package com.aftersales.ai.service;

import com.aftersales.ai.config.AiProperties;
import com.aftersales.ai.entity.WarningRuleConfig;
import com.aftersales.ai.feign.WorkOrderClient;
import com.aftersales.ai.feign.dto.WorkOrderDetailVOView;
import com.aftersales.ai.feign.dto.WorkOrderView;
import com.aftersales.ai.mapper.WarningRuleConfigMapper;
import com.aftersales.ai.mq.WarningEventProducer;
import com.aftersales.common.enums.WorkOrderStatusEnum;
import com.aftersales.common.event.WorkOrderEvent;
import com.aftersales.common.exception.BizException;
import com.aftersales.common.result.PageResult;
import com.aftersales.common.result.Result;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 异常预警 Agent
 * 设计思路：
 * 1. 规则配置化存 warning_rule_config 表，按 ruleType 分发评估，加规则只插数据不改代码；
 * 2. 同一工单同一规则用 Redis setnx 24h 防重，避免 MQ 重试/多条事件导致预警轰炸；
 * 3. 预警是旁路能力——发事件/调升级接口失败只记 error，绝不阻塞主链路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarningService {

    /** 超时检查防重 key 前缀（每条创建事件只发一次延时检查） */
    private static final String TIMEOUT_CHECK_KEY = "ai:warning:tmo:";
    /** 预警命中防重 key 前缀（同一工单同一规则 24h 内只命中一次） */
    private static final String WARNING_HIT_KEY = "ai:warning:hit:";

    private final WarningRuleConfigMapper ruleConfigMapper;
    private final WorkOrderClient workOrderClient;
    private final WarningEventProducer warningEventProducer;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;

    /**
     * 创建事件触发：评估 KEYWORD / REPEAT 规则，并发起超时未处理延时检查
     */
    public void evaluateOnCreate(Long workOrderId) {
        List<WarningRuleConfig> rules = enabledRules();
        WorkOrderDetailVOView vo = queryDetail(workOrderId);
        WorkOrderView wo = vo.getWorkOrder();
        String content = (wo.getTitle() == null ? "" : wo.getTitle())
                + (vo.getDetail() == null || vo.getDetail().getContent() == null ? "" : vo.getDetail().getContent());

        for (WarningRuleConfig rule : rules) {
            try {
                switch (rule.getRuleType()) {
                    case "KEYWORD" -> evalKeyword(rule, wo, content);
                    case "REPEAT" -> evalRepeat(rule, wo);
                    // TIMEOUT 规则不在此 hit，只用来决定是否发起延时检查（见下方循环外逻辑）
                    default -> { }
                }
            } catch (Exception e) {
                // 单条规则评估失败不影响其他规则
                log.warn("预警规则评估失败，跳过: ruleCode={}, err={}", rule.getRuleCode(), e.getMessage());
            }
        }

        // 每条创建事件发一次超时检查延时消息（setnx 防重，MQ 重投不会重复发）
        boolean hasTimeoutRule = rules.stream().anyMatch(r -> "TIMEOUT".equals(r.getRuleType()));
        if (hasTimeoutRule && setNxIfAbsent(TIMEOUT_CHECK_KEY + workOrderId)) {
            warningEventProducer.sendTimeoutCheck(workOrderId, aiProperties.getWarning().getTimeoutCheckDelayLevel());
        }
    }

    /**
     * 结构化完成事件触发：评估依赖 AI 提取字段的规则（HIGH_VALUE / SENTIMENT）
     */
    public void evaluateOnStructured(Long workOrderId) {
        List<WarningRuleConfig> rules = enabledRules();
        WorkOrderDetailVOView vo = queryDetail(workOrderId);
        WorkOrderView wo = vo.getWorkOrder();

        Map<String, Object> fields;
        try {
            String structuredFields = vo.getDetail() == null ? null : vo.getDetail().getStructuredFields();
            if (structuredFields == null || structuredFields.isBlank()) {
                log.info("工单无结构化字段，跳过预警评估: workOrderId={}", workOrderId);
                return;
            }
            fields = objectMapper.readValue(structuredFields, Map.class);
        } catch (Exception e) {
            log.warn("结构化字段解析失败，跳过预警评估: workOrderId={}, err={}", workOrderId, e.getMessage());
            return;
        }

        for (WarningRuleConfig rule : rules) {
            try {
                switch (rule.getRuleType()) {
                    case "HIGH_VALUE" -> evalHighValue(rule, wo, fields);
                    case "SENTIMENT" -> evalSentiment(rule, wo, fields);
                    default -> { }
                }
            } catch (Exception e) {
                log.warn("预警规则评估失败，跳过: ruleCode={}, err={}", rule.getRuleCode(), e.getMessage());
            }
        }
    }

    /**
     * 超时检查延时消息到期触发：仍未解决/关闭则命中 TIMEOUT 规则
     */
    public void onTimeoutCheck(Long workOrderId) {
        WorkOrderDetailVOView vo = queryDetail(workOrderId);
        WorkOrderView wo = vo.getWorkOrder();
        String status = wo.getStatus();
        if (WorkOrderStatusEnum.RESOLVED.name().equals(status) || WorkOrderStatusEnum.CLOSED.name().equals(status)) {
            log.info("工单已终态，超时检查忽略: workOrderId={}, status={}", workOrderId, status);
            return;
        }
        WarningRuleConfig timeoutRule = enabledRules().stream()
                .filter(r -> "TIMEOUT".equals(r.getRuleType()))
                .findFirst()
                .orElse(null);
        if (timeoutRule == null) {
            log.info("无启用的超时预警规则，忽略: workOrderId={}", workOrderId);
            return;
        }
        long timeoutMinutes = parseCondition(timeoutRule).path("timeoutMinutes").asLong(30);
        hit(timeoutRule, wo, "超时" + timeoutMinutes + "分钟未处理完毕");
    }

    /** KEYWORD：标题+诉求命中任一关键词即触发 */
    private void evalKeyword(WarningRuleConfig rule, WorkOrderView wo, String content) {
        JsonNode keywords = parseCondition(rule).path("keywords");
        if (!keywords.isArray()) {
            return;
        }
        for (JsonNode keyword : keywords) {
            if (content.contains(keyword.asText())) {
                hit(rule, wo, "命中投诉关键词：" + keyword.asText());
                return;
            }
        }
    }

    /** REPEAT：同一用户 hours 小时内工单数 >= times 即触发（重复诉求/集中投诉信号） */
    private void evalRepeat(WarningRuleConfig rule, WorkOrderView wo) {
        JsonNode condition = parseCondition(rule);
        long hours = condition.path("hours").asLong(24);
        long times = condition.path("times").asLong(3);
        Result<PageResult<WorkOrderView>> pageResult = workOrderClient.page(
                1L, 100L, null, null, null, wo.getUserId());
        if (pageResult == null || pageResult.getCode() == null || pageResult.getCode() != 200
                || pageResult.getData() == null) {
            throw new BizException("查询用户工单失败: " + (pageResult == null ? "无响应" : pageResult.getMessage()));
        }
        LocalDateTime threshold = LocalDateTime.now().minusHours(hours);
        long count = pageResult.getData().getRecords().stream()
                .filter(w -> w.getCreateTime() != null && w.getCreateTime().isAfter(threshold))
                .count();
        if (count >= times) {
            hit(rule, wo, "用户" + hours + "小时内重复发起" + count + "次诉求");
        }
    }

    /** HIGH_VALUE：AI 提取金额 >= 阈值即触发 */
    private void evalHighValue(WarningRuleConfig rule, WorkOrderView wo, Map<String, Object> fields) {
        Object amount = fields.get("amount");
        if (amount == null) {
            return;
        }
        BigDecimal amountValue;
        try {
            amountValue = new BigDecimal(String.valueOf(amount));
        } catch (Exception e) {
            return;
        }
        BigDecimal threshold = new BigDecimal(parseCondition(rule).path("amountThreshold").asText("0"));
        if (amountValue.compareTo(threshold) >= 0) {
            hit(rule, wo, "涉及金额" + amountValue + "元，超过预警阈值" + threshold + "元");
        }
    }

    /** SENTIMENT：AI 识别到投诉/升级意向即触发 */
    private void evalSentiment(WarningRuleConfig rule, WorkOrderView wo, Map<String, Object> fields) {
        if (Boolean.TRUE.equals(fields.get("complaintIntent"))) {
            hit(rule, wo, "AI识别到投诉/升级意向");
        }
    }

    /**
     * 命中预警：防重 -> 发预警事件 -> ESCALATE 动作调工单中心升级接口
     */
    private void hit(WarningRuleConfig rule, WorkOrderView wo, String reason) {
        if (!setNxIfAbsent(WARNING_HIT_KEY + wo.getId() + ":" + rule.getRuleCode())) {
            log.info("预警已命中过，跳过: workOrderId={}, ruleCode={}", wo.getId(), rule.getRuleCode());
            return;
        }
        log.info("预警命中: workOrderId={}, ruleCode={}, reason={}", wo.getId(), rule.getRuleCode(), reason);
        WorkOrderEvent event = WorkOrderEvent.builder()
                .eventId(UUID.randomUUID().toString().replace("-", ""))
                .eventType("WARNING")
                .workOrderId(wo.getId())
                .orderNo(wo.getOrderNo())
                .payload(Map.of(
                        "ruleCode", rule.getRuleCode(),
                        "ruleName", rule.getRuleName(),
                        "ruleType", rule.getRuleType(),
                        "action", rule.getAction() == null ? "NOTIFY" : rule.getAction(),
                        "reason", reason))
                .occurTime(LocalDateTime.now())
                .build();
        // 发送失败只记 error 不抛——预警不能阻塞主链路（生产者内部已 catch，这里双保险）
        warningEventProducer.sendWarningEvent(event);

        if ("ESCALATE".equals(rule.getAction())) {
            try {
                Result<Void> result = workOrderClient.escalate(wo.getId(), "ai-warning-agent", reason);
                if (result == null || result.getCode() == null || result.getCode() != 200) {
                    log.error("工单升级失败: workOrderId={}, msg={}", wo.getId(),
                            result == null ? "无响应" : result.getMessage());
                }
            } catch (Exception e) {
                log.error("工单升级调用失败: workOrderId={}", wo.getId(), e);
            }
        }
    }

    private List<WarningRuleConfig> enabledRules() {
        return ruleConfigMapper.selectList(new LambdaQueryWrapper<WarningRuleConfig>()
                .eq(WarningRuleConfig::getEnabled, 1)
                .orderByAsc(WarningRuleConfig::getId));
    }

    private JsonNode parseCondition(WarningRuleConfig rule) {
        try {
            return objectMapper.readTree(rule.getConditionConfig() == null ? "{}" : rule.getConditionConfig());
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private WorkOrderDetailVOView queryDetail(Long workOrderId) {
        Result<WorkOrderDetailVOView> result;
        try {
            result = workOrderClient.detail(workOrderId);
        } catch (Exception e) {
            throw new BizException("查询工单详情失败: " + e.getMessage());
        }
        if (result == null || result.getCode() == null || result.getCode() != 200
                || result.getData() == null || result.getData().getWorkOrder() == null) {
            throw new BizException("查询工单详情失败: " + (result == null ? "无响应" : result.getMessage()));
        }
        return result.getData();
    }

    /** Redis setnx 防重（24h 过期）；Redis 故障记 warn 并放行（宁可重复预警不可漏预警） */
    private boolean setNxIfAbsent(String key) {
        try {
            Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofHours(24));
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            log.warn("预警防重写入Redis失败，放行: key={}, err={}", key, e.getMessage());
            return true;
        }
    }
}
