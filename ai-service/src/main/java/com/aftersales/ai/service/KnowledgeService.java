package com.aftersales.ai.service;

import com.aftersales.ai.entity.KnowledgeBase;
import com.aftersales.ai.gateway.AiGateway;
import com.aftersales.ai.mapper.KnowledgeBaseMapper;
import com.aftersales.ai.milvus.MilvusInitializer;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库服务（向量化 + 语义召回）
 * 设计思路：MySQL 存知识原文（唯一事实源），Milvus 存向量副本；
 * 召回结果只回传 Milvus 中冗余的 title/content，免回查 MySQL，减少一次 IO
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final MilvusServiceClient milvusClient;
    private final AiGateway aiGateway;
    // MilvusInitializer 依赖本服务做存量补齐，直接注入会形成 Bean 循环依赖；
    // ObjectProvider 延迟到调用时才解析，避开启动期循环
    private final ObjectProvider<MilvusInitializer> milvusInitializerProvider;

    @Value("${milvus.collection:knowledge_base}")
    private String collection;

    /**
     * 全量重建向量集合（种子数据修复、embedding 模型切换后使用）
     * 设计思路：旧向量与原文已不一致时增量同步无意义——drop 集合并按最新 schema 重建，
     * 再把 vectorized 标记清零，复用既有增量逻辑全量重灌，保证 MySQL 与 Milvus 严格一致
     */
    public void rebuildAll() {
        MilvusInitializer initializer = milvusInitializerProvider.getObject();
        initializer.dropCollection();
        initializer.initCollection();
        // 全部条目标记为未向量化，随后走既有增量逻辑全量重灌
        knowledgeBaseMapper.update(null, new LambdaUpdateWrapper<KnowledgeBase>()
                .set(KnowledgeBase::getVectorized, 0));
        vectorizePending();
        log.info("知识库向量集合全量重建完成");
    }

    /**
     * 增量向量化：把 status=ENABLED 且未向量化的知识条目写入 Milvus
     * 单条向量化失败只记 warn 跳过，不影响其他条目（下次启动会重试）
     */
    public void vectorizePending() {
        List<KnowledgeBase> pending = knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getStatus, "ENABLED")
                .eq(KnowledgeBase::getVectorized, 0)
                .orderByAsc(KnowledgeBase::getId));
        if (pending.isEmpty()) {
            log.info("无待向量化的知识条目");
            return;
        }
        log.info("开始向量化知识条目: {} 条", pending.size());

        // 逐条 embed（单条失败跳过），成功的攒批一次 insert Milvus
        List<Long> kbIds = new ArrayList<>(pending.size());
        List<List<Float>> vectors = new ArrayList<>(pending.size());
        List<String> titles = new ArrayList<>(pending.size());
        List<String> contents = new ArrayList<>(pending.size());
        List<KnowledgeBase> succeeded = new ArrayList<>(pending.size());
        for (KnowledgeBase kb : pending) {
            try {
                List<Float> vector = aiGateway.embed(kb.getTitle() + "\n" + kb.getContent());
                kbIds.add(kb.getId());
                vectors.add(vector);
                // VarChar 字段超长会被 Milvus 拒绝，截断保护（维度远小于 256/2048 的内容不会截断）
                titles.add(truncate(kb.getTitle(), 250));
                contents.add(truncate(kb.getContent(), 2000));
                succeeded.add(kb);
            } catch (Exception e) {
                log.warn("知识条目向量化失败，跳过: kbId={}, err={}", kb.getId(), e.getMessage());
            }
        }
        if (succeeded.isEmpty()) {
            return;
        }

        R<?> insertResp = milvusClient.insert(InsertParam.newBuilder()
                .withCollectionName(collection)
                .withFields(List.of(
                        new InsertParam.Field(MilvusInitializer.FIELD_KB_ID, kbIds),
                        new InsertParam.Field(MilvusInitializer.FIELD_VECTOR, vectors),
                        new InsertParam.Field(MilvusInitializer.FIELD_TITLE, titles),
                        new InsertParam.Field(MilvusInitializer.FIELD_CONTENT, contents)))
                .build());
        if (insertResp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus 写入失败: " + insertResp.getMessage());
        }
        // 写入成功后回置 vectorized=1，下次不再重复同步
        for (KnowledgeBase kb : succeeded) {
            kb.setVectorized(1);
            knowledgeBaseMapper.updateById(kb);
        }
        log.info("知识条目向量化完成: {} 条", succeeded.size());
    }

    /**
     * 语义召回 TopK
     *
     * @param query 查询文本（通常是工单标题+诉求）
     * @param topK  返回条数
     */
    public List<KnowledgeHit> search(String query, int topK) {
        List<Float> queryVector = aiGateway.embed(query);
        // 2.5.4 SDK：search(SearchParam) 直接返回 R<SearchResults>
        R<SearchResults> searchResp = milvusClient.search(SearchParam.newBuilder()
                .withCollectionName(collection)
                .withMetricType(MetricType.COSINE)
                .withVectorFieldName(MilvusInitializer.FIELD_VECTOR)
                .withVectors(List.of(queryVector))
                .withTopK(topK)
                .withOutFields(List.of(MilvusInitializer.FIELD_KB_ID,
                        MilvusInitializer.FIELD_TITLE, MilvusInitializer.FIELD_CONTENT))
                .build());
        if (searchResp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus 检索失败: " + searchResp.getMessage());
        }

        // 单 query 检索，结果都挂在第 0 个 target 下
        // 2.5.4 SDK：SearchResultsWrapper 构造函数收的是 SearchResultData（SearchResults.getResults()）
        SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResp.getData().getResults());
        List<SearchResultsWrapper.IDScore> idScores = wrapper.getIDScore(0);
        List<?> kbIdData = wrapper.getFieldData(MilvusInitializer.FIELD_KB_ID, 0);
        List<?> titleData = wrapper.getFieldData(MilvusInitializer.FIELD_TITLE, 0);
        List<?> contentData = wrapper.getFieldData(MilvusInitializer.FIELD_CONTENT, 0);

        List<KnowledgeHit> hits = new ArrayList<>(idScores.size());
        for (int i = 0; i < idScores.size(); i++) {
            // IDScore 与 outFields 按下标一一对齐
            Long kbId = kbIdData != null && i < kbIdData.size()
                    ? ((Number) kbIdData.get(i)).longValue() : idScores.get(i).getLongID();
            String title = titleData != null && i < titleData.size() ? String.valueOf(titleData.get(i)) : null;
            String content = contentData != null && i < contentData.size() ? String.valueOf(contentData.get(i)) : null;
            hits.add(new KnowledgeHit(kbId, title, content, idScores.get(i).getScore()));
        }
        return hits;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
