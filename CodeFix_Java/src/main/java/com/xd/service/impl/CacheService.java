package com.xd.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xd.model.vo.AuditResponseVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Service
public class CacheService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    public String get(String md5) {
        return (String) redisTemplate.opsForValue().get(md5);
    }

    public void put(String md5, String auditResponse) {
        redisTemplate.opsForValue().setIfAbsent(md5, auditResponse);
    }
}
