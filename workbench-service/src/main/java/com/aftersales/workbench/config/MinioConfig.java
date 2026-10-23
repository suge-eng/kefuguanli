package com.aftersales.workbench.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 配置
 * 设计思路：附件桶启动时自动创建（bucketExists 不存在则 makeBucket），
 * 部署新环境不用手工建桶；初始化失败只告警不阻断启动——
 * MinIO 暂时不可用不应拖垮工作台，上传时才会真正依赖它
 */
@Slf4j
@Configuration
public class MinioConfig {

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Value("${minio.bucket}")
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /** 启动后确保附件桶存在 */
    @Bean
    public ApplicationRunner bucketInitializer(MinioClient minioClient) {
        return args -> {
            try {
                boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("MinIO 附件桶创建成功: {}", bucket);
                } else {
                    log.info("MinIO 附件桶已存在: {}", bucket);
                }
            } catch (Exception e) {
                log.warn("MinIO 附件桶初始化失败（不阻断启动，上传功能暂不可用）: {}", e.getMessage());
            }
        };
    }
}
