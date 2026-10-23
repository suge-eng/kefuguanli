package com.aftersales.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 限流响应体兜底过滤器
 * 设计思路：网关的 RequestRateLimiter 命中限流时直接 setComplete 结束响应（HTTP 429），
 * 不会抛异常，因此 GatewayExceptionHandler 拿不到它；为了前端拿到统一 JSON 结构，
 * 这里用响应装饰器拦截「429 + 空响应体」的场景补写 JSON。
 * 只处理 429，其他错误仍由 GatewayExceptionHandler 统一收敛。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RateLimitResponseFilter implements WebFilter {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpResponse original = exchange.getResponse();
        ServerHttpResponseDecorator decorator = new ServerHttpResponseDecorator(original) {
            @Override
            public Mono<Void> setComplete() {
                // 仅限流场景补响应体；其他状态码保持原有行为
                if (getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    byte[] body;
                    try {
                        body = objectMapper.writeValueAsBytes(Map.of(
                                "code", 429,
                                "message", "请求过于频繁，请稍后重试",
                                "data", Map.of()));
                    } catch (Exception e) {
                        body = "{\"code\":429,\"message\":\"请求过于频繁，请稍后重试\"}"
                                .getBytes(StandardCharsets.UTF_8);
                    }
                    getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    return super.writeWith(Mono.just(bufferFactory().wrap(body)));
                }
                return super.setComplete();
            }
        };
        return chain.filter(exchange.mutate().response(decorator).build());
    }
}
