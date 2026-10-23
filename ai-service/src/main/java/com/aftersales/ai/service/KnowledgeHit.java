package com.aftersales.ai.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库召回命中项
 * 设计思路：语义召回结果用简单 POJO 承载（不暴露 Milvus SDK 类型给上层），
 * 回复 Agent 只关心「哪条知识、什么内容、相关度多少」
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeHit {

    /** 知识条目 ID（knowledge_base.id） */
    private Long kbId;

    private String title;

    private String content;

    /** 相似度得分（COSINE 越大越相关） */
    private Float score;
}
