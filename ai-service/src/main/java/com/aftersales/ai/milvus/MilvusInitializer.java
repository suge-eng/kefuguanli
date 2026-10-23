package com.aftersales.ai.milvus;

import com.aftersales.ai.config.AiProperties;
import com.aftersales.ai.service.KnowledgeService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.collection.ReleaseCollectionParam;
import io.milvus.param.index.CreateIndexParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Milvus 集合初始化器
 * 设计思路：启动时自动建集合/建索引/加载/补齐存量向量，免去手工 DDL；
 * 整体 try-catch 只记 warn 不阻塞启动——Milvus 挂了 ai-service 的其他 Agent
 * （结构化提取/预警）仍可用，RAG 召回由 KnowledgeService 降级逻辑兜底
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MilvusInitializer implements ApplicationRunner {

    /** 向量字段名（检索时也引用同一常量，避免写错） */
    public static final String FIELD_VECTOR = "vector";
    public static final String FIELD_KB_ID = "kb_id";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_CONTENT = "content";

    private final MilvusServiceClient milvusClient;
    private final KnowledgeService knowledgeService;
    private final AiProperties aiProperties;

    @Value("${milvus.collection:knowledge_base}")
    private String collection;

    @Override
    public void run(ApplicationArguments args) {
        try {
            initCollection();
            // 补齐存量未向量化的知识条目（新增条目启动后自动追上）
            knowledgeService.vectorizePending();
        } catch (Exception e) {
            log.warn("Milvus 初始化失败，RAG 召回将走数据库降级: {}", e.getMessage());
        }
    }

    /**
     * 删除集合（重建场景用）
     * 设计思路：已 load 的集合不能直接 drop，必须先 release；
     * 集合不存在时静默跳过，保证重建逻辑幂等可重入
     */
    public void dropCollection() {
        R<Boolean> hasResp = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collection)
                .build());
        if (hasResp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("检查集合失败: " + hasResp.getMessage());
        }
        if (!Boolean.TRUE.equals(hasResp.getData())) {
            log.info("Milvus 集合不存在，跳过删除: {}", collection);
            return;
        }
        R<?> releaseResp = milvusClient.releaseCollection(ReleaseCollectionParam.newBuilder()
                .withCollectionName(collection)
                .build());
        if (releaseResp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("释放集合失败: " + releaseResp.getMessage());
        }
        R<?> dropResp = milvusClient.dropCollection(DropCollectionParam.newBuilder()
                .withCollectionName(collection)
                .build());
        if (dropResp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("删除集合失败: " + dropResp.getMessage());
        }
        log.info("Milvus 集合已删除: {}", collection);
    }

    public void initCollection() {
        int dimension = aiProperties.getEmbedding().getDimension();
        R<Boolean> hasResp = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collection)
                .build());
        if (hasResp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("检查集合失败: " + hasResp.getMessage());
        }
        if (Boolean.TRUE.equals(hasResp.getData())) {
            log.info("Milvus 集合已存在，跳过建集合: {}", collection);
        } else {
            // 字段：自增主键 id / 知识条目 kb_id / 向量 vector / 冗余 title、content（召回时随结果带出，免回查 MySQL）
            R<?> createResp = milvusClient.createCollection(CreateCollectionParam.newBuilder()
                    .withCollectionName(collection)
                    .addFieldType(FieldType.newBuilder()
                            .withName("id")
                            .withDataType(DataType.Int64)
                            .withPrimaryKey(true)
                            .withAutoID(true)
                            .build())
                    .addFieldType(FieldType.newBuilder()
                            .withName(FIELD_KB_ID)
                            .withDataType(DataType.Int64)
                            .build())
                    .addFieldType(FieldType.newBuilder()
                            .withName(FIELD_VECTOR)
                            .withDataType(DataType.FloatVector)
                            .withDimension(dimension)
                            .build())
                    .addFieldType(FieldType.newBuilder()
                            .withName(FIELD_TITLE)
                            .withDataType(DataType.VarChar)
                            .withMaxLength(256)
                            .build())
                    .addFieldType(FieldType.newBuilder()
                            .withName(FIELD_CONTENT)
                            .withDataType(DataType.VarChar)
                            .withMaxLength(2048)
                            .build())
                    .build());
            if (createResp.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("创建集合失败: " + createResp.getMessage());
            }
            log.info("Milvus 集合创建成功: {}, dimension={}", collection, dimension);
        }

        try {
            // AUTOINDEX 让 Milvus 自选索引类型，演示环境免调参；COSINE 匹配向量归一化场景
            R<?> indexResp = milvusClient.createIndex(CreateIndexParam.newBuilder()
                    .withCollectionName(collection)
                    .withFieldName(FIELD_VECTOR)
                    .withIndexType(IndexType.AUTOINDEX)
                    .withMetricType(MetricType.COSINE)
                    .withSyncMode(Boolean.TRUE)
                    .build());
            if (indexResp.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("创建索引失败: " + indexResp.getMessage());
            }
        } catch (Exception e) {
            // 索引已存在时 Milvus 会报错，忽略即可
            log.info("Milvus 索引创建跳过（可能已存在）: {}", e.getMessage());
        }

        R<?> loadResp = milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(collection)
                .build());
        if (loadResp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("加载集合失败: " + loadResp.getMessage());
        }
        log.info("Milvus 集合加载完成: {}", collection);
    }
}
