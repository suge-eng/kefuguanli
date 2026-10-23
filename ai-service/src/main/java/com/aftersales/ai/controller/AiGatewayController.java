package com.aftersales.ai.controller;

import com.aftersales.ai.gateway.AiGateway;
import com.aftersales.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 能力网关运维接口
 * 设计思路：降级开关与调用统计暴露成 HTTP 接口，运维不用进 Redis 就能
 * 一键切断 AI 调用（大模型故障时保主链路）/查看调用量与平均耗时
 */
@Tag(name = "AI能力网关")
@RestController
@RequestMapping("/ai/gateway")
@RequiredArgsConstructor
public class AiGatewayController {

    private final AiGateway aiGateway;

    @Operation(summary = "查询 AI 调用统计（chat/embed 调用量与平均耗时）")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(aiGateway.stats());
    }

    @Operation(summary = "切换降级开关（true=切断 AI 调用，业务走兜底）")
    @PostMapping("/degrade")
    public Result<Void> degrade(@RequestParam boolean enabled) {
        aiGateway.setDegraded(enabled);
        return Result.success();
    }

    @Operation(summary = "连通性测试（Mock 模式返回 Mock 文案）")
    @GetMapping("/test")
    public Result<String> test() {
        return Result.success(aiGateway.chat("你是测试助手", "回复'连接正常'四个字"));
    }
}
