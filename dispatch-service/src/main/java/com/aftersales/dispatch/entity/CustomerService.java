package com.aftersales.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客服信息表实体
 */
@Data
@TableName("customer_service")
public class CustomerService {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号 */
    private String account;

    /** 姓名 */
    private String name;

    /** 密码（演示项目明文，生产需加密） */
    private String password;

    /** 技能标签，逗号分隔，如：退货,退款,物流 */
    private String skillTags;

    /** 最大同时在办工单数 */
    private Integer maxLoad;

    /** 在线状态：ONLINE/OFFLINE */
    private String onlineStatus;

    /** 历史解决率（派单加权因子） */
    private BigDecimal resolveRate;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
