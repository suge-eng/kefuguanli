package com.aftersales.ai.controller;

import com.aftersales.ai.entity.KnowledgeBase;
import com.aftersales.ai.mapper.KnowledgeBaseMapper;
import com.aftersales.ai.service.KnowledgeService;
import com.aftersales.common.result.PageResult;
import com.aftersales.common.result.Result;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 知识库管理接口
 * 设计思路：知识条目在 MySQL 维护，vectorize 接口提供手动触发增量同步能力——
 * 启动时虽会自动补齐，但运营临时加知识后不想重启服务时可手动触发
 */
@Tag(name = "知识库管理")
@RestController
@RequestMapping("/ai/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeService knowledgeService;

    @Operation(summary = "分页查询知识条目")
    @GetMapping("/page")
    public Result<PageResult<KnowledgeBase>> page(@RequestParam(defaultValue = "1") Long pageNum,
                                                  @RequestParam(defaultValue = "10") Long pageSize,
                                                  @RequestParam(required = false) String category) {
        Page<KnowledgeBase> page = knowledgeBaseMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(category != null && !category.isBlank(), KnowledgeBase::getCategory, category)
                        .orderByDesc(KnowledgeBase::getId));
        return Result.success(PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords()));
    }

    @Operation(summary = "手动触发待向量化条目同步到 Milvus")
    @PostMapping("/vectorize")
    public Result<Void> vectorize() {
        knowledgeService.vectorizePending();
        return Result.success();
    }

    @Operation(summary = "全量重建向量集合（数据修复或切换 embedding 模型后使用）")
    @PostMapping("/rebuild")
    public Result<Void> rebuild() {
        knowledgeService.rebuildAll();
        return Result.success();
    }
}
