package com.aftersales.workbench.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 附件追加请求体（Feign 调用工单中心 attachments 用），对应工单中心 AttachmentAppendRequest
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentAppendRequestDto {

    /** MinIO 对象名列表 */
    private List<String> objectNames;

    private String operatorId;
}
