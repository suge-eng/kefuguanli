package com.aftersales.gateway.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 网关全局异常处理
 * 设计思路：网关是流量入口，错误必须收敛成统一 JSON 结构，
 * 避免把 WebFlux 默认的 HTML 错误页透给前端；
 * 重点覆盖「下游服务无实例（503）」——这是微服务最常见的故障形态，
 * 网关快速失败并给出可读提示，比让调用方超时等待友好
 */
@Slf4j
@Component
@Order(-2)   // 优先于 Spring Boot 默认的 DefaultErrorWebExceptionHandler
@RequiredArgsConstructor
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        // 响应已提交（如流式返回中途失败）时无法再改写，按原样抛出
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = "网关内部错误，请稍后重试";
        if (ex instanceof ResponseStatusException statusException) {
            int code = statusException.getStatusCode().value();
            status = HttpStatus.resolve(code) == null ? HttpStatus.INTERNAL_SERVER_ERROR
                    : HttpStatus.valueOf(code);
            message = switch (code) {
                case 503 -> "下游服务暂不可用，请稍后重试";
                case 404 -> "请求的接口不存在";
                case 429 -> "请求过于频繁，请稍后重试";
                default -> "网关处理失败：" + statusException.getReason();
            };
        }
        log.warn("网关异常收敛: path={}, status={}, err={}",
                exchange.getRequest().getPath(), status.value(), ex.toString());

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(
                    Map.of("code", status.value(), "message", message, "data", Map.of()));
        } catch (Exception e) {
            body = "{\"code\":500,\"message\":\"网关内部错误\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
