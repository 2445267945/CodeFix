package com.xd.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class CacheServiceImpl {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public String get(String key) {
        return (String) redisTemplate.opsForValue().get(key);
    }

    public void put(String key, String value) {
        redisTemplate.opsForValue().setIfAbsent(key, value);
    }

    public void put(String key, String value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
    }

    public String permissionKey(String sessionId, String workspaceId, String toolName) {
        return String.format("agent:permission:%s:%s:%s", sessionId, workspaceId, toolName);
    }

    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }
}