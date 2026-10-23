package com.aftersales.stats;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 统计分析服务启动类
 * 职责：消费工单事件异步累加日指标，对外提供看板查询接口
 */
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.aftersales.stats.mapper")
@SpringBootApplication(scanBasePackages = {"com.aftersales.stats", "com.aftersales.common"})
public class StatsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StatsServiceApplication.class, args);
    }
}
