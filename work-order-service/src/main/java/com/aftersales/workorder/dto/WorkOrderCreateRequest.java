package com.aftersales.workorder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 工单创建请求
 */
@Data
public class WorkOrderCreateRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "用户昵称不能为空")
    private String userName;

    /** 来源渠道：WEB/API/PLATFORM，默认 WEB */
    private String sourceChannel = "WEB";

    @NotBlank(message = "诉求内容不能为空")
    @Size(max = 2000, message = "诉求内容不能超过2000字")
    private String content;

    /** 附件对象名列表（MinIO），可选 */
    private List<String> attachments;

    /** 涉及的电商平台订单号，可选 */
    private String relatedOrderId;
}
