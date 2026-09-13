package com.xd.service.impl;


import com.alibaba.fastjson2.JSON;
import com.xd.model.dto.PermissionRuleDTO;
import com.xd.runtime.permission.PermissionRuntimeStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class PermissionRuntimeStoreImpl implements PermissionRuntimeStore {

    private static final String KEY_PREFIX = "agent:permission:rules:";

    @Autowired
    private CacheServiceImpl cacheService;

    @Override
    public List<PermissionRuleDTO> getRules(String runId) {
        if (runId == null || runId.isBlank()) {
            return Collections.emptyList();
        }
        String key = buildKey(runId);
        String value = cacheService.get(key);
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<PermissionRuleDTO> rules = JSON.parseArray(value, PermissionRuleDTO.class);
            return rules == null ? Collections.emptyList() : rules;
        } catch (Exception e) {
            log.warn("Permission Rule 解析失败: runId={}, key={}", runId, key, e);

            /*
             * 权限数据异常时不要自动放行。
             *
             * 返回空规则，
             * 由 PermissionEvaluator 使用 Profile
             * 继续计算。
             */
            return Collections.emptyList();
        }
    }

    @Override
    public void saveRule(String runId, PermissionRuleDTO rule) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId 不能为空");
        }
        if (rule == null) {
            throw new IllegalArgumentException("PermissionRule 不能为空");
        }
        List<PermissionRuleDTO> rules = new ArrayList<>(getRules(runId));
        rules.removeIf(existing -> sameRule(existing, rule));
        rules.add(rule);
        String key = buildKey(runId);

        /*
         * Permission Runtime Rule
         * 第一版跟随 Runtime 生命周期，
         * 后续再根据实际需求决定 TTL。
         */
        cacheService.put(key, JSON.toJSONString(rules));
        log.info("Permission Rule 保存成功: runId={}, tool={}, decision={}, scope={}, pattern={}", runId, rule.getToolName(), rule.getDecision(), rule.getScope(), rule.getPattern());
    }

    @Override
    public void deleteRule(String runId, PermissionRuleDTO rule) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId 不能为空");
        }
        if (rule == null) {
            return;
        }
        List<PermissionRuleDTO> rules = new ArrayList<>(getRules(runId));
        rules.removeIf(existing -> sameRule(existing, rule));
        String key = buildKey(runId);
        if (rules.isEmpty()) {
            cacheService.delete(key);
            return;
        }
        cacheService.put(key, JSON.toJSONString(rules));
    }

    private String buildKey(String runId) {
        return KEY_PREFIX + runId;
    }

    private boolean sameRule(PermissionRuleDTO a, PermissionRuleDTO b) {

        if (a == null || b == null) {
            return false;
        }

        return equals(a.getToolName(), b.getToolName())
                && a.getScope() == b.getScope()
                && equals(a.getPattern(), b.getPattern());
    }

    private boolean equals(String a, String b) {
        if (a == null) {
            return b == null;
        }

        return a.equals(b);
    }
}