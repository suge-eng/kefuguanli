package com.aftersales.ai.service;

import com.aftersales.ai.entity.KnowledgeBase;
import com.aftersales.ai.feign.WorkOrderClient;
import com.aftersales.ai.feign.dto.AiDraftRequestDto;
import com.aftersales.ai.feign.dto.WorkOrderDetailVOView;
import com.aftersales.ai.gateway.AiGateway;
import com.aftersales.ai.mapper.KnowledgeBaseMapper;
import com.aftersales.common.exception.BizException;
import com.aftersales.common.result.Result;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 智能回复 Agent
 * 设计思路：先语义召回知识库 Top3 作为「可选解决方案」，再让大模型严格依据方案生成草稿，
 * 约束模型不编造方案外承诺（减少幻觉）；召回或生成任一环节故障都有兜底——
 * 草稿是辅助产物，宁可质量降级也不能没有产出
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplyDraftService {

    private final WorkOrderClient workOrderClient;
    private final KnowledgeService knowledgeService;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final AiGateway aiGateway;

    /**
     * 生成回复草稿并回写工单中心，返回草稿文本
     */
    public String draft(Long workOrderId) {
        WorkOrderDetailVOView vo = queryDetail(workOrderId);
        String title = vo.getWorkOrder().getTitle() == null ? "" : vo.getWorkOrder().getTitle();
        String workType = vo.getWorkOrder().getWorkType() == null ? "" : vo.getWorkOrder().getWorkType();
        String content = vo.getDetail() == null || vo.getDetail().getContent() == null
                ? "" : vo.getDetail().getContent();

        // 1. 语义召回 Top3；Milvus/向量挂了降级为数据库取前 3 条启用知识，保证草稿链路不断
        List<KnowledgeHit> hits;
        try {
            hits = knowledgeService.search(title + "\n" + content, 3);
        } catch (Exception e) {
            log.warn("语义召回失败，降级为数据库取启用知识: workOrderId={}, err={}", workOrderId, e.getMessage());
            hits = fallbackRecall();
        }

        // 2. 生成草稿；大模型挂了用模板兜底
        String draft;
        try {
            draft = aiGateway.chat(buildSystemPrompt(), buildUserPrompt(title, workType, content, hits));
        } catch (Exception e) {
            log.warn("草稿生成失败，走模板兜底: workOrderId={}, err={}", workOrderId, e.getMessage());
            draft = buildFallbackDraft(title, hits);
        }

        // 3. 回写工单中心（失败抛 BizException，由调用方/MQ 决定重试）
        String citedKnowledge = hits.stream()
                .map(KnowledgeHit::getTitle)
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining("、"));
        AiDraftRequestDto dto = new AiDraftRequestDto();
        dto.setDraft(draft);
        dto.setCitedKnowledge(citedKnowledge.isBlank() ? null : citedKnowledge);
        Result<Void> result = workOrderClient.aiDraft(workOrderId, dto);
        if (result == null || result.getCode() == null || result.getCode() != 200) {
            throw new BizException("回复草稿回写失败: " + (result == null ? "无响应" : result.getMessage()));
        }
        log.info("回复草稿回写成功: workOrderId={}, cited={}", workOrderId, citedKnowledge);
        return draft;
    }

    private WorkOrderDetailVOView queryDetail(Long workOrderId) {
        Result<WorkOrderDetailVOView> result;
        try {
            result = workOrderClient.detail(workOrderId);
        } catch (Exception e) {
            throw new BizException("查询工单详情失败: " + e.getMessage());
        }
        if (result == null || result.getCode() == null || result.getCode() != 200
                || result.getData() == null || result.getData().getWorkOrder() == null) {
            throw new BizException("查询工单详情失败: " + (result == null ? "无响应" : result.getMessage()));
        }
        return result.getData();
    }

    /** 召回降级：向量检索不可用时直接取启用知识前 3 条（无相关性排序，仅保证有参考方案） */
    private List<KnowledgeHit> fallbackRecall() {
        List<KnowledgeBase> list = knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getStatus, "ENABLED")
                .orderByAsc(KnowledgeBase::getId)
                .last("LIMIT 3"));
        List<KnowledgeHit> hits = new ArrayList<>(list.size());
        for (KnowledgeBase kb : list) {
            hits.add(new KnowledgeHit(kb.getId(), kb.getTitle(), kb.getContent(), null));
        }
        return hits;
    }

    private String buildSystemPrompt() {
        return "你是电商售后客服助手，根据给定的解决方案为用户生成回复草稿。"
                + "要求：语气温和专业、150字以内、严格依据方案内容、"
                + "禁止承诺方案之外的赔偿、结尾告知用户可继续补充问题。";
    }

    private String buildUserPrompt(String title, String workType, String content, List<KnowledgeHit> hits) {
        StringBuilder sb = new StringBuilder();
        sb.append("工单标题：").append(title).append('\n');
        sb.append("工单类型：").append(workType).append('\n');
        sb.append("用户诉求：").append(content).append('\n');
        sb.append("可选解决方案：\n");
        if (hits.isEmpty()) {
            sb.append("（暂无匹配方案，请按通用售后政策礼貌回复）");
        } else {
            for (int i = 0; i < hits.size(); i++) {
                KnowledgeHit hit = hits.get(i);
                sb.append("【方案").append(i + 1).append("】").append(hit.getTitle())
                        .append("：").append(hit.getContent()).append('\n');
            }
        }
        return sb.toString();
    }

    /** 模板兜底草稿：大模型不可用时也能给客服一个可编辑的底子 */
    private String buildFallbackDraft(String title, List<KnowledgeHit> hits) {
        String cited = hits.stream()
                .map(KnowledgeHit::getTitle)
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining("、"));
        return "您好，关于您反馈的\"" + title + "\"，我们非常重视。"
                + (cited.isBlank() ? "" : "参考售后政策：" + cited + "。")
                + "我们的客服将尽快为您跟进处理，请保持联系方式畅通。";
    }
}
