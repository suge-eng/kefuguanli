package com.aftersales.common.exception;

import lombok.Getter;

/**
 * 业务异常
 * 设计思路：业务校验失败统一抛 BizException，由全局异常处理器转成 Result 返回，
 * Service 层不用写 try-catch 拼返回体
 */
@Getter
public class BizException extends RuntimeException {

    private final Integer code;

    public BizException(String message) {
        super(message);
        this.code = 500;
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
