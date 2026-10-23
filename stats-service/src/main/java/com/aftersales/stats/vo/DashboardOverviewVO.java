package com.aftersales.stats.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 今日概览
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverviewVO {

    /** 统计日期 */
    private String statDate;

    /** 工单总量 */
    private Integer totalCount;

    /** 已解决量 */
    private Integer resolvedCount;

    /** 升级量 */
    private Integer escalatedCount;

    /** 解决率（百分比，保留一位小数） */
    private Double resolveRate;

    /** 平均解决时长（分钟） */
    private Integer avgResolveMinutes;
}
