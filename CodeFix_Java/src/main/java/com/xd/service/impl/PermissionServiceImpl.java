package com.xd.service.impl;

import com.xd.mapper.AgentRunMapper;
import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.dto.PermissionRuleDTO;
import com.xd.model.entity.AgentRunDO;
import com.xd.model.enums.AgentEventEnum;
import com.xd.model.enums.PermissionDecisionEnum;
import com.xd.model.enums.PermissionProfileEnum;
import com.xd.runtime.permission.PermissionPolicyEvaluator;
import com.xd.runtime.permission.PermissionRuntimeStore;
import com.xd.service.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PermissionServiceImpl implements PermissionService {

    @Autowired
    private AgentRunMapper agentRunMapper;

    @Autowired
    private PermissionRuntimeStore permissionRuntimeStore;

    @Autowired
    private PermissionPolicyEvaluator permissionPolicyEvaluator;

    @Override
    public PermissionDecisionEnum evaluate(AgentMessageDTO messageDTO) {
        if (messageDTO == null) {
            return PermissionDecisionEnum.ASK;
        }
        /*
         * Permission 只处理 TOOL_WAITING。
         */
        if (!AgentEventEnum.TOOL_WAITING.eventDesc.equalsIgnoreCase(messageDTO.getEvent())) {
            return PermissionDecisionEnum.ASK;
        }
        if (messageDTO.getRunId() == null || messageDTO.getRunId().isBlank()) {
            return PermissionDecisionEnum.ASK;
        }

        /*
         * 一个 Task 对应当前正在执行的 Run。
         */
        AgentRunDO run = agentRunMapper.selectByTaskId(messageDTO.getTaskId()).get(0);

        if (run == null) {
            log.warn("Permission Evaluation 找不到 Run: taskId={}", messageDTO.getTaskId());
            return PermissionDecisionEnum.ASK;
        }

        /*
         * 读取当前 Run 的 Permission Profile。
         */
        PermissionProfileEnum profile;

        try {
            profile = PermissionProfileEnum.valueOf(run.getPermissionProfile());
        } catch (Exception e) {
            log.warn("未知 Permission Profile，降级为 WORKSPACE: runId={}, profile={}", run.getRunId(), run.getPermissionProfile());
            profile = PermissionProfileEnum.WORKSPACE;
        }

        /*
         * Tool。
         */
        String toolName = extractToolName(messageDTO);

        if (toolName == null || toolName.isBlank()) {
            return PermissionDecisionEnum.ASK;
        }

        /*
         * Tool Arguments。
         */
        Map<String, Object> arguments = extractArguments(messageDTO);

        /*
         * 当前 Run 的 Granular Permission Rules。
         */
        List<PermissionRuleDTO> rules = permissionRuntimeStore.getRules(run.getRunId());

        /*
         * Profile + Rules
         *      ↓
         * Permission Policy Evaluator
         *      ↓
         * ALLOW / ASK / DENY
         */
        PermissionDecisionEnum decision = permissionPolicyEvaluator.evaluate(profile, rules, toolName, arguments);

        log.info("Permission Evaluation: runId={}, profile={}, tool={}, ruleCount={}, decision={}", run.getRunId(), profile, toolName, rules == null ? 0 : rules.size(), decision);

        return decision;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractArguments(AgentMessageDTO messageDTO) {

        if (messageDTO.getOutput() == null) {
            return Collections.emptyMap();
        }

        Object arguments = messageDTO.getOutput().get("arguments");

        if (arguments instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }

        return Collections.emptyMap();
    }

    private String extractToolName(AgentMessageDTO messageDTO) {

        if (messageDTO.getOutput() == null) {
            return null;
        }

        Object tool = messageDTO.getOutput().get("tool");

        return tool == null ? null : String.valueOf(tool);
    }
}