package com.aftersales.ai.controller;

import com.aftersales.ai.service.ReplyDraftService;
import com.aftersales.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 智能回复接口
 * 设计思路：草稿生成暴露成手动触发接口，客服在工作台可对指定工单
 * 现场生成/重新生成 AI 草稿，生成结果同步回写工单中心
 */
@Tag(name = "智能回复")
@RestController
@RequestMapping("/ai/reply")
@RequiredArgsConstructor
public class ReplyController {

    private final ReplyDraftService replyDraftService;

    @Operation(summary = "为指定工单生成回复草稿（RAG 召回知识库 Top3）")
    @PostMapping("/draft")
    public Result<String> draft(@RequestParam Long workOrderId) {
        return Result.success(replyDraftService.draft(workOrderId));
    }
}
