package com.aftersales.workorder;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 工单中心启动类
 * scanBasePackages 显式扫描 com.aftersales：既扫本服务包，也扫公共模块的
 * 全局异常处理器等组件（公共模块无启动类，依赖业务服务扫描注册）
 */
@EnableDiscoveryClient
@MapperScan("com.aftersales.workorder.mapper")
@SpringBootApplication(scanBasePackages = {"com.aftersales.workorder", "com.aftersales.common"})
public class WorkOrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkOrderServiceApplication.class, args);
    }
}
