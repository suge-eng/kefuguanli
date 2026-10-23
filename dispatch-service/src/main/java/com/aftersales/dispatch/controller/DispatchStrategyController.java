package com.aftersales.dispatch.controller;

import com.aftersales.common.exception.BizException;
import com.aftersales.common.result.Result;
import com.aftersales.dispatch.entity.DispatchStrategyConfig;
import com.aftersales.dispatch.mapper.DispatchStrategyConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 派单策略配置接口
 * 设计思路：策略热切换——改配置即生效，不用发版；
 * 「全禁用 + 启用目标」放在一个事务里，保证任何时刻不会同时启用两个策略
 */
@Tag(name = "派单策略配置")
@RestController
@RequestMapping("/dispatch/strategy")
@RequiredArgsConstructor
public class DispatchStrategyController {

    private final DispatchStrategyConfigMapper configMapper;

    @Operation(summary = "查询策略列表")
    @GetMapping
    public Result<List<DispatchStrategyConfig>> list() {
        return Result.success(configMapper.selectList(
                new LambdaQueryWrapper<DispatchStrategyConfig>().orderByAsc(DispatchStrategyConfig::getId)));
    }

    @Operation(summary = "启用指定策略（自动禁用其他）")
    @PutMapping("/{code}/enable")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> enable(@PathVariable String code) {
        DispatchStrategyConfig target = configMapper.selectOne(
                new LambdaQueryWrapper<DispatchStrategyConfig>()
                        .eq(DispatchStrategyConfig::getStrategyCode, code));
        if (target == null) {
            throw new BizException("策略不存在: " + code);
        }
        // 先全部禁用，再启用目标，事务保证原子性
        configMapper.update(null, new LambdaUpdateWrapper<DispatchStrategyConfig>()
                .set(DispatchStrategyConfig::getEnabled, 0));
        configMapper.update(null, new LambdaUpdateWrapper<DispatchStrategyConfig>()
                .eq(DispatchStrategyConfig::getStrategyCode, code)
                .set(DispatchStrategyConfig::getEnabled, 1));
        return Result.success();
    }
}
