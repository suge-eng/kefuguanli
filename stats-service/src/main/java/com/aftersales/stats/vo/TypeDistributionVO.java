package com.aftersales.stats.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单类型分布项
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TypeDistributionVO {

    private String workType;

    private Integer totalCount;

    private Integer resolvedCount;

    private Integer escalatedCount;

    /** 该类型解决率（百分比） */
    private Double resolveRate;
}
