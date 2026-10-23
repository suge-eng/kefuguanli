package com.aftersales.workbench.service;

import com.aftersales.common.exception.BizException;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * 工单附件服务（MinIO 对象存储）
 * 设计思路：文件存对象存储、数据库只存对象名，分离存储降低数据库压力；
 * 对象名用 UUID 防重名和路径穿越，原始扩展名保留便于预览时识别文件类型
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    /**
     * 上传附件，返回 MinIO 对象名
     *
     * @param workOrderId 工单ID（对象名带前缀，便于按工单归档排查）
     */
    public String upload(Long workOrderId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件不能为空");
        }
        String originalFilename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex >= 0) {
            extension = originalFilename.substring(dotIndex);
        }
        String objectName = workOrderId + "/" + UUID.randomUUID().toString().replace("-", "") + extension;

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                    .build());
            log.info("附件上传成功: bucket={}, objectName={}", bucket, objectName);
            return objectName;
        } catch (Exception e) {
            log.error("附件上传失败: objectName={}", objectName, e);
            throw new BizException("附件上传失败: " + e.getMessage());
        }
    }
}
