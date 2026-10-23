-- ============================================================
-- 智能售后工单系统 - 数据库初始化脚本
-- 按服务拆库：work_order_db / dispatch_db / ai_db / stats_db
-- ============================================================

CREATE DATABASE IF NOT EXISTS work_order_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS dispatch_db   DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ai_db         DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS stats_db      DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ============================================================
-- 工单中心库
-- ============================================================
USE work_order_db;

-- 工单主表：只放核心流转字段，详情拆到 detail 表避免单表过宽
CREATE TABLE IF NOT EXISTS work_order (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '工单ID',
    order_no        VARCHAR(32)  NOT NULL COMMENT '工单编号（业务唯一标识，WO+时间+随机）',
    user_id         BIGINT       NOT NULL COMMENT '用户ID',
    user_name       VARCHAR(64)  NOT NULL COMMENT '用户昵称',
    source_channel  VARCHAR(16)  NOT NULL DEFAULT 'WEB' COMMENT '来源渠道：WEB/API/PLATFORM',
    work_type       VARCHAR(32)  DEFAULT NULL COMMENT '工单类型：REFUND退货退款/EXCHANGE换货/LOGISTICS物流/COMPLAINT投诉等（AI结构化后回填）',
    priority        VARCHAR(16)  NOT NULL DEFAULT 'NORMAL' COMMENT '优先级：LOW/NORMAL/HIGH/URGENT',
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING_STRUCTURE' COMMENT '状态：PENDING_STRUCTURE待结构化/PENDING_DISPATCH待分派/PENDING_HANDLE待处理/PROCESSING处理中/PENDING_CONFIRM待用户确认/RESOLVED已解决/CLOSED已关闭/ESCALATED已升级',
    handler_id      BIGINT       DEFAULT NULL COMMENT '当前处理客服ID',
    handler_name    VARCHAR(64)  DEFAULT NULL COMMENT '当前处理客服姓名',
    title           VARCHAR(128) DEFAULT NULL COMMENT '工单标题（AI提取后回填）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    -- 列表页高频查询：按类型+状态+时间倒序
    KEY idx_type_status_time (work_type, status, create_time),
    KEY idx_user_id (user_id),
    KEY idx_handler_status (handler_id, status)
) ENGINE = InnoDB COMMENT ='工单主表';

-- 工单详情表：非结构化内容 + AI 提取结果 + 附件
CREATE TABLE IF NOT EXISTS work_order_detail (
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    work_order_id      BIGINT       NOT NULL COMMENT '工单ID',
    content            TEXT         NOT NULL COMMENT '用户原始诉求文本',
    structured_fields  JSON         DEFAULT NULL COMMENT 'AI结构化提取结果（类型/诉求等级/订单号/品类/投诉意向等）',
    attachments        JSON         DEFAULT NULL COMMENT '附件地址数组（MinIO对象名）',
    related_order_id   VARCHAR(64)  DEFAULT NULL COMMENT '涉及的电商平台订单号',
    ai_draft_reply     TEXT         DEFAULT NULL COMMENT 'AI生成的回复草稿',
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_work_order_id (work_order_id)
) ENGINE = InnoDB COMMENT ='工单详情表';

-- 工单操作日志表：全链路留痕，满足审计
CREATE TABLE IF NOT EXISTS work_order_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    work_order_id   BIGINT       NOT NULL COMMENT '工单ID',
    operator_id     VARCHAR(64)  NOT NULL COMMENT '操作人ID（用户/客服/系统/AI）',
    operator_type   VARCHAR(16)  NOT NULL COMMENT '操作人类型：USER/CS/SYSTEM/AI',
    action          VARCHAR(32)  NOT NULL COMMENT '操作类型：CREATE/STRUCTURE/DISPATCH/CLAIM/REPLY/TRANSFER/CLOSE/ESCALATE等',
    before_status   VARCHAR(32)  DEFAULT NULL COMMENT '变更前状态',
    after_status    VARCHAR(32)  DEFAULT NULL COMMENT '变更后状态',
    content         TEXT         DEFAULT NULL COMMENT '变更内容说明',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_work_order_id (work_order_id)
) ENGINE = InnoDB COMMENT ='工单操作日志表';

-- ============================================================
-- 派单服务库
-- ============================================================
USE dispatch_db;

