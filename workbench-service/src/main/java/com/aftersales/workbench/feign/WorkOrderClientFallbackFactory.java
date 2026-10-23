package com.aftersales.workbench.feign;

import com.aftersales.common.result.PageResult;
import com.aftersales.common.result.Result;
import com.aftersales.workbench.feign.dto.AttachmentAppendRequestDto;
import com.aftersales.workbench.feign.dto.HandlerRequestDto;
import com.aftersales.workbench.feign.dto.ReplyRequestDto;
import com.aftersales.workbench.feign.dto.TransferRequestDto;
import com.aftersales.workbench.feign.dto.WorkOrderDetailVOView;
import com.aftersales.workbench.feign.dto.WorkOrderView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 工单中心 Feign 降级工厂
 * 设计思路：工作台是人工操作入口，降级时返回明确的业务提示（而不是卡住或抛 500 堆栈），
 * 客服能立刻知道「工单中心暂时不可用」并重试，体验优于静默失败
 */
@Slf4j
@Component
public class WorkOrderClientFallbackFactory implements FallbackFactory<WorkOrderClient> {

    @Override
    public WorkOrderClient create(Throwable cause) {
        return new WorkOrderClient() {

            @Override
            public Result<PageResult<WorkOrderView>> page(Long pageNum, Long pageSize, String status, Long handlerId) {
                return fail("查询我的工单", cause);
            }

            @Override
            public Result<WorkOrderDetailVOView> detail(Long id) {
                return fail("查询工单详情", cause);
            }

            @Override
            public Result<Void> claim(Long id, HandlerRequestDto request) {
                return fail("领取工单", cause);
            }

            @Override
            public Result<Void> reply(Long id, ReplyRequestDto request) {
                return fail("回复工单", cause);
            }

            @Override
            public Result<Void> resolve(Long id, HandlerRequestDto request) {
                return fail("标记解决", cause);
            }

            @Override
            public Result<Void> transfer(Long id, TransferRequestDto request) {
                return fail("转交工单", cause);
            }

            @Override
            public Result<Void> close(Long id, String operatorId, String reason) {
                return fail("关闭工单", cause);
            }

            @Override
            public Result<Void> appendAttachments(Long id, AttachmentAppendRequestDto request) {
                return fail("追加工单附件", cause);
            }
        };
    }

    private <T> Result<T> fail(String action, Throwable cause) {
        log.warn("工单中心调用被熔断降级: action={}, err={}", action, cause.getMessage());
        return Result.fail("工单中心暂不可用（" + action + "），请稍后重试");
    }
}
