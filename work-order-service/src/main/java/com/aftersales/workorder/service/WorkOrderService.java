package com.aftersales.workorder.service;

import com.aftersales.common.result.PageResult;
import com.aftersales.workorder.dto.AiDraftRequest;
import com.aftersales.workorder.dto.AttachmentAppendRequest;
import com.aftersales.workorder.dto.DispatchRequest;
import com.aftersales.workorder.dto.ReplyRequest;
import com.aftersales.workorder.dto.StructureRequest;
import com.aftersales.workorder.dto.TransferRequest;
import com.aftersales.workorder.dto.WorkOrderCreateRequest;
import com.aftersales.workorder.entity.WorkOrder;
import com.aftersales.workorder.vo.WorkOrderDetailVO;

/**
 * 工单中心服务接口
 */
public interface WorkOrderService {

    /**
     * 创建工单：落库（主表+详情+日志）后，事务提交再发「工单创建事件」
     *
     * @return 创建成功的工单
     */
    WorkOrder create(WorkOrderCreateRequest request);

    /**
     * 分页查询工单列表（按创建时间倒序）
     *
     * @param handlerId 处理人ID（工作台「我的工单」用，可空）
     * @param userId    提交人ID（预警Agent「同一用户重复提交」统计用，可空）
     */
    PageResult<WorkOrder> pageQuery(Long pageNum, Long pageSize, String status, String workType,
                                    Long handlerId, Long userId);

    /**
     * 查询工单详情（主表+详情+操作日志）
     */
    WorkOrderDetailVO getDetail(Long id);

    /**
     * 变更工单状态（走状态机校验 + 留痕 + 发状态变更事件）
     */
    void changeStatus(Long id, String targetStatus, String operatorId, String operatorType,
                      String action, String remark);

    /**
     * 第一阶段模拟 AI 结构化：消费创建事件后回填结构化字段，
     * 状态推进到「待分派」并发送「结构化完成事件」
     */
    void mockStructured(Long workOrderId);

    /**
     * 分派回写（派单服务 Feign 调用）：
     * 首派 PENDING_DISPATCH -> PENDING_HANDLE；
     * 超时转派 PENDING_HANDLE -> PENDING_HANDLE（仅换处理人，动作记 RE_DISPATCH）
     */
    void dispatch(Long id, DispatchRequest request);

    /**
     * 客服领取：PENDING_HANDLE -> PROCESSING，只能领取分配给自己的工单
     */
    void claim(Long id, Long handlerId);

    /**
     * 客服回复：处理中状态追加回复日志，不改变工单状态
     */
    void reply(Long id, ReplyRequest request);

    /**
     * 客服解决：PROCESSING -> RESOLVED
     */
    void resolve(Long id, Long handlerId);

    /**
     * 工单转交：更换处理人，状态不变
     */
    void transfer(Long id, TransferRequest request);

    /**
     * 关闭工单：按状态机允许的入口状态 -> CLOSED
     */
    void close(Long id, String operatorId, String reason);

    /**
     * 追加工单附件（MinIO 对象名）
     */
    void appendAttachments(Long id, AttachmentAppendRequest request);

    /**
     * AI 结构化结果回写（ai-service 结构化 Agent Feign 调用）：
     * 回填结构化字段 -> 状态推进到「待分派」-> 发送「结构化完成事件」；
     * 与 mockStructured 同一状态机入口，仅「待结构化」状态可执行（幂等）
     */
    void applyStructured(Long id, StructureRequest request);

    /**
     * AI 回复草稿回写：只更新 detail.ai_draft_reply 并留痕，不改变工单状态
     */
    void saveAiDraft(Long id, AiDraftRequest request);

    /**
     * 工单升级（预警 Agent 命中升级规则时调用）：
     * 优先级提升为 URGENT + 留痕 + 广播状态事件（action=WARNING），不改变工单状态
     */
    void escalate(Long id, String operatorId, String reason);
}
