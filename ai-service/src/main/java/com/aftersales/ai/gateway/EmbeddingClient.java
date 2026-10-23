package com.aftersales.ai.gateway;

import java.util.List;

/**
 * 向量模型客户端
 * 设计思路：向量化同样做真实/Mock 双实现；Mock 向量确定性可复现，
 * 同一文本向量恒定，语义召回在 Mock 模式下也能验证链路（仅不保证语义相关性）
 */
public interface EmbeddingClient {

    /**
     * 文本向量化
     *
     * @param text 输入文本
     * @return 向量（长度 = dimension()）
     */
    List<Float> embed(String text);

    /**
     * 向量维度（必须与 Milvus 集合维度一致）
     */
    int dimension();
}
