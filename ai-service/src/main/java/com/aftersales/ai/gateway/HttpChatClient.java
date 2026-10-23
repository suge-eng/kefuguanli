package com.aftersales.ai.gateway;

import com.aftersales.ai.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 真实大模型客户端（OpenAI 兼容 HTTP 协议）
 * 设计思路：走 OpenAI 兼容协议而非绑定某一家 SDK，通义/DeepSeek 等国产模型
 * 都提供兼容端点，换模型只改 yml 的 base-url/model/api-key；
 * 只在 ai.chat.mock-enabled=false 时装配，与 Mock 实现互斥
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.chat.mock-enabled", havingValue = "false")
public class HttpChatClient implements ChatClient {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public HttpChatClient(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        // SimpleClientHttpRequestFactory 即可满足超时控制，不引入额外 HTTP 客户端依赖
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = aiProperties.getChat().getTimeoutMs();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        return invoke(systemPrompt, userPrompt, false);
    }

    @Override
    public String chatJson(String systemPrompt, String userPrompt) {
        return invoke(systemPrompt, userPrompt, true);
    }

    /**
     * 调用 /chat/completions
     *
     * @param forceJson true 时附加 function calling 约束，强制模型以 JSON 返回
     */
    private String invoke(String systemPrompt, String userPrompt, boolean forceJson) {
        AiProperties.Chat chat = aiProperties.getChat();
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", chat.getModel());
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userPrompt);
            if (forceJson) {
                // function calling：模型必须调用该函数，arguments 即 JSON 输出
                ArrayNode tools = body.putArray("tools");
                ObjectNode tool = tools.addObject();
                tool.put("type", "function");
                ObjectNode function = tool.putObject("function");
                function.put("name", "extract_fields");
                function.put("description", "提取字段并以JSON返回");
                ObjectNode parameters = function.putObject("parameters");
                parameters.put("type", "object");
                parameters.putObject("properties");
                ObjectNode toolChoice = body.putObject("tool_choice");
                toolChoice.put("type", "function");
                toolChoice.putObject("function").put("name", "extract_fields");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(chat.getApiKey());
            ResponseEntity<String> response = restTemplate.exchange(
                    chat.getBaseUrl() + "/chat/completions", HttpMethod.POST,
                    new HttpEntity<>(objectMapper.writeValueAsString(body), headers), String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("大模型响应异常: status=" + response.getStatusCode());
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode message = root.path("choices").path(0).path("message");
            // 优先取 function calling 的 arguments，没有则退回普通 content
            JsonNode arguments = message.path("tool_calls").path(0).path("function").path("arguments");
            String result = arguments.isTextual() ? arguments.asText() : message.path("content").asText(null);
            if (result == null || result.isBlank()) {
                throw new RuntimeException("大模型返回内容为空");
            }
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // HTTP 超时/连接失败/序列化失败统一转成运行时异常，交给上层重试
            throw new RuntimeException("大模型调用失败: " + e.getMessage(), e);
        }
    }
}
