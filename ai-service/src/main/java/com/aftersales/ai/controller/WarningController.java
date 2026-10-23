package com.aftersales.ai.controller;

import com.aftersales.ai.entity.WarningRuleConfig;
import com.aftersales.ai.mapper.WarningRuleConfigMapper;
import com.aftersales.common.result.Result;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 预警规则管理接口
 * 设计思路：规则启用/禁用走配置表热生效，评估逻辑每次实时查表，
 * 运维调整规则不用重启服务
 */
@Tag(name = "异常预警规则")
@RestController
@RequestMapping("/ai/warning")
@RequiredArgsConstructor
public class WarningController {

    private final WarningRuleConfigMapper ruleConfigMapper;

    @Operation(summary = "查询全部预警规则")
    @GetMapping("/rules")
    public Result<List<WarningRuleConfig>> rules() {
        return Result.success(ruleConfigMapper.selectList(
                new LambdaQueryWrapper<WarningRuleConfig>().orderByAsc(WarningRuleConfig::getId)));
    }

    @Operation(summary = "启用/禁用指定规则")
    @PutMapping("/rules/{code}/toggle")
    public Result<Void> toggle(@PathVariable String code, @RequestParam boolean enabled) {
        ruleConfigMapper.update(null, new LambdaUpdateWrapper<WarningRuleConfig>()
                .eq(WarningRuleConfig::getRuleCode, code)
                .set(WarningRuleConfig::getEnabled, enabled ? 1 : 0));
        return Result.success();
    }
}
