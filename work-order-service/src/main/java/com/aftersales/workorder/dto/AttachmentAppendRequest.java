package com.aftersales.workorder.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 追加工单附件请求
 * 设计思路：文件本体存 MinIO，工单只存对象名数组（JSON），
 * 追加式接口避免并发上传时互相覆盖
 */
@Data
public class AttachmentAppendRequest {

    /** MinIO 对象名列表 */
    @NotEmpty(message = "附件对象名不能为空")
    private List<String> objectNames;

    /** 操作人（留痕用，可空） */
    private String operatorId;
}
