package com.aftersales.dispatch;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 智能派单服务启动类
 */
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.aftersales.dispatch.mapper")
@SpringBootApplication(scanBasePackages = {"com.aftersales.dispatch", "com.aftersales.common"})
public class DispatchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DispatchServiceApplication.class, args);
    }
}
