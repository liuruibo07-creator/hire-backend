package com.hire.common;

import lombok.Data;

import java.io.Serializable;


//统一响应结果类
@Data
public class Result<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    //状态码比如规定1表示成功,0表示失败 可以自定义
    private Integer code;
    //成功或者失败的消息提示
    private String msg;
    //数据
    private T data;
    //成功响应,没有数据返回,通常用于增删改操作
    public static <T> Result<T> success() {
        Result<T> result = new Result<T>();
        result.code = 1;
        result.msg = "操作成功";
        return result;
    }
    //成功响应,有数据返回,通常用于查询操作
    public static <T> Result<T> success(T object) {
        Result<T> result = new Result<T>();
        result.data = object;
        result.code = 1;
        result.msg = "操作成功";
        return result;
    }
    //失败响应,返回错误原因
    public static <T> Result<T> error(String msg) {
        Result result = new Result();
        result.msg = msg;
        result.code = 0;
        return result;
    }
}
