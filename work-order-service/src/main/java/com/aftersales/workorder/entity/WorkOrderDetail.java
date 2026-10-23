package com.aftersales.workorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单详情表实体
 * MySQL 的 JSON 列直接用 String 接收（驱动自动转换），
 * 序列化/反序列化由业务层用 Jackson 处理，避免引入类型处理器增加复杂度
 */
@Data
@TableName("work_order_detail")
public class WorkOrderDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workOrderId;

    /** 用户原始诉求文本 */
    private String content;

    /** AI结构化提取结果（JSON字符串） */
    private String structuredFields;

    /** 附件地址数组（JSON字符串） */
    private String attachments;

    /** 涉及的电商平台订单号 */
    private String relatedOrderId;

    /** AI生成的回复草稿 */
    private String aiDraftReply;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
