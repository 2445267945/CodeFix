package com.xd.service;

public interface TaskDispatcher {
    /**
     * 分发消息
     * @param messageJson MQ 收到的原始 JSON 字符串
     */
    void dispatch(String messageJson);
}
