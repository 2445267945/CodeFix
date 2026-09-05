package com.xd.service.impl;

import com.xd.model.dto.PermissionRuleDTO;
import com.xd.model.enums.PermissionDecisionEnum;
import com.xd.model.enums.PermissionProfileEnum;
import com.xd.model.enums.PermissionScopeEnum;
import com.xd.runtime.permission.PermissionPolicyEvaluator;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class PermissionPolicyEvaluatorImpl implements PermissionPolicyEvaluator {

    @Override
    public PermissionDecisionEnum evaluate(PermissionProfileEnum profile, List<PermissionRuleDTO> rules, String toolName, Map<String, Object> arguments) {
        if (profile == null || toolName == null || toolName.isBlank()) {
            return PermissionDecisionEnum.ASK;
        }

        List<PermissionRuleDTO> safeRules = rules == null ? Collections.emptyList() : rules;

        /*
         * 1. 先查 Granular Rule。
         *
         * Rule 优先级高于 Profile 默认策略。
         */
        PermissionDecisionEnum ruleDecision = evaluateRules(safeRules, toolName, arguments);

        if (ruleDecision != null) {
            return ruleDecision;
        }

        /*
         * 2. 没有命中 Rule，
         *    再使用 Profile 默认策略。
         */
        return evaluateProfile(profile, toolName, arguments);
    }

    private PermissionDecisionEnum evaluateRules(List<PermissionRuleDTO> rules, String toolName, Map<String, Object> arguments) {

        /*
         * 第一版：
         * 精确 Tool Rule 优先。
         */
        for (PermissionRuleDTO rule : rules) {

            if (rule == null || rule.getToolName() == null || rule.getDecision() == null) {
                continue;
            }

            if (!rule.getToolName().equals(toolName)) {
                continue;
            }

            /*
             * TOOL 级规则
             */
            if (rule.getScope() == PermissionScopeEnum.TOOL) {
                return rule.getDecision();
            }

            /*
             * PATH 级规则
             */
            if (rule.getScope() == PermissionScopeEnum.PATH) {

                String path = extractPath(arguments);

                if (path != null && matchPath(rule.getPattern(), path)) {
                    return rule.getDecision();
                }
            }
        }

        return null;
    }

    private PermissionDecisionEnum evaluateProfile(PermissionProfileEnum profile, String toolName, Map<String, Object> arguments) {
        return switch (profile) {
            case READ_ONLY -> evaluateReadOnly(toolName);
            case WORKSPACE -> evaluateWorkspace(toolName);
            case FULL_AUTO -> PermissionDecisionEnum.ALLOW;
        };
    }

    private PermissionDecisionEnum evaluateReadOnly(String toolName) {
        return switch (toolName) {
            case "list_files", "glob", "grep", "read_file", "search_manual", "parse_java_code" -> PermissionDecisionEnum.ALLOW;
            case "write_file", "delete_file", "apply_patch" -> PermissionDecisionEnum.DENY;
            case "verify_java_syntax" -> PermissionDecisionEnum.ALLOW;
            default -> PermissionDecisionEnum.ASK;
        };
    }

    private PermissionDecisionEnum evaluateWorkspace(String toolName) {
        return switch (toolName) {
            case "list_files", "glob", "grep", "read_file", "search_manual", "parse_java_code", "verify_java_syntax" -> PermissionDecisionEnum.ALLOW;
            case "write_file", "delete_file", "apply_patch" -> PermissionDecisionEnum.ASK;
            default -> PermissionDecisionEnum.ASK;
        };
    }

    private String extractPath(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return null;
        }
        Object path = arguments.get("path");
        if (path == null) {
            path = arguments.get("file_name");
        }
        return path == null ? null : String.valueOf(path);
    }

    private boolean matchPath(String pattern, String path) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }

        /*
         * 第一版先做最简单的匹配。
         *
         * 后面再升级 AntPathMatcher / PathPattern。
         */
        if (pattern.endsWith("/**")) {

            String prefix = pattern.substring(0, pattern.length() - 3);

            return path.startsWith(prefix);
        }

        return pattern.equals(path);
    }
}