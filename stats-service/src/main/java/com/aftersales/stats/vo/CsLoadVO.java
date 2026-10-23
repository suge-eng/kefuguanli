package com.aftersales.stats.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 客服实时负载项
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CsLoadVO {

    private Long csId;

    private String name;

    /** 在线状态：ONLINE/OFFLINE */
    private String onlineStatus;

    /** 当前在办工单数（Redis 实时值） */
    private Integer load;

    /** 最大并发在办数 */
    private Integer maxLoad;

    /** 历史解决率 */
    private BigDecimal resolveRate;

    /** 负载占用率（百分比，负载/上限） */
    private Double loadRate;
}
