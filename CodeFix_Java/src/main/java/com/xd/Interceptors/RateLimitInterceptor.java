package com.xd.Interceptors;

import com.xd.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@Order(1)
public class RateLimitInterceptor implements HandlerInterceptor {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ResourceLoader resourceLoader;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String luaScript = null;
        try {
            Resource resource = resourceLoader.getResource("classpath:templates/Lua/rate_limit.lua");
            luaScript = new String(resource.getInputStream().readAllBytes());
        } catch (Exception e) {
            log.error("Lua 脚本读取失败");
        }
        // IP + 接口名，例如 "rate:192.168.1.1:/analyze"
        String key = "rate:" + request.getRemoteAddr() + ":" + request.getRequestURI();
        // 桶容量 10，每秒补充 2 个令牌（即 QPS=2）
        List<String> keys = Collections.singletonList(key);
        List<String> args = Arrays.asList("10", "2", String.valueOf(System.currentTimeMillis() / 1000));
        DefaultRedisScript<List> script = new DefaultRedisScript<>(luaScript, List.class);
        List<Long> result = redisTemplate.execute(script, keys, args.toArray());
        System.out.println(result);
        if (result.get(0) == 0L) {
            response.setStatus(429);
            throw new BusinessException("请求过于频繁，请稍后重试");
        }
        System.out.println("放行");
        return true;
    }
}
