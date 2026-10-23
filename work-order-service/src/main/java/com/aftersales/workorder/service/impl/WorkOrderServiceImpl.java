package com.aftersales.workorder.service.impl;

import java.util.UUID;
import com.aftersales.common.enums.OperatorTypeEnum;
import com.aftersales.common.enums.WorkOrderStatusEnum;
import com.aftersales.common.event.WorkOrderEvent;
import com.aftersales.common.exception.BizException;
import com.aftersales.common.mq.MqConstants;
import com.aftersales.common.result.PageResult;
import com.aftersales.workorder.dto.AiDraftRequest;
import com.aftersales.workorder.dto.AttachmentAppendRequest;
import com.aftersales.workorder.dto.DispatchRequest;
import com.aftersales.workorder.dto.ReplyRequest;
import com.aftersales.workorder.dto.StructureRequest;
import com.aftersales.workorder.dto.TransferRequest;
import com.aftersales.workorder.dto.WorkOrderCreateRequest;
import com.aftersales.workorder.entity.WorkOrder;
import com.aftersales.workorder.entity.WorkOrderDetail;
import com.aftersales.workorder.entity.WorkOrderLog;
import com.aftersales.workorder.mapper.WorkOrderDetailMapper;
import com.aftersales.workorder.mapper.WorkOrderLogMapper;
import com.aftersales.workorder.mapper.WorkOrderMapper;
import com.aftersales.workorder.mq.WorkOrderEventProducer;
import com.aftersales.workorder.service.WorkOrderService;
import com.aftersales.workorder.statemachine.WorkOrderStateMachine;
import com.aftersales.workorder.vo.WorkOrderDetailVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 工单中心核心服务
 * 关键设计：MQ 事件统一在「事务提交后」发送（afterCommit），
 * 防止消息已被下游消费、本地事务却回滚导致的脏数据/脏消息问题
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderServiceImpl implements WorkOrderService {

    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderDetailMapper workOrderDetailMapper;
    private final WorkOrderLogMapper workOrderLogMapper;
    private final WorkOrderStateMachine stateMachine;
    private final WorkOrderEventProducer eventProducer;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkOrder create(WorkOrderCreateRequest request) {
        // 1. 组装并插入工单主表
        WorkOrder workOrder = new WorkOrder();
        workOrder.setOrderNo(generateOrderNo());
        workOrder.setUserId(request.getUserId());
        workOrder.setUserName(request.getUserName());
        workOrder.setSourceChannel(request.getSourceChannel());
        workOrder.setPriority("NORMAL");
        workOrder.setStatus(WorkOrderStatusEnum.PENDING_STRUCTURE.name());
        workOrderMapper.insert(workOrder);

        // 2. 插入工单详情（附件列表转 JSON 存储）
        WorkOrderDetail detail = new WorkOrderDetail();
        detail.setWorkOrderId(workOrder.getId());
        detail.setContent(request.getContent());
        detail.setRelatedOrderId(request.getRelatedOrderId());
        detail.setAttachments(toJsonQuietly(request.getAttachments()));
        workOrderDetailMapper.insert(detail);

        // 3. 操作日志留痕
        saveLog(workOrder.getId(), String.valueOf(request.getUserId()), OperatorTypeEnum.USER.name(),
                "CREATE", null, workOrder.getStatus(), "用户提交工单");

        // 4. 事务提交后发送「工单创建事件」，触发下游结构化流程
        WorkOrderEvent event = buildEvent("CREATE", workOrder, null);
        registerAfterCommitSend(MqConstants.TOPIC_WORK_ORDER_CREATE, event);

        return workOrder;
    }

    @Override
    public PageResult<WorkOrder> pageQuery(Long pageNum, Long pageSize, String status, String workType,
                                           Long handlerId, Long userId) {
        Page<WorkOrder> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<WorkOrder>()
                .eq(StringUtils.hasText(status), WorkOrder::getStatus, status)
                .eq(StringUtils.hasText(workType), WorkOrder::getWorkType, workType)
                .eq(handlerId != null, WorkOrder::getHandlerId, handlerId)
                .eq(userId != null, WorkOrder::getUserId, userId)
                .orderByDesc(WorkOrder::getCreateTime);
        Page<WorkOrder> result = workOrderMapper.selectPage(page, wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    @Override
    public WorkOrderDetailVO getDetail(Long id) {
        WorkOrder workOrder = getWorkOrderOrThrow(id);
        WorkOrderDetail detail = workOrderDetailMapper.selectOne(
                new LambdaQueryWrapper<WorkOrderDetail>().eq(WorkOrderDetail::getWorkOrderId, id));
        List<WorkOrderLog> logs = workOrderLogMapper.selectList(
                new LambdaQueryWrapper<WorkOrderLog>()
                        .eq(WorkOrderLog::getWorkOrderId, id)
                        .orderByAsc(WorkOrderLog::getCreateTime));
        return new WorkOrderDetailVO(workOrder, detail, logs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String targetStatus, String operatorId, String operatorType,
                             String action, String remark) {
        WorkOrder workOrder = getWorkOrderOrThrow(id);
        String beforeStatus = workOrder.getStatus();
        // 状态机校验，非法流转直接抛异常
        stateMachine.checkTransition(beforeStatus, targetStatus);

        workOrder.setStatus(targetStatus);
        workOrderMapper.updateById(workOrder);

        saveLog(id, operatorId, operatorType, action, beforeStatus, targetStatus, remark);

        // 事务提交后广播状态变更事件（统计分析服务消费）
        WorkOrderEvent event = buildEvent("STATUS_CHANGE", workOrder,
                buildHandlerPayload(workOrder, beforeStatus, targetStatus, action, null, null));
        registerAfterCommitSend(MqConstants.TOPIC_WORK_ORDER_STATUS, event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void mockStructured(Long workOrderId) {
        WorkOrder workOrder = getWorkOrderOrThrow(workOrderId);
        // 幂等保护：只有「待结构化」状态才执行，重复消费直接跳过
        if (!WorkOrderStatusEnum.PENDING_STRUCTURE.name().equals(workOrder.getStatus())) {
            log.warn("工单{}当前状态为{}，跳过模拟结构化（幂等）", workOrderId, workOrder.getStatus());
            return;
        }

        // 模拟 AI 结构化结果回填
        WorkOrderDetail detail = workOrderDetailMapper.selectOne(
                new LambdaQueryWrapper<WorkOrderDetail>().eq(WorkOrderDetail::getWorkOrderId, workOrderId));
        detail.setStructuredFields(toJsonQuietly(Map.of(
                "workType", "OTHER",
                "appealLevel", "NORMAL",
                "complaintIntent", false,
                "mock", true)));
        workOrderDetailMapper.updateById(detail);

        workOrder.setWorkType("OTHER");
        workOrder.setTitle("模拟结构化标题");
        String beforeStatus = workOrder.getStatus();
        workOrder.setStatus(WorkOrderStatusEnum.PENDING_DISPATCH.name());
        workOrderMapper.updateById(workOrder);

        saveLog(workOrderId, "mock-agent", OperatorTypeEnum.SYSTEM.name(),
                "STRUCTURE", beforeStatus, workOrder.getStatus(), "模拟结构化完成（第一阶段占位，第三阶段由AI替代）");

        WorkOrderEvent event = buildEvent("STRUCTURED", workOrder, null);
        registerAfterCommitSend(MqConstants.TOPIC_WORK_ORDER_STRUCTURED, event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dispatch(Long id, DispatchRequest request) {
        WorkOrder workOrder = getWorkOrderOrThrow(id);
        String beforeStatus = workOrder.getStatus();
        Long beforeHandlerId = workOrder.getHandlerId();
        String action;

        if (WorkOrderStatusEnum.PENDING_DISPATCH.name().equals(beforeStatus)) {
            // 首派：状态机校验后推进到「待处理」
            stateMachine.checkTransition(beforeStatus, WorkOrderStatusEnum.PENDING_HANDLE.name());
            workOrder.setStatus(WorkOrderStatusEnum.PENDING_HANDLE.name());
            action = "DISPATCH";
        } else if (WorkOrderStatusEnum.PENDING_HANDLE.name().equals(beforeStatus)) {
            // 超时转派：状态不变，仅更换处理人
            action = "RE_DISPATCH";
        } else {
            throw new BizException(String.format("工单当前状态[%s]不允许分派", beforeStatus));
        }

        workOrder.setHandlerId(request.getHandlerId());
        workOrder.setHandlerName(request.getHandlerName());
        // 列级定向更新：只写 status/handler 三列，不整行回写——
        // 派单与 AI 预警升级（escalate 置 priority=URGENT）消费同一事件并发写同一行，
        // 整行 updateById 会用派单前读到的旧 priority 覆盖掉刚升级的 URGENT（丢失更新）
        workOrderMapper.update(null, new LambdaUpdateWrapper<WorkOrder>()
                .eq(WorkOrder::getId, id)
                .set(WorkOrder::getStatus, workOrder.getStatus())
                .set(WorkOrder::getHandlerId, request.getHandlerId())
                .set(WorkOrder::getHandlerName, request.getHandlerName()));

        saveLog(id, "dispatch-service", OperatorTypeEnum.SYSTEM.name(), action,
                beforeStatus, workOrder.getStatus(),
                "DISPATCH".equals(action)
                        ? String.format("派单给客服[%s(%d)]", request.getHandlerName(), request.getHandlerId())
                        : String.format("超时未接单，从客服[%d]转派给客服[%s(%d)]",
                                beforeHandlerId, request.getHandlerName(), request.getHandlerId()));

        // 负载增减所需的处理人信息放进事件载荷，派单服务消费后统一调整 Redis 负载
        WorkOrderEvent event = buildEvent("STATUS_CHANGE", workOrder,
                buildHandlerPayload(workOrder, beforeStatus, workOrder.getStatus(), action,
                        request.getHandlerId(), beforeHandlerId));
        registerAfterCommitSend(MqConstants.TOPIC_WORK_ORDER_STATUS, event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void claim(Long id, Long handlerId) {
        WorkOrder workOrder = getWorkOrderOrThrow(id);
        checkCurrentHandler(workOrder, handlerId);
        String beforeStatus = workOrder.getStatus();
        stateMachine.checkTransition(beforeStatus, WorkOrderStatusEnum.PROCESSING.name());

        workOrder.setStatus(WorkOrderStatusEnum.PROCESSING.name());
        workOrderMapper.updateById(workOrder);

        saveLog(id, String.valueOf(handlerId), OperatorTypeEnum.CS.name(),
                "CLAIM", beforeStatus, workOrder.getStatus(), "客服领取工单，开始处理");

        WorkOrderEvent event = buildEvent("STATUS_CHANGE", workOrder,
                buildHandlerPayload(workOrder, beforeStatus, workOrder.getStatus(), "CLAIM", handlerId, null));
        registerAfterCommitSend(MqConstants.TOPIC_WORK_ORDER_STATUS, event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reply(Long id, ReplyRequest request) {
        WorkOrder workOrder = getWorkOrderOrThrow(id);
        checkCurrentHandler(workOrder, request.getHandlerId());
        if (!WorkOrderStatusEnum.PROCESSING.name().equals(workOrder.getStatus())) {
            throw new BizException("只有「处理中」的工单才能回复");
        }
        // 回复不改变状态，只留痕；详情接口随日志列表返回给前端展示
        saveLog(id, String.valueOf(request.getHandlerId()), OperatorTypeEnum.CS.name(),
                "REPLY", workOrder.getStatus(), workOrder.getStatus(), request.getContent());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolve(Long id, Long handlerId) {
        WorkOrder workOrder = getWorkOrderOrThrow(id);
        checkCurrentHandler(workOrder, handlerId);
        String beforeStatus = workOrder.getStatus();
        stateMachine.checkTransition(beforeStatus, WorkOrderStatusEnum.RESOLVED.name());

        workOrder.setStatus(WorkOrderStatusEnum.RESOLVED.name());
        workOrderMapper.updateById(workOrder);

        saveLog(id, String.valueOf(handlerId), OperatorTypeEnum.CS.name(),
                "RESOLVE", beforeStatus, workOrder.getStatus(), "客服标记工单已解决");

        WorkOrderEvent event = buildEvent("STATUS_CHANGE", workOrder,
                buildHandlerPayload(workOrder, beforeStatus, workOrder.getStatus(), "RESOLVE", handlerId, null));
        registerAfterCommitSend(MqConstants.TOPIC_WORK_ORDER_STATUS, event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long id, TransferRequest request) {
        WorkOrder workOrder = getWorkOrderOrThrow(id);
        checkCurrentHandler(workOrder, request.getOperatorId());
        // 只有待处理/处理中能转交（其他状态下工单不属于任何客服的待办）
        String status = workOrder.getStatus();
        if (!WorkOrderStatusEnum.PENDING_HANDLE.name().equals(status)
                && !WorkOrderStatusEnum.PROCESSING.name().equals(status)) {
            throw new BizException(String.format("工单当前状态[%s]不允许转交", status));
        }
        Long beforeHandlerId = workOrder.getHandlerId();
        workOrder.setHandlerId(request.getNewHandlerId());
        workOrder.setHandlerName(request.getNewHandlerName());
        workOrderMapper.updateById(workOrder);

        saveLog(id, String.valueOf(request.getOperatorId()), OperatorTypeEnum.CS.name(),
                "TRANSFER", status, status,
                String.format("转交给客服[%s(%d)]，原因：%s",
                        request.getNewHandlerName(), request.getNewHandlerId(),
                        StringUtils.hasText(request.getReason()) ? request.getReason() : "无"));

        WorkOrderEvent event = buildEvent("STATUS_CHANGE", workOrder,
                buildHandlerPayload(workOrder, status, status, "TRANSFER",
                        request.getNewHandlerId(), beforeHandlerId));
        registerAfterCommitSend(MqConstants.TOPIC_WORK_ORDER_STATUS, event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, String operatorId, String reason) {
        WorkOrder workOrder = getWorkOrderOrThrow(id);
        String beforeStatus = workOrder.getStatus();
        stateMachine.checkTransition(beforeStatus, WorkOrderStatusEnum.CLOSED.name());

        workOrder.setStatus(WorkOrderStatusEnum.CLOSED.name());
        workOrderMapper.updateById(workOrder);

        saveLog(id, operatorId, OperatorTypeEnum.SYSTEM.name(),
                "CLOSE", beforeStatus, workOrder.getStatus(), "关闭工单：" + reason);

        // 关闭时若工单还挂在某个客服名下，事件携带 handlerId 供派单服务释放其负载
        WorkOrderEvent event = buildEvent("STATUS_CHANGE", workOrder,
                buildHandlerPayload(workOrder, beforeStatus, workOrder.getStatus(), "CLOSE",
                        workOrder.getHandlerId(), null));
        registerAfterCommitSend(MqConstants.TOPIC_WORK_ORDER_STATUS, event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void appendAttachments(Long id, AttachmentAppendRequest request) {
        WorkOrder workOrder = getWorkOrderOrThrow(id);
        WorkOrderDetail detail = workOrderDetailMapper.selectOne(
                new LambdaQueryWrapper<WorkOrderDetail>().eq(WorkOrderDetail::getWorkOrderId, id));

        // 解析既有附件数组，追加新对象名（无则新建）
        List<String> attachments = parseAttachments(detail.getAttachments());
        attachments.addAll(request.getObjectNames());
        detail.setAttachments(toJsonQuietly(attachments));
        workOrderDetailMapper.updateById(detail);

        saveLog(id, StringUtils.hasText(request.getOperatorId()) ? request.getOperatorId() : "unknown",
                OperatorTypeEnum.CS.name(), "UPLOAD_ATTACHMENT",
                workOrder.getStatus(), workOrder.getStatus(),
                "上传附件：" + String.join(",", request.getObjectNames()));
    }

    // ==================== 第三阶段：AI Agent 回写接口 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyStructured(Long id, StructureRequest request) {
        WorkOrder workOrder = getWorkOrderOrThrow(id);
        // 幂等保护：只有「待结构化」状态才执行，MQ 重试/重复消费直接跳过
        if (!WorkOrderStatusEnum.PENDING_STRUCTURE.name().equals(workOrder.getStatus())) {
            log.warn("工单{}当前状态为{}，跳过AI结构化回写（幂等）", id, workOrder.getStatus());
            return;
        }

        // 1. 回填结构化字段（原始 JSON 整体落库，预警/统计直接消费）
        WorkOrderDetail detail = workOrderDetailMapper.selectOne(
                new LambdaQueryWrapper<WorkOrderDetail>().eq(WorkOrderDetail::getWorkOrderId, id));
        detail.setStructuredFields(request.getRawJson());
        if (StringUtils.hasText(request.getRelatedOrderId())) {
            detail.setRelatedOrderId(request.getRelatedOrderId());
        }
        workOrderDetailMapper.updateById(detail);

        // 2. 主表列级定向更新类型/标题/状态（不用 updateById 整行覆盖：
        //    结构化与预警Agent消费同一事件并发写同一行，整行覆盖会用读到的旧值
        //    把对方刚写入的 priority 等字段冲掉，造成丢失更新）
        String beforeStatus = workOrder.getStatus();
        String newStatus = WorkOrderStatusEnum.PENDING_DISPATCH.name();
        workOrderMapper.update(null, new LambdaUpdateWrapper<WorkOrder>()
                .eq(WorkOrder::getId, id)
                .set(WorkOrder::getWorkType, StringUtils.hasText(request.getWorkType()) ? request.getWorkType() : "OTHER")
                .set(WorkOrder::getTitle, StringUtils.hasText(request.getTitle()) ? request.getTitle() : "用户售后诉求")
                .set(WorkOrder::getStatus, newStatus));
        workOrder.setWorkType(StringUtils.hasText(request.getWorkType()) ? request.getWorkType() : "OTHER");
        workOrder.setTitle(StringUtils.hasText(request.getTitle()) ? request.getTitle() : "用户售后诉求");
        workOrder.setStatus(newStatus);

        saveLog(id, "ai-structure-agent", OperatorTypeEnum.SYSTEM.name(),
                "STRUCTURE", beforeStatus, workOrder.getStatus(),
                String.format("AI结构化完成：类型=%s，诉求等级=%s，投诉意向=%s",
                        workOrder.getWorkType(), request.getAppealLevel(), request.getComplaintIntent()));

        // 3. 事务提交后发送「结构化完成事件」，触发派单/预警
        //    载荷携带类型/优先级：统计服务按类型维度累加，无需反查工单库
        WorkOrderEvent event = buildEvent("STRUCTURED", workOrder,
                Map.of("workType", workOrder.getWorkType(),
                        "priority", workOrder.getPriority() == null ? "NORMAL" : workOrder.getPriority()));
        registerAfterCommitSend(MqConstants.TOPIC_WORK_ORDER_STRUCTURED, event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAiDraft(Long id, AiDraftRequest request) {
        WorkOrder workOrder = getWorkOrderOrThrow(id);
        WorkOrderDetail detail = workOrderDetailMapper.selectOne(
                new LambdaQueryWrapper<WorkOrderDetail>().eq(WorkOrderDetail::getWorkOrderId, id));
        detail.setAiDraftReply(request.getDraft());
        workOrderDetailMapper.updateById(detail);

        // 草稿只是参考，不改变状态；留痕记录引用的知识条目，便于客服溯源审核
        saveLog(id, "ai-reply-agent", OperatorTypeEnum.SYSTEM.name(), "AI_DRAFT",
                workOrder.getStatus(), workOrder.getStatus(),
                "AI生成回复草稿" + (StringUtils.hasText(request.getCitedKnowledge())
                        ? "，引用知识库：" + request.getCitedKnowledge() : ""));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void escalate(Long id, String operatorId, String reason) {
        WorkOrder workOrder = getWorkOrderOrThrow(id);
        // 已终态的工单不再升级（预警迟到无意义）
        if (WorkOrderStatusEnum.RESOLVED.name().equals(workOrder.getStatus())
                || WorkOrderStatusEnum.CLOSED.name().equals(workOrder.getStatus())) {
            log.info("工单{}已终态（{}），跳过升级", id, workOrder.getStatus());
            return;
        }
        // 只升不降：已是 URGENT 不重复处理（幂等）
        if ("URGENT".equals(workOrder.getPriority())) {
            log.info("工单{}已是最高优先级，跳过升级", id);
            return;
        }
        // 列级定向更新 priority：预警与结构化/派单并发写同一行，
        // 整行覆盖会把对方刚写入的 status/handler 用旧值冲掉（丢失更新）
        workOrderMapper.update(null, new LambdaUpdateWrapper<WorkOrder>()
                .eq(WorkOrder::getId, id)
                .set(WorkOrder::getPriority, "URGENT"));
        // 同步内存对象，保证下游事件的 priority 载荷是升级后的值（不再参与持久化）
        workOrder.setPriority("URGENT");

        saveLog(id, operatorId, OperatorTypeEnum.SYSTEM.name(), "WARNING",
                workOrder.getStatus(), workOrder.getStatus(), "工单升级为URGENT：" + reason);

        // 广播状态事件（action=WARNING）：派单负载消费者忽略未知动作，统计服务用于累加升级量
        WorkOrderEvent event = buildEvent("STATUS_CHANGE", workOrder,
                buildHandlerPayload(workOrder, workOrder.getStatus(), workOrder.getStatus(), "WARNING", null, null));
        registerAfterCommitSend(MqConstants.TOPIC_WORK_ORDER_STATUS, event);
    }

    // ==================== 私有工具方法 ====================

    /** 校验操作人是否为工单当前处理人（防止越权操作他人工单） */
    private void checkCurrentHandler(WorkOrder workOrder, Long handlerId) {
        if (workOrder.getHandlerId() == null || !workOrder.getHandlerId().equals(handlerId)) {
            throw new BizException("只能操作分配给自己的工单");
        }
    }

    /**
     * 状态变更事件载荷：携带动作、处理人变更以及工单类型/优先级
     * 设计思路：workType/priority 冗余进载荷，统计服务按类型维度聚合时无需回查工单库，
     * 保持「事件即事实」的消费模式
     */
    private Map<String, Object> buildHandlerPayload(WorkOrder workOrder, String beforeStatus, String afterStatus,
                                                    String action, Long handlerId, Long beforeHandlerId) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("beforeStatus", beforeStatus);
        payload.put("afterStatus", afterStatus);
        payload.put("action", action);
        if (workOrder.getWorkType() != null) {
            payload.put("workType", workOrder.getWorkType());
        }
        if (workOrder.getPriority() != null) {
            payload.put("priority", workOrder.getPriority());
        }
        if (handlerId != null) {
            payload.put("handlerId", handlerId);
        }
        if (beforeHandlerId != null) {
            payload.put("beforeHandlerId", beforeHandlerId);
        }
        return payload;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseAttachments(String json) {
        if (!StringUtils.hasText(json)) {
            return new java.util.ArrayList<>();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            log.warn("附件JSON解析失败，按空列表处理: {}", e.getMessage());
            return new java.util.ArrayList<>();
        }
    }

    private WorkOrder getWorkOrderOrThrow(Long id) {
        WorkOrder workOrder = workOrderMapper.selectById(id);
        if (workOrder == null) {
            throw new BizException("工单不存在: " + id);
        }
        return workOrder;
    }

    /** 工单编号：WO + 时间戳 + 4位随机数，可读且基本不重复 */
    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "WO" + timestamp + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    private void saveLog(Long workOrderId, String operatorId, String operatorType, String action,
                         String beforeStatus, String afterStatus, String content) {
        WorkOrderLog orderLog = new WorkOrderLog();
        orderLog.setWorkOrderId(workOrderId);
        orderLog.setOperatorId(operatorId);
        orderLog.setOperatorType(operatorType);
        orderLog.setAction(action);
        orderLog.setBeforeStatus(beforeStatus);
        orderLog.setAfterStatus(afterStatus);
        orderLog.setContent(content);
        workOrderLogMapper.insert(orderLog);
    }

    private WorkOrderEvent buildEvent(String eventType, WorkOrder workOrder, Object payload) {
        return WorkOrderEvent.builder()
                .eventId(UUID.randomUUID().toString().replace("-", ""))
                .eventType(eventType)
                .workOrderId(workOrder.getId())
                .orderNo(workOrder.getOrderNo())
                .payload(payload)
                .occurTime(LocalDateTime.now())
                .build();
    }

    /**
     * 注册事务提交后的消息发送动作；
     * 若当前没有事务（理论上不会走到），则直接发送兜底
     */
    private void registerAfterCommitSend(String topic, WorkOrderEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventProducer.sendEvent(topic, event);
                }
            });
        } else {
            eventProducer.sendEvent(topic, event);
        }
    }

    private String toJsonQuietly(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON序列化失败: {}", e.getMessage());
            return null;
        }
    }
}
