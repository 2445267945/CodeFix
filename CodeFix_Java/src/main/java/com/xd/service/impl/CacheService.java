package com.xd.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xd.model.vo.AuditResponseVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class CacheService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    public AuditResponseVO get(String md5) {
        Object auditResponseObj = redisTemplate.opsForValue().get(md5);
        return objectMapper.convertValue(auditResponseObj, AuditResponseVO.class);
    }

    public void put(String md5, String auditResponse) {
        redisTemplate.opsForValue().setIfAbsent(md5, auditResponse);
    }
}
