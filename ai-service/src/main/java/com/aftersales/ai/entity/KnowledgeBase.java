package com.aftersales.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库条目表实体
 * 设计思路：知识库内容落 MySQL 做唯一事实源，Milvus 只存向量副本用于语义召回；
 * vectorized 标记增量同步状态，启动时自动把未向量化的条目补进 Milvus
 */
@Data
@TableName("knowledge_base")
public class KnowledgeBase {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 知识分类（如 REFUND/LOGISTICS 等售后场景） */
    private String category;

    /** 条目标题 */
    private String title;

    /** 条目内容（解决方案正文） */
    private String content;

    /** 状态：ENABLED/DISABLED */
    private String status;

    /** 是否已向量化写入 Milvus：0/1 */
    private Integer vectorized;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
