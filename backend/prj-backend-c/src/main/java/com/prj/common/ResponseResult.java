package com.prj.common;

/** 统一响应结果类
 * @param <T>
 */
public class ResponseResult<T> {
    private Integer code;
    private String message;
    private T data;

    public ResponseResult() {
    }

    public ResponseResult(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ResponseResult<T> ok(T data) {
        return new ResponseResult<>(200, "操作成功", data);
    }

    public static <T> ResponseResult<T> ok(T data, String message) {
        return new ResponseResult<>(200, message, data);
    }

    public static <T> ResponseResult<T> failed(String message) {
        return new ResponseResult<>(500, message, null);
    }

    public static <T> ResponseResult<T> failed(String message, String error) {
        return new ResponseResult<>(500, message, null);
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}