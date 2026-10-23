package com.aftersales.workbench;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 客服工作台服务启动类
 */
@EnableFeignClients
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {"com.aftersales.workbench", "com.aftersales.common"})
public class WorkbenchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkbenchServiceApplication.class, args);
    }
}