-- 客服信息表
CREATE TABLE IF NOT EXISTS customer_service (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '客服ID',
    account        VARCHAR(32)  NOT NULL COMMENT '登录账号',
    name           VARCHAR(64)  NOT NULL COMMENT '姓名',
    password       VARCHAR(64)  NOT NULL DEFAULT '123456' COMMENT '密码（演示项目明文，生产需加密）',
    skill_tags     VARCHAR(256) NOT NULL DEFAULT '' COMMENT '技能标签，逗号分隔，如：退货,退款,物流',
    max_load       INT          NOT NULL DEFAULT 10 COMMENT '最大同时在办工单数',
    online_status  VARCHAR(16)  NOT NULL DEFAULT 'OFFLINE' COMMENT '在线状态：ONLINE/OFFLINE',
    resolve_rate   DECIMAL(5,4) NOT NULL DEFAULT 0.8000 COMMENT '历史解决率（派单加权因子）',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_account (account)
) ENGINE = InnoDB COMMENT ='客服信息表';

-- 派单策略配置表：策略可配置、可热切换
CREATE TABLE IF NOT EXISTS dispatch_strategy_config (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    strategy_code VARCHAR(32)  NOT NULL COMMENT '策略编码：RANDOM/SKILL/LEAST_LOAD/WEIGHTED',
    strategy_name VARCHAR(64)  NOT NULL COMMENT '策略名称',
    weights       JSON         DEFAULT NULL COMMENT '加权策略权重配置：{"skill":0.4,"load":0.3,"resolveRate":0.2,"onlineHours":0.1}',
    enabled       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否启用（同一时间只有一个策略启用）',
    remark        VARCHAR(256) DEFAULT NULL COMMENT '备注',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_strategy_code (strategy_code)
) ENGINE = InnoDB COMMENT ='派单策略配置表';

-- 客服种子数据：覆盖不同技能方向，方便演示技能匹配派单
INSERT INTO customer_service (account, name, skill_tags, max_load, online_status, resolve_rate) VALUES
('cs001', '张晓', '退货,退款,售后政策', 10, 'ONLINE', 0.9200),
('cs002', '李强', '物流,催件,地址修改', 8,  'ONLINE', 0.8500),
('cs003', '王芳', '换货,发票,投诉', 12, 'ONLINE', 0.7800);

-- 派单策略种子数据：默认启用综合加权
INSERT INTO dispatch_strategy_config (strategy_code, strategy_name, weights, enabled, remark) VALUES
('RANDOM',      '随机派单',     NULL, 0, '兜底策略'),
('SKILL',       '技能匹配派单', NULL, 0, '优先匹配技能标签'),
('LEAST_LOAD',  '最小负载派单', NULL, 0, '优先分给当前负载最低的客服'),
('WEIGHTED',    '综合加权派单', JSON_OBJECT('skill', 0.4, 'load', 0.3, 'resolveRate', 0.2, 'onlineHours', 0.1), 1, '默认策略：技能40%+负载30%+解决率20%+在线时长10%');

-- ============================================================
-- AI 服务库
-- ============================================================
USE ai_db;

-- 售后知识库：RAG 召回语料
CREATE TABLE IF NOT EXISTS knowledge_base (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    category    VARCHAR(32)  NOT NULL COMMENT '分类：REFUND/EXCHANGE/LOGISTICS/INVOICE/POLICY',
    title       VARCHAR(128) NOT NULL COMMENT '标题',
    content     TEXT         NOT NULL COMMENT '知识内容（将向量化存入Milvus）',
    status      VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    vectorized  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已向量化：0否1是',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_category_status (category, status)
) ENGINE = InnoDB COMMENT ='售后知识库表';

-- 预警规则配置表
CREATE TABLE IF NOT EXISTS warning_rule_config (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    rule_code        VARCHAR(32)  NOT NULL COMMENT '规则编码',
    rule_name        VARCHAR(64)  NOT NULL COMMENT '规则名称',
    rule_type        VARCHAR(32)  NOT NULL COMMENT '规则类型：TIMEOUT超时/HIGH_VALUE高价值/REPEAT重复提交/KEYWORD关键词/SENTIMENT情绪(AI)',
    condition_config JSON         NOT NULL COMMENT '规则条件JSON，如{"timeoutMinutes":30}',
    action           VARCHAR(32)  NOT NULL DEFAULT 'WARN' COMMENT '命中动作：WARN预警/ESCALATE升级',
    enabled          TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rule_code (rule_code)
) ENGINE = InnoDB COMMENT ='预警规则配置表';

