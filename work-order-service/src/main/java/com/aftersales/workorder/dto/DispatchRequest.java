package com.aftersales.workorder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 分派请求（派单服务 Feign 回写）
 * 设计思路：首派和超时转派共用同一入口，
 * 工单中心内部根据当前状态区分 DISPATCH / RE_DISPATCH 两种动作留痕
 */
@Data
public class DispatchRequest {

    /** 目标客服ID */
    @NotNull(message = "客服ID不能为空")
    private Long handlerId;

    /** 目标客服姓名（冗余到工单主表，列表查询不用联表） */
    @NotBlank(message = "客服姓名不能为空")
    private String handlerName;
}
