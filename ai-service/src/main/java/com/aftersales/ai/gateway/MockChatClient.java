package com.aftersales.ai.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mock 大模型客户端（默认装配）
 * 设计思路：确定性关键词规则模拟大模型输出——同样的输入永远得到同样的输出，
 * 演示/联调时可断言结果；无真实 Key 也能跑通「结构化提取 + 回复草稿」全链路
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.chat.mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockChatClient implements ChatClient {

    /** 金额提取：匹配「500元 / 1299.5元」，3位以上数字避免误命中编号类小数字 */
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(\\d{3,}(?:\\.\\d+)?)\\s*元");

    /** 订单号提取：匹配「订单号：ABC12345」 */
    private static final Pattern ORDER_PATTERN = Pattern.compile("订单号[:：\\s]*([A-Za-z0-9]{5,})");

    private final ObjectMapper objectMapper;

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        // 模板化礼貌回复，保证 Mock 模式下草稿也有可用文案
        return "您好，非常抱歉给您带来了不便。您反馈的问题我们已经受理，"
                + "售后团队将严格按照平台售后政策为您核实处理，处理进展会第一时间同步给您。"
                + "请保持联系方式畅通，如有其他疑问可随时补充说明，感谢您的理解与支持。"
                + "（本回复为 Mock 草稿）";
    }

    @Override
    public String chatJson(String systemPrompt, String userPrompt) {
        String content = userPrompt == null ? "" : userPrompt;
        ObjectNode json = objectMapper.createObjectNode();

        // 关键词识别工单类型与投诉意向（与 StructureExtractService 的规则兜底逻辑保持一致）
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
        json.put("workType", workType);
        json.put("appealLevel", appealLevel);
        json.put("complaintIntent", complaintIntent);

        // 金额/订单号正则提取，提不到给 null（与真实提取的字段约束一致）
        Matcher amountMatcher = AMOUNT_PATTERN.matcher(content);
        if (amountMatcher.find()) {
            json.put("amount", Double.parseDouble(amountMatcher.group(1)));
        } else {
            json.putNull("amount");
        }
        Matcher orderMatcher = ORDER_PATTERN.matcher(content);
        if (orderMatcher.find()) {
            json.put("relatedOrderId", orderMatcher.group(1));
        } else {
            json.putNull("relatedOrderId");
        }

        // 标题取内容前 18 字
        json.put("title", content.length() > 18 ? content.substring(0, 18) : content);
        // Mock 标记，回写落库后可识别哪些是 Mock 产出
        json.put("mock", true);
        return json.toString();
    }

    private boolean containsAny(String content, String... keywords) {
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
