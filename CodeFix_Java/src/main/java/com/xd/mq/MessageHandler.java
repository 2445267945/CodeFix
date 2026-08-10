package com.xd.mq;

public interface MessageHandler {
    public void handleMsg(String agentMessageJSON);
}
