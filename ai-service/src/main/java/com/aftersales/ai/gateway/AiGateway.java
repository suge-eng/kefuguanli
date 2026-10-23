package com.aftersales.ai.gateway;

import com.aftersales.ai.config.AiProperties;
import com.aftersales.common.exception.BizException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * AI 能力网关（业务层调用大模型的唯一入口）
 * 设计思路：
 * 1. 统一收口——重试/降级/统计逻辑只写一处，业务 Agent 只关心 prompt；
 * 2. 降级开关存 Redis，多实例共享状态，运维可通过接口一键切断 AI 调用保主链路；
 * 3. 调用统计写 Redis Hash（total/success/fail/totalMs），不动业务库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiGateway {

    /** 降级开关 Redis key（多实例共享） */
    private static final String DEGRADED_KEY = "ai:gateway:degraded";

    /** 调用统计 Hash key */
    private static final String STATS_CHAT_KEY = "ai:gateway:stats:chat";
    private static final String STATS_EMBED_KEY = "ai:gateway:stats:embed";

    private static final long RETRY_INTERVAL_MS = 500L;

    private final ChatClient chatClient;
    private final EmbeddingClient embeddingClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final AiProperties aiProperties;

    /** 本地降级标记（volatile 保证多线程可见；真实状态以 Redis 为准） */
    private volatile boolean degraded = false;

    /**
     * 启动时从 Redis 恢复降级状态；Redis 挂了按未降级处理（宁可可用不可误降级）
     */
    @PostConstruct
    public void init() {
        try {
            String value = stringRedisTemplate.opsForValue().get(DEGRADED_KEY);
            this.degraded = "true".equals(value);
        } catch (Exception e) {
            log.warn("降级开关初始化失败，按未降级处理: {}", e.getMessage());
            this.degraded = false;
        }
    }

    /** 自由文本对话（含降级判断 + 重试 + 统计） */
    public String chat(String systemPrompt, String userPrompt) {
        return invokeWithGuard("chat", () -> chatClient.chat(systemPrompt, userPrompt));
    }

    /** 结构化 JSON 对话（含降级判断 + 重试 + 统计，统计归入 chat） */
    public String chatJson(String systemPrompt, String userPrompt) {
        return invokeWithGuard("chat", () -> chatClient.chatJson(systemPrompt, userPrompt));
    }

    /** 文本向量化（含降级判断 + 重试 + 统计） */
    public List<Float> embed(String text) {
        return invokeWithGuard("embed", () -> embeddingClient.embed(text));
    }

    /**
     * 设置降级开关：写 Redis（多实例共享）+ 本地标记
     */
    public void setDegraded(boolean enabled) {
        this.degraded = enabled;
        try {
            stringRedisTemplate.opsForValue().set(DEGRADED_KEY, String.valueOf(enabled));
        } catch (Exception e) {
            // Redis 写失败只影响其他实例同步，本实例本地标记已生效
            log.warn("降级开关写Redis失败: {}", e.getMessage());
        }
        log.info("AI能力降级开关已切换: degraded={}", enabled);
    }

    public boolean isDegraded() {
        return degraded;
    }

    /**
     * 调用统计：返回 chat/embed 两个 Hash 的统计数据（含平均耗时 avgMs）
     */
    public Map<String, Object> stats() {
        Map<String, Object> result = new HashMap<>();
        result.put("chat", readStats(STATS_CHAT_KEY));
        result.put("embed", readStats(STATS_EMBED_KEY));
        result.put("degraded", degraded);
        return result;
    }

    /**
     * 统一调用模板：降级判断 -> 重试 -> 统计
     * 重试次数取 ai.chat.retry-times（向量调用复用同一档位，避免引入过多配置项）
     */
    private <T> T invokeWithGuard(String type, Supplier<T> call) {
        if (degraded) {
            throw new BizException("AI能力已降级，请稍后重试");
        }
        String statsKey = "chat".equals(type) ? STATS_CHAT_KEY : STATS_EMBED_KEY;
        int maxAttempts = aiProperties.getChat().getRetryTimes() + 1;
        long start = System.currentTimeMillis();
        incrementStats(statsKey, "total");
        try {
            for (int i = 1; i <= maxAttempts; i++) {
                try {
                    T result = call.get();
                    incrementStats(statsKey, "success");
                    incrementStats(statsKey, "totalMs", System.currentTimeMillis() - start);
                    return result;
                } catch (Exception e) {
                    if (i >= maxAttempts) {
                        throw e;
                    }
                    // 间隔 500ms 再重试，给对端短暂抖动恢复时间
                    log.warn("大模型调用失败，{}ms后重试({}/{}): {}", RETRY_INTERVAL_MS, i, maxAttempts - 1, e.getMessage());
                    try {
                        Thread.sleep(RETRY_INTERVAL_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
            throw new IllegalStateException("unreachable");
        } catch (Exception e) {
            incrementStats(statsKey, "fail");
            incrementStats(statsKey, "totalMs", System.currentTimeMillis() - start);
            throw new BizException("大模型调用失败: " + e.getMessage());
        }
    }

    private void incrementStats(String key, String field) {
        incrementStats(key, field, 1L);
    }

    /** 统计写 Redis 失败只记 warn，绝不影响调用主链路 */
    private void incrementStats(String key, String field, long delta) {
        try {
            stringRedisTemplate.opsForHash().increment(key, field, delta);
        } catch (Exception e) {
            log.warn("AI调用统计写入失败: key={}, field={}", key, field);
        }
    }

    private Map<String, Object> readStats(String key) {
        Map<String, Object> stats = new HashMap<>();
        try {
            Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
            long total = parseLong(entries.get("total"));
            long success = parseLong(entries.get("success"));
            long fail = parseLong(entries.get("fail"));
            long totalMs = parseLong(entries.get("totalMs"));
            stats.put("total", total);
            stats.put("success", success);
            stats.put("fail", fail);
            stats.put("totalMs", totalMs);
            // 平均耗时按全部调用次数均摊（含失败）
            stats.put("avgMs", total == 0 ? 0 : totalMs / total);
        } catch (Exception e) {
            log.warn("AI调用统计读取失败: key={}", key);
        }
        return stats;
    }

    private long parseLong(Object value) {
        return value == null ? 0L : Long.parseLong(value.toString());
    }
}
