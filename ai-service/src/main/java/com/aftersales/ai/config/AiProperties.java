package com.aftersales.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 能力配置属性
 * 设计思路：大模型接入参数全部外置到 yml，真实/Mock 双实现靠 mock-enabled 开关切换——
 * 没有真实 Key 时演示环境也能跑通全链路，拿到 Key 后只改配置不改代码
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** 对话大模型配置 */
    private Chat chat = new Chat();

    /** 向量模型配置 */
    private Embedding embedding = new Embedding();

    /** 预警 Agent 配置 */
    private Warning warning = new Warning();

    @Data
    public static class Chat {

        /** true=使用内置 Mock 实现（无需 Key）；false=走 OpenAI 兼容 HTTP 接口 */
        private Boolean mockEnabled = true;

        /** OpenAI 兼容端点 base-url */
        private String baseUrl;

        /** API Key（可用环境变量 AI_API_KEY 注入） */
        private String apiKey;

        /** 模型名 */
        private String model;

        /** 连接/读取超时（毫秒） */
        private Integer timeoutMs = 30000;

        /** 调用失败重试次数（不含首次） */
        private Integer retryTimes = 2;
    }

    @Data
    public static class Embedding {

        /** true=确定性 Mock 向量；false=走 OpenAI 兼容 /embeddings 接口 */
        private Boolean mockEnabled = true;

        private String baseUrl;

        private String apiKey;

        private String model;

        /** 向量维度，必须与 Milvus 集合维度一致 */
        private Integer dimension = 1024;

        private Integer timeoutMs = 30000;
    }

    @Data
    public static class Warning {

        /** 超时未处理检查的 RocketMQ 延时档位（16=30分钟） */
        private Integer timeoutCheckDelayLevel = 16;
    }
}
