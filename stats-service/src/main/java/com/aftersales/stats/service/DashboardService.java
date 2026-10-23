package com.aftersales.stats.service;

import com.aftersales.common.result.PageResult;
import com.aftersales.common.result.Result;
import com.aftersales.stats.entity.WorkOrderMetricsDaily;
import com.aftersales.stats.feign.CustomerServiceClient;
import com.aftersales.stats.feign.dto.CustomerServiceView;
import com.aftersales.stats.mapper.WorkOrderMetricsDailyMapper;
import com.aftersales.stats.vo.CsLoadVO;
import com.aftersales.stats.vo.DashboardOverviewVO;
import com.aftersales.stats.vo.TrendPointVO;
import com.aftersales.stats.vo.TypeDistributionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 看板查询服务
 * 设计思路：所有查询只读预聚合表 work_order_metrics_daily（数据量=天数×类型数，非常小），
 * 不扫工单库、不做跨服务聚合；趋势图对缺失日期补 0，避免前端画图断点
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** 客服负载 key 前缀，与派单服务 LoadService 保持一致 */
    private static final String CS_LOAD_KEY_PREFIX = "dispatch:cs:load:";

    private final WorkOrderMetricsDailyMapper metricsMapper;
    private final CustomerServiceClient customerServiceClient;
    private final StringRedisTemplate stringRedisTemplate;

    /** 今日概览：读当天 ALL 维度，无数据返回全 0 */
    public DashboardOverviewVO overview() {
        LocalDate today = LocalDate.now();
        WorkOrderMetricsDaily row = metricsMapper.selectOne(new LambdaQueryWrapper<WorkOrderMetricsDaily>()
                .eq(WorkOrderMetricsDaily::getStatDate, today)
                .eq(WorkOrderMetricsDaily::getWorkType, MetricsService.TYPE_ALL));
        if (row == null) {
            return new DashboardOverviewVO(today.toString(), 0, 0, 0, 0.0, 0);
        }
        return new DashboardOverviewVO(today.toString(), row.getTotalCount(), row.getResolvedCount(),
                row.getEscalatedCount(), rate(row.getResolvedCount(), row.getTotalCount()),
                row.getAvgResolveMinutes());
    }

    /** 近 N 天趋势（ALL 维度，缺失日期补 0，按日期升序） */
    public List<TrendPointVO> trend(int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1L);
        List<WorkOrderMetricsDaily> rows = metricsMapper.selectList(new LambdaQueryWrapper<WorkOrderMetricsDaily>()
                .between(WorkOrderMetricsDaily::getStatDate, start, end)
                .eq(WorkOrderMetricsDaily::getWorkType, MetricsService.TYPE_ALL));
        Map<LocalDate, WorkOrderMetricsDaily> rowMap = new LinkedHashMap<>();
        rows.forEach(row -> rowMap.put(row.getStatDate(), row));

        List<TrendPointVO> points = new ArrayList<>(days);
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            WorkOrderMetricsDaily row = rowMap.get(date);
            points.add(row == null
                    ? new TrendPointVO(date.toString(), 0, 0, 0, 0)
                    : new TrendPointVO(date.toString(), row.getTotalCount(), row.getResolvedCount(),
                    row.getEscalatedCount(), row.getAvgResolveMinutes()));
        }
        return points;
    }

    /** 近 N 天工单类型分布（按类型聚合后按总量倒序） */
    public List<TypeDistributionVO> typeDistribution(int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1L);
        List<WorkOrderMetricsDaily> rows = metricsMapper.selectList(new LambdaQueryWrapper<WorkOrderMetricsDaily>()
                .between(WorkOrderMetricsDaily::getStatDate, start, end)
                .ne(WorkOrderMetricsDaily::getWorkType, MetricsService.TYPE_ALL));

        // 同类型跨天累加：total/resolved/escalated 求和
        Map<String, int[]> agg = new LinkedHashMap<>();
        for (WorkOrderMetricsDaily row : rows) {
            int[] values = agg.computeIfAbsent(row.getWorkType(), k -> new int[3]);
            values[0] += row.getTotalCount();
            values[1] += row.getResolvedCount();
            values[2] += row.getEscalatedCount();
        }
        List<TypeDistributionVO> result = new ArrayList<>(agg.size());
        agg.forEach((workType, values) -> result.add(new TypeDistributionVO(workType,
                values[0], values[1], values[2], rate(values[1], values[0]))));
        result.sort(Comparator.comparing(TypeDistributionVO::getTotalCount).reversed());
        return result;
    }

    /**
     * 客服实时负载：客服基础信息远程读派单服务，负载值读共享 Redis
     * 设计思路：即使派单服务不可用也只影响看板这一块，返回空列表由前端提示，不阻断整个看板
     */
    public List<CsLoadVO> csLoad() {
        List<CustomerServiceView> services;
        try {
            Result<PageResult<CustomerServiceView>> result = customerServiceClient.page(1L, 100L);
            if (result == null || result.getCode() == null || result.getCode() != 200
                    || result.getData() == null || result.getData().getRecords() == null) {
                log.warn("查询客服列表失败，看板负载返回空: {}", result == null ? "无响应" : result.getMessage());
                return List.of();
            }
            services = result.getData().getRecords();
        } catch (Exception e) {
            log.warn("查询客服列表异常，看板负载返回空: {}", e.getMessage());
            return List.of();
        }

        List<CsLoadVO> list = new ArrayList<>(services.size());
        for (CustomerServiceView cs : services) {
            int load = readLoad(cs.getId());
            int maxLoad = cs.getMaxLoad() == null ? 0 : cs.getMaxLoad();
            double loadRate = maxLoad <= 0 ? 0.0 : BigDecimal.valueOf(load)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(maxLoad), 1, RoundingMode.HALF_UP)
                    .doubleValue();
            list.add(new CsLoadVO(cs.getId(), cs.getName(), cs.getOnlineStatus(), load, maxLoad,
                    cs.getResolveRate(), loadRate));
        }
        return list;
    }

    private int readLoad(Long csId) {
        try {
            String value = stringRedisTemplate.opsForValue().get(CS_LOAD_KEY_PREFIX + csId);
            return value == null ? 0 : Integer.parseInt(value);
        } catch (Exception e) {
            log.warn("读取客服负载失败，按0处理: csId={}, err={}", csId, e.getMessage());
            return 0;
        }
    }

    /** 百分比，保留一位小数；分母为 0 返回 0.0 */
    private double rate(Integer numerator, Integer denominator) {
        if (numerator == null || denominator == null || denominator <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
