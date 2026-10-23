package com.aftersales.stats.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 趋势图单点（按日期）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrendPointVO {

    private String statDate;

    private Integer totalCount;

    private Integer resolvedCount;

    private Integer escalatedCount;

    private Integer avgResolveMinutes;
}
