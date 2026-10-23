package com.aftersales.workorder.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 客服领取工单请求
 */
@Data
public class ClaimRequest {

    /** 领取人客服ID（必须是工单当前处理人，防止抢单） */
    @NotNull(message = "客服ID不能为空")
    private Long handlerId;
}
