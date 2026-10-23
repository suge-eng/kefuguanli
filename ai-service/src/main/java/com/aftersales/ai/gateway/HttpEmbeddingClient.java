package com.aftersales.ai.gateway;

import com.aftersales.ai.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 真实向量模型客户端（OpenAI 兼容 /embeddings 协议）
 * 只在 ai.embedding.mock-enabled=false 时装配
 */
@Component
@ConditionalOnProperty(name = "ai.embedding.mock-enabled", havingValue = "false")
public class HttpEmbeddingClient implements EmbeddingClient {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public HttpEmbeddingClient(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = aiProperties.getEmbedding().getTimeoutMs();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public List<Float> embed(String text) {
        AiProperties.Embedding embedding = aiProperties.getEmbedding();
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", embedding.getModel());
            body.put("input", text);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(embedding.getApiKey());
            ResponseEntity<String> response = restTemplate.exchange(
                    embedding.getBaseUrl() + "/embeddings", HttpMethod.POST,
                    new HttpEntity<>(objectMapper.writeValueAsString(body), headers), String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("向量模型响应异常: status=" + response.getStatusCode());
            }
            JsonNode data = objectMapper.readTree(response.getBody()).path("data").path(0).path("embedding");
            if (!data.isArray() || data.isEmpty()) {
                throw new RuntimeException("向量模型返回内容为空");
            }
            List<Float> vector = new ArrayList<>(data.size());
            for (JsonNode node : data) {
                vector.add((float) node.asDouble());
            }
            return vector;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("向量模型调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public int dimension() {
        return aiProperties.getEmbedding().getDimension();
    }
}
