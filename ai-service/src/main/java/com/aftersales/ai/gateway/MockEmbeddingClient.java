package com.aftersales.ai.gateway;

import com.aftersales.ai.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Mock 向量模型客户端（默认装配）
 * 设计思路：以文本 hashCode 为随机种子生成向量——同一文本向量恒定（可复现），
 * 保证 Mock 模式下「向量化 -> 入库 -> 召回」链路可重复验证，仅不保证语义相关性
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.embedding.mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockEmbeddingClient implements EmbeddingClient {

    private final AiProperties aiProperties;

    @Override
    public List<Float> embed(String text) {
        // 确定性种子：相同文本生成相同向量
        Random random = new Random((text == null ? "" : text).hashCode());
        int dimension = dimension();
        List<Float> vector = new ArrayList<>(dimension);
        for (int i = 0; i < dimension; i++) {
            // -1 ~ 1 的随机浮点
            vector.add(random.nextFloat() * 2 - 1);
        }
        return vector;
    }

    @Override
    public int dimension() {
        return aiProperties.getEmbedding().getDimension();
    }
}
