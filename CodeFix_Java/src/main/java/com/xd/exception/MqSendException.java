package com.xd.exception;

public class MqSendException extends RuntimeException {
    public MqSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
