package com.aftersales.ai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * AI 服务启动类
 * 设计思路：AI 引擎层只消费事件/回写结果，不直接改工单库，
 * 与工单中心的交互全部走 Feign + MQ，保证服务边界清晰
 */
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.aftersales.ai.mapper")
@SpringBootApplication(scanBasePackages = {"com.aftersales.ai", "com.aftersales.common"})
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
