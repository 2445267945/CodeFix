package com.xd.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xd.model.dto.AuditResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class CacheService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    public AuditResponse get(String md5) {
        Object auditResponseObj = redisTemplate.opsForValue().get(md5);
        return objectMapper.convertValue(auditResponseObj, AuditResponse.class);
    }

    public void put(String md5, AuditResponse auditResponse) {
        redisTemplate.opsForValue().setIfAbsent(md5, auditResponse);
    }
}