-- 知识库种子数据：售后高频场景，供 RAG 召回演示
INSERT INTO knowledge_base (category, title, content) VALUES
('REFUND', '七天无理由退货退款政策', '自用户签收商品之日起7天内，商品保持完好（包装、配件、吊牌齐全）可申请七天无理由退货。退款将在收到退回商品并验收通过后1-3个工作日内原路退回。定制类、生鲜类、虚拟商品不支持七天无理由退货。'),
('REFUND', '质量问题退货退款流程', '商品存在质量问题的，用户可随时申请退货退款，不受7天限制。请用户提供问题商品照片或视频作为凭证，审核通过后安排上门取件或用户自行寄回，运费由平台承担，退款1-3个工作日到账。'),
('EXCHANGE', '换货流程说明', '用户收到商品后如需换货（尺码、颜色等），可在签收后7天内申请。换货商品需保持完好。仓库收到退回商品后24小时内发出换货商品，换货运费：质量问题平台承担，个人原因用户承担。'),
('LOGISTICS', '物流超时未更新处理方案', '物流信息超过48小时未更新时，客服应主动联系快递公司核实包裹状态。如确认丢件，优先为用户补发商品或全额退款，并向快递公司发起理赔。处理时效不超过24小时。'),
('LOGISTICS', '催件标准话术与流程', '用户催件时，先查询物流最新节点，告知用户当前位置和预计送达时间。如已超承诺时效，为用户申请加急处理，并赠送优惠券作为补偿。'),
('INVOICE', '发票开具规则', '电子普通发票：订单完成后可在订单详情页自助申请，1-3个工作日开具并发送至邮箱。增值税专用发票：需提供企业资质，审核通过后7个工作日内开具寄出。'),
('POLICY', '投诉升级处理规范', '用户明确表示投诉意向（提及12315、曝光、律师等）时，工单立即升级为高危，30分钟内由主管介入处理，优先电话回访，避免舆情风险。'),
('POLICY', '高价值订单售后特别通道', '单笔订单金额超过5000元的售后申请，自动标记为高优先级，由资深客服专人跟进，处理时效不超过2小时。');

-- 预警规则种子数据
INSERT INTO warning_rule_config (rule_code, rule_name, rule_type, condition_config, action) VALUES
('TIMEOUT_UNHANDLED', '工单超时未处理', 'TIMEOUT',    JSON_OBJECT('timeoutMinutes', 30), 'ESCALATE'),
('HIGH_VALUE_ORDER',  '高价值订单',     'HIGH_VALUE', JSON_OBJECT('amountThreshold', 5000), 'WARN'),
('REPEAT_SUBMIT',     '同一用户重复提交', 'REPEAT',    JSON_OBJECT('hours', 24, 'times', 3), 'WARN'),
('COMPLAINT_KEYWORD', '投诉关键词命中',  'KEYWORD',    JSON_OBJECT('keywords', JSON_ARRAY('12315','投诉','曝光','消协','律师')), 'ESCALATE');

-- ============================================================
-- 统计分析库
-- ============================================================
USE stats_db;

-- 工单指标日汇总表：统计服务异步消费事件后累加
CREATE TABLE IF NOT EXISTS work_order_metrics_daily (
    id               BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    stat_date        DATE   NOT NULL COMMENT '统计日期',
    work_type        VARCHAR(32) NOT NULL DEFAULT 'ALL' COMMENT '工单类型，ALL表示全量',
    total_count      INT NOT NULL DEFAULT 0 COMMENT '工单总量',
    resolved_count   INT NOT NULL DEFAULT 0 COMMENT '已解决量',
    escalated_count  INT NOT NULL DEFAULT 0 COMMENT '升级量',
    avg_resolve_minutes INT NOT NULL DEFAULT 0 COMMENT '平均解决时长（分钟）',
    create_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_date_type (stat_date, work_type)
) ENGINE = InnoDB COMMENT ='工单指标日汇总表';
