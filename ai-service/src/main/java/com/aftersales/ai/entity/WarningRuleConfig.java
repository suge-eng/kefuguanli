package com.aftersales.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预警规则配置表实体
 * 设计思路：预警规则高频调整，做成配置表可热生效不用发版；
 * conditionConfig 用 JSON 承载各类型的不同参数（关键词数组/时间窗次数/金额阈值），
 * 加规则类型不用改表结构
 */
@Data
@TableName("warning_rule_config")
public class WarningRuleConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则编码（唯一），如 KEYWORD_COMPLAINT */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 规则类型：KEYWORD 关键词 / REPEAT 重复诉求 / TIMEOUT 超时未处理 / HIGH_VALUE 高金额 / SENTIMENT 投诉情绪 */
    private String ruleType;

    /** 规则参数（JSON 字符串），如 {"keywords":["投诉","曝光"]}、{"hours":24,"times":3}、{"amountThreshold":500}、{"timeoutMinutes":30} */
    private String conditionConfig;

    /** 命中动作：NOTIFY 仅发预警事件 / ESCALATE 发事件并升级工单 */
    private String action;

    /** 是否启用：0/1 */
    private Integer enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
