package com.xd.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.core.JsonParseException;
import com.xd.mq.MessageHandler;
import com.xd.service.TaskDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class TaskDispatcherImpl implements TaskDispatcher {

    // Spring 会自动注入所有 MessageHandlerService 的实现类
    @Autowired
    private Map<String, MessageHandler> handlerMap;

    @Override
    public void dispatch(String messageJson) {
        JSONObject json;
        try {
            json = JSON.parseObject(messageJson);
        } catch (Exception e) {
            log.error("Agent消息JSON解析失败: {}", messageJson, e);
            throw e;
        }
        String type = json.getString("type");
        String version = json.getString("version");

        if (!"1.0".equals(version)) {
            log.warn("消息协议版本不对，目前 1.0, python: {}", version);
        }
        MessageHandler handler = handlerMap.get(type);

        if (handler != null) {
            handler.handleMsg(messageJson);
        } else {
            log.warn("未找到消息类型 [{}] 的处理器，消息被丢弃", type);
        }
    }
}
