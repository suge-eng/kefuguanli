package com.aftersales.stats.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工单指标日汇总实体（按月日 + 工单类型唯一）
 * 设计思路：看板查询走预聚合表而不是实时扫工单库——
 * 微服务下统计服务不持有工单数据，实时聚合只能跨服务调用，既慢又耦合；
 * 改为消费事件异步累加，查询时只做一次按日期范围的小结果集读取
 */
@Data
@TableName("work_order_metrics_daily")
public class WorkOrderMetricsDaily {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 统计日期 */
    private LocalDate statDate;

    /** 工单类型；ALL 表示全量维度（避免前端再做跨类型求和） */
    private String workType;

    /** 工单总量 */
    private Integer totalCount;

    /** 已解决量 */
    private Integer resolvedCount;

    /** 升级（预警触发 URGENT）量 */
    private Integer escalatedCount;

    /** 平均解决时长（分钟，增量加权平均） */
    private Integer avgResolveMinutes;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
