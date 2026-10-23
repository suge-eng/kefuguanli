package com.aftersales.workbench.feign;

import com.aftersales.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * AI 服务 Feign 客户端
 * 设计思路：工作台只暴露「生成回复草稿」一个能力给客服，
 * RAG 召回、Prompt 组装、大模型调用细节全部收敛在 ai-service 内部
 */
@FeignClient(name = "ai-service", fallbackFactory = AiClientFallbackFactory.class)
public interface AiClient {

    /**
     * 生成工单回复草稿（RAG 召回知识库 + 大模型生成，草稿由 ai-service 回写工单详情）
     *
     * @param workOrderId 工单ID
     * @return 草稿文本（客服审核修改后再走 reply 接口正式发送）
     */
    @PostMapping("/ai/reply/draft")
    Result<String> draft(@RequestParam("workOrderId") Long workOrderId);
}
