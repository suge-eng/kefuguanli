package com.aftersales.stats.feign.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 客服信息视图（只声明看板需要的字段，避免依赖派单服务的实体类）
 * 设计思路：跨服务 DTO 各自维护，服务间只通过 JSON 契约耦合，
 * 一方加字段不会逼迫另一方同步升级
 */
@Data
public class CustomerServiceView {

    private Long id;

    private String name;

    private String onlineStatus;

    private Integer maxLoad;

    private BigDecimal resolveRate;
}
