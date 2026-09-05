package com.xd.exception;


import lombok.Getter;


/**
 * 业务异常。
 *
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 业务错误码。
     */
    private final Integer code;

    public BusinessException(String msg) {
        this(500, msg);
    }

    public BusinessException(Integer code, String msg) {
        super(msg);
        this.code = code;
    }

    public BusinessException(Integer code, String msg, Throwable cause) {
        super(msg, cause);
        this.code = code;
    }

    public BusinessException(String msg, Throwable cause) {
        super(msg, cause);
        this.code = 500;
    }
}