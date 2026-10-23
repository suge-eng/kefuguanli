package com.aftersales.ai.feign.dto;

import lombok.Data;

/**
 * AI 回复草稿回写请求
 * 设计思路：草稿只写入 detail.ai_draft_reply 供客服参考，不改变工单状态；
 * 正式回复仍走客服 reply 接口，保证「AI 起草、人工把关」
 */
@Data
public class AiDraftRequestDto {

    /** 草稿内容 */
    private String draft;

    /** 草稿引用的知识库条目标题（溯源展示用，可空） */
    private String citedKnowledge;
}
