package com.aftersales.ai.service;

import com.aftersales.ai.feign.WorkOrderClient;
import com.aftersales.ai.feign.dto.StructureRequestDto;
import com.aftersales.ai.feign.dto.WorkOrderDetailVOView;
import com.aftersales.ai.gateway.AiGateway;
import com.aftersales.common.enums.WorkOrderStatusEnum;
import com.aftersales.common.exception.BizException;
import com.aftersales.common.result.Result;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构化提取 Agent
 * 设计思路：消费「工单创建事件」，用大模型 Function Calling 把用户自由文本
 * 提取成固定 JSON 字段，Feign 同步回写工单中心；
 * 大模型故障时走关键词规则兜底提取——结构化是全链路（派单/预警）的前置环节，
 * 不能因为 AI 挂了让工单卡在待结构化状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StructureExtractService {

    private static final Set<String> WORK_TYPES = Set.of(
            "REFUND", "EXCHANGE", "LOGISTICS", "INVOICE", "COMPLAINT", "OTHER");
    private static final Set<String> APPEAL_LEVELS = Set.of("LOW", "NORMAL", "HIGH", "URGENT");

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(\\d{3,}(?:\\.\\d+)?)\\s*元");
    private static final Pattern ORDER_PATTERN = Pattern.compile("订单号[:：\\s]*([A-Za-z0-9]{5,})");
    private static final Pattern ORDER_ID_VALID = Pattern.compile("[A-Za-z0-9]{5,}");

    private final WorkOrderClient workOrderClient;
    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;

    /**
     * 提取并回写结构化字段（消费失败抛异常让 MQ 重试；状态不对幂等跳过）
     */
    public void extract(Long workOrderId) {
        WorkOrderDetailVOView vo = queryDetail(workOrderId);
        String status = vo.getWorkOrder().getStatus();
        // 幂等保护：重复消费/人工已处理时不覆盖
        if (!WorkOrderStatusEnum.PENDING_STRUCTURE.name().equals(status)) {
            log.info("工单非待结构化状态，跳过提取: workOrderId={}, status={}", workOrderId, status);
            return;
        }
        String content = vo.getDetail() == null ? "" : vo.getDetail().getContent();

        Map<String, Object> fields;
        try {
            String json = aiGateway.chatJson(buildSystemPrompt(), content);
            fields = objectMapper.readValue(json, new TypeReference<>() {});
            normalize(fields, content);
            applyRuleCorrection(fields, content);
        } catch (Exception e) {
            // 大模型解析失败走规则兜底，保证结构化环节永远有产出
            log.warn("AI提取失败，走规则兜底: workOrderId={}, err={}", workOrderId, e.getMessage());
            fields = ruleFallback(content);
            fields.put("fallback", true);
        }

        try {
            StructureRequestDto dto = new StructureRequestDto();
            dto.setWorkType((String) fields.get("workType"));
            dto.setAppealLevel((String) fields.get("appealLevel"));
            dto.setComplaintIntent((Boolean) fields.get("complaintIntent"));
            dto.setTitle((String) fields.get("title"));
            dto.setRelatedOrderId((String) fields.get("relatedOrderId"));
            dto.setAmount((BigDecimal) fields.get("amount"));
            dto.setRawJson(objectMapper.writeValueAsString(fields));

            Result<Void> result = workOrderClient.structure(workOrderId, dto);
            if (result == null || result.getCode() == null || result.getCode() != 200) {
                throw new BizException("结构化结果回写失败: " + (result == null ? "无响应" : result.getMessage()));
            }
            log.info("结构化提取回写成功: workOrderId={}, workType={}", workOrderId, dto.getWorkType());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("结构化结果回写失败: " + e.getMessage());
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

    /** few-shot 示例让模型输出更贴合字段定义（枚举值/空值表达） */
    private String buildSystemPrompt() {
        return """
                你是售后工单结构化提取助手，从用户诉求中提取字段并以JSON返回。字段定义：\
                workType(REFUND退货退款/EXCHANGE换货/LOGISTICS物流/INVOICE发票/COMPLAINT投诉/OTHER其他)、\
                appealLevel(LOW/NORMAL/HIGH/URGENT)、\
                relatedOrderId(平台订单号,无则null)、\
                amount(涉及金额数字,无则null)、\
                complaintIntent(是否有投诉/曝光/升级意向)、\
                title(一句话概括,18字内)。只输出JSON，不要多余文字。
                示例1输入：我要退货，订单号：A123456，商品用了两天就坏了，花了899元
                示例1输出：{"workType":"REFUND","appealLevel":"NORMAL","relatedOrderId":"A123456","amount":899,"complaintIntent":false,"title":"商品质量问题申请退货"}
                示例2输入：快递五天了还没送到，到底怎么回事？
                示例2输出：{"workType":"LOGISTICS","appealLevel":"NORMAL","relatedOrderId":null,"amount":null,"complaintIntent":false,"title":"物流长时间未送达催办"}
                示例3输入：再不处理我就打12315投诉，还要找媒体曝光你们！
                示例3输出：{"workType":"COMPLAINT","appealLevel":"HIGH","relatedOrderId":null,"amount":null,"complaintIntent":true,"title":"用户威胁投诉曝光要求处理"}""";
    }

    /** 字段校验规整：非法值一律落到安全默认，防止模型幻觉污染下游 */
    private void normalize(Map<String, Object> fields, String content) {
        Object workType = fields.get("workType");
        if (!(workType instanceof String) || !WORK_TYPES.contains((String) workType)) {
            fields.put("workType", "OTHER");
        }
        Object appealLevel = fields.get("appealLevel");
        if (!(appealLevel instanceof String) || !APPEAL_LEVELS.contains((String) appealLevel)) {
            fields.put("appealLevel", "NORMAL");
        }
        Object amount = fields.get("amount");
        BigDecimal amountValue = null;
        if (amount != null) {
            try {
                amountValue = new BigDecimal(String.valueOf(amount));
            } catch (Exception e) {
                // 金额解析不了按未提取处理
            }
        }
        fields.put("amount", amountValue);
        Object relatedOrderId = fields.get("relatedOrderId");
        if (!(relatedOrderId instanceof String) || !ORDER_ID_VALID.matcher((String) relatedOrderId).matches()) {
            fields.put("relatedOrderId", null);
        }
        Object complaintIntent = fields.get("complaintIntent");
        fields.put("complaintIntent", Boolean.TRUE.equals(complaintIntent));
        Object title = fields.get("title");
        if (!(title instanceof String) || ((String) title).isBlank()) {
            fields.put("title", content.length() > 18 ? content.substring(0, 18) : content);
        }
    }

    /**
     * 规则校正：模型负责语义概述，关键字段以关键词规则为锚
     * 设计思路：真实模型偶发返回非法枚举值或漏抽字段（被 normalize 归一成 OTHER/NORMAL），
     * 而「投诉/退款/发票」等强信号词在规则里是确定的；用规则做最终校正，
     * 保证下游派单技能匹配与预警升级的口径不因模型波动而失真。
     * 仅做「补空 + 强信号纠偏」，不覆盖模型给出的合理结果。
     */
    private void applyRuleCorrection(Map<String, Object> fields, String content) {
        Map<String, Object> rule = ruleFallback(content);
        // 1. 投诉强信号：命中则纠偏类型与意向（等级仅在模型未给 URGENT 时提升为 HIGH）
        if (Boolean.TRUE.equals(rule.get("complaintIntent"))) {
            fields.put("workType", "COMPLAINT");
            fields.put("complaintIntent", true);
            if (!"URGENT".equals(fields.get("appealLevel"))) {
                fields.put("appealLevel", "HIGH");
            }
        } else if ("OTHER".equals(fields.get("workType")) && !"OTHER".equals(rule.get("workType"))) {
            // 2. 模型归到 OTHER 但规则能识别出明确类型时，采纳规则类型
            fields.put("workType", rule.get("workType"));
        }
        // 3. 订单号/金额被模型漏抽时用规则补齐
        if (fields.get("relatedOrderId") == null) {
            fields.put("relatedOrderId", rule.get("relatedOrderId"));
        }
        if (fields.get("amount") == null) {
            fields.put("amount", rule.get("amount"));
        }
    }

    /**
     * 规则兜底提取：关键词识别与 MockChatClient 保持同一套逻辑，
     * 保证「Mock 演示」与「真实模型故障兜底」产出字段口径一致
     */
    private Map<String, Object> ruleFallback(String content) {
        Map<String, Object> fields = new HashMap<>();
        boolean complaintIntent = false;
        String appealLevel = "NORMAL";
        String workType;
        if (containsAny(content, "投诉", "12315", "曝光", "消协", "律师")) {
            workType = "COMPLAINT";
            complaintIntent = true;
            appealLevel = "HIGH";
        } else if (containsAny(content, "换货")) {
            workType = "EXCHANGE";
        } else if (containsAny(content, "发票")) {
            workType = "INVOICE";
        } else if (containsAny(content, "物流", "快递", "催件")) {
            workType = "LOGISTICS";
        } else if (containsAny(content, "退货", "退款")) {
            workType = "REFUND";
        } else {
            workType = "OTHER";
        }
        fields.put("workType", workType);
        fields.put("appealLevel", appealLevel);
        fields.put("complaintIntent", complaintIntent);

        Matcher amountMatcher = AMOUNT_PATTERN.matcher(content);
        fields.put("amount", amountMatcher.find() ? new BigDecimal(amountMatcher.group(1)) : null);
        Matcher orderMatcher = ORDER_PATTERN.matcher(content);
        fields.put("relatedOrderId", orderMatcher.find() ? orderMatcher.group(1) : null);
        fields.put("title", content.length() > 18 ? content.substring(0, 18) : content);
        return fields;
    }

    private boolean containsAny(String content, String... keywords) {
        for (String keyword : keywords) {
            if (content != null && content.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
