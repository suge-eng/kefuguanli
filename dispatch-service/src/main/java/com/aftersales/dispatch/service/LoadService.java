package com.aftersales.dispatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 客服实时负载服务（Redis 计数）
 * 设计思路：派单计算是高频读，负载存 Redis 直接读缓存不查库；
 * 增减由 LoadAdjustConsumer 消费工单状态事件驱动，与业务操作解耦
 *
 * 注意：Redis 重启后负载清空，与数据库在办工单数会漂移；
 * 生产环境需服务启动时对账（扫描在办工单重建负载），演示项目从简
 */
@Service
@RequiredArgsConstructor
public class LoadService {

    /** 负载 Key 前缀：dispatch:cs:load:{csId} */
    private static final String LOAD_KEY_PREFIX = "dispatch:cs:load:";

    private final StringRedisTemplate stringRedisTemplate;

    /** 负载 +1，返回新值 */
    public int incr(Long csId) {
        Long value = stringRedisTemplate.opsForValue().increment(key(csId));
        return value == null ? 0 : value.intValue();
    }

    /** 负载 -1，下限 0（防止重复消费/对账缺失导致扣成负数） */
    public int decr(Long csId) {
        Long value = stringRedisTemplate.opsForValue().decrement(key(csId));
        if (value == null || value < 0) {
            stringRedisTemplate.opsForValue().set(key(csId), "0");
            return 0;
        }
        return value.intValue();
    }

    /** 查询当前负载，无记录按 0 处理 */
    public int get(Long csId) {
        String value = stringRedisTemplate.opsForValue().get(key(csId));
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String key(Long csId) {
        return LOAD_KEY_PREFIX + csId;
    }
}
