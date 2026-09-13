package com.xd.controller.response;

import lombok.Data;

import java.io.Serializable;

@Data
public class Result<T> implements Serializable {

    private Integer code; // 状态码
    private String msg; // 提示信息
    private T data; // 返回数据

    public Result(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    // 成功响应 有参
    public static <T> Result<T> success(T data) {
        return new Result<>(1, "success", data);
    }

    // 成功响应 无参
    public static <T> Result<T> success() {
        return new Result<>(1, "success", null);
    }

    // 失败响应
    public static <T> Result<T> error(String msg) {
        return new Result<>(0, msg, null);
    }

    public static <T> Result<T> error(Integer code, String msg) {
        return new Result<>(code, msg, null);
    }
}
