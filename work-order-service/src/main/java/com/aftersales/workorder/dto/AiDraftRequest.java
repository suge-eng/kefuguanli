package com.aftersales.workorder.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 回复草稿回写请求（ai-service 智能回复 Agent 调用）
 * 设计思路：草稿只写入 detail.ai_draft_reply 供客服参考，
 * 不改变工单状态；正式回复仍走客服 reply 接口，保证「AI 起草、人工把关」
 */
@Data
public class AiDraftRequest {

    @NotBlank(message = "草稿内容不能为空")
    private String draft;

    /** 草稿引用的知识库条目标题（溯源展示用，可空） */
    private String citedKnowledge;
}
