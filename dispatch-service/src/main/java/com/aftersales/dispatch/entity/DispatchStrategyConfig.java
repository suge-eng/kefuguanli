package com.aftersales.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 派单策略配置表实体
 * 设计思路：派单规则高频迭代，策略做成配置表可热切换，
 * 改策略不用发版；同一时间只有一条 enabled=1
 */
@Data
@TableName("dispatch_strategy_config")
public class DispatchStrategyConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 策略编码：RANDOM/SKILL/LEAST_LOAD/WEIGHTED，与策略实现类的 Bean 名一一对应 */
    private String strategyCode;

    /** 策略名称 */
    private String strategyName;

    /** 加权策略权重配置（JSON 字符串），如 {"skill":0.4,"load":0.3,"resolveRate":0.2,"onlineHours":0.1} */
    private String weights;

    /** 是否启用：0/1 */
    private Integer enabled;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
