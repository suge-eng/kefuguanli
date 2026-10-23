package com.aftersales.stats.mapper;

import com.aftersales.stats.entity.WorkOrderMetricsDaily;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 指标日汇总 Mapper
 * 设计思路：累加必须用数据库原子 upsert（INSERT ... ON DUPLICATE KEY UPDATE），
 * 而不是「先查再改」——统计事件并发消费时先查后写会丢计数；
 * 平均解决时长用增量加权平均公式，避免额外维护总时长字段
 */
@Mapper
public interface WorkOrderMetricsDailyMapper extends BaseMapper<WorkOrderMetricsDaily> {

    /**
     * 原子累加一行指标（行不存在则插入）
     * 注意：ON DUPLICATE KEY UPDATE 的赋值按书写顺序执行，
     * avg_resolve_minutes 必须写在 resolved_count 之前才能用到自增前的旧分母
     */
    @Insert("""
            INSERT INTO work_order_metrics_daily
                (stat_date, work_type, total_count, resolved_count, escalated_count, avg_resolve_minutes)
            VALUES
                (#{statDate}, #{workType}, #{total}, #{resolved}, #{escalated}, #{minutes})
            ON DUPLICATE KEY UPDATE
                total_count = total_count + #{total},
                avg_resolve_minutes = CASE WHEN #{resolved} > 0
                    THEN ROUND((avg_resolve_minutes * resolved_count + #{minutes}) / (resolved_count + #{resolved}))
                    ELSE avg_resolve_minutes END,
                resolved_count = resolved_count + #{resolved},
                escalated_count = escalated_count + #{escalated}
            """)
    int accumulate(@Param("statDate") LocalDate statDate,
                   @Param("workType") String workType,
                   @Param("total") int total,
                   @Param("resolved") int resolved,
                   @Param("escalated") int escalated,
                   @Param("minutes") int minutes);
}
