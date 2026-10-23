package com.aftersales.stats.controller;

import com.aftersales.common.result.Result;
import com.aftersales.stats.service.DashboardService;
import com.aftersales.stats.vo.CsLoadVO;
import com.aftersales.stats.vo.DashboardOverviewVO;
import com.aftersales.stats.vo.TrendPointVO;
import com.aftersales.stats.vo.TypeDistributionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 统计分析看板接口
 * 设计思路：四个接口对应前端看板的四个区块，职责单一、互不依赖，
 * 任一区块数据缺失不影响其他区块展示
 */
@Tag(name = "统计分析看板")
@RestController
@RequestMapping("/stats/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "今日概览：总量/已解决/升级/解决率/平均解决时长")
    @GetMapping("/overview")
    public Result<DashboardOverviewVO> overview() {
        return Result.success(dashboardService.overview());
    }

    @Operation(summary = "近 N 天工单趋势（默认7天，缺失日期补0）")
    @GetMapping("/trend")
    public Result<List<TrendPointVO>> trend(@RequestParam(defaultValue = "7") Integer days) {
        return Result.success(dashboardService.trend(clampDays(days)));
    }

    @Operation(summary = "近 N 天工单类型分布（默认7天）")
    @GetMapping("/type-distribution")
    public Result<List<TypeDistributionVO>> typeDistribution(@RequestParam(defaultValue = "7") Integer days) {
        return Result.success(dashboardService.typeDistribution(clampDays(days)));
    }

    @Operation(summary = "客服实时负载（Redis 计数 + 派单服务客服信息）")
    @GetMapping("/cs-load")
    public Result<List<CsLoadVO>> csLoad() {
        return Result.success(dashboardService.csLoad());
    }

    /** 限制查询天数，防止恶意的超大范围查询打满数据库 */
    private int clampDays(Integer days) {
        if (days == null || days < 1) {
            return 7;
        }
        return Math.min(days, 90);
    }
}
