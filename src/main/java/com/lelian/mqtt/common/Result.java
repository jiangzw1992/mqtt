package com.lelian.mqtt.common;


import org.springframework.stereotype.Component;

import java.util.HashMap;

@Component
public class Result extends HashMap<String, Object> {

    private Integer code;

    private String msg;

    public Result() {
        this.code = 200;
    }

    public Result success() {
        this.put("ok", true);
        this.put("code", 200);
        return this;
    }

    public Result fail() {
        this.put("ok", false);
        this.put("code", 400);
        return this;
    }

    public Result code(int code) {
        this.put("code", code);
        return this;
    }

    public Result message(String message) {
        this.put("message", message);
        return this;
    }

    public Result setData(Object data) {
        this.put("data", data);
        return this;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
