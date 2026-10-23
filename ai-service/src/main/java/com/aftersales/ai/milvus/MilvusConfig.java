package com.aftersales.ai.milvus;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 客户端配置
 * 设计思路：客户端做成单例 Bean 全局复用；连接失败不在此处抛异常，
 * 由使用方（初始化器/知识服务）try-catch 兜底，保证 Milvus 挂了 ai-service 也能启动
 */
@Configuration
public class MilvusConfig {

    @Bean
    public MilvusServiceClient milvusServiceClient(@Value("${milvus.host:localhost}") String host,
                                                   @Value("${milvus.port:19530}") int port) {
        return new MilvusServiceClient(ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .build());
    }
}
