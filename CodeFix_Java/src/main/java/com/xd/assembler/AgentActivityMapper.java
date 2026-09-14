package com.xd.assembler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xd.model.entity.AgentFileChangeDO;
import com.xd.model.vo.AgentChatBlockVO;
import com.xd.model.vo.FileChangeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@Component
public class AgentActivityMapper {

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Python ToolRegistry 中实际存在的工具
     * -> 产品层 Activity Action
     */
    public String resolveAction(String toolName) {
        if (isBlank(toolName)) {
            return "EXECUTE";
        }

        return switch (toolName) {
            // 文件 / 目录读取
            case "list_files", "read_file" -> "READ";
            // 文件匹配
            case "glob" -> "READ";
            // 搜索
            case "grep", "search_manual" -> "SEARCH";
            // 文件修改
            case "write_file", "delete_file", "apply_patch" -> "WRITE";
            // 验证 / 命令执行
            case "verify_java_syntax", "run_command" -> "VERIFY";
            // 子 Agent
            case "run_explorer", "run_fixer" -> "DELEGATE";
            // 普通执行
            case "parse_java_code", "get_length" -> "EXECUTE";
            // 未知工具
            default -> "EXECUTE";
        };
    }

    /**
     * 等待用户确认时的产品文案。
     */
    public String buildWaitingSummary(String action, String toolName, Map<String, Object> data) {
        Map<String, Object> arguments = extractArguments(data);

        return switch (action) {
            case "READ" -> {
                if ("glob".equals(toolName)) {
                    String pattern = firstString(arguments, "pattern");
                    yield isBlank(pattern) ? "等待确认匹配文件" : "等待确认匹配文件 " + pattern;
                }

                String path = firstString(arguments, "path", "file_name");
                yield isBlank(path) ? "等待确认读取文件" : "等待确认读取 " + path;
            }

            case "SEARCH" -> {
                String keyword = firstString(arguments, "keyword", "query");
                yield isBlank(keyword) ? "等待确认搜索代码" : "等待确认搜索 " + keyword;
            }

            case "WRITE" -> {
                String path = firstString(arguments, "path", "file_name");

                yield switch (toolName) {
                    case "delete_file" -> isBlank(path) ? "等待确认删除文件" : "等待确认删除 " + path;
                    default -> isBlank(path) ? "等待确认修改文件" : "等待确认修改 " + path;
                };
            }

            case "VERIFY" -> {
                if ("run_command".equals(toolName)) {
                    String command = firstString(arguments, "command", "cmd");
                    yield isBlank(command) ? "等待确认执行验证命令" : "等待确认执行验证命令 " + command;
                }

                yield "等待确认执行验证";
            }

            case "DELEGATE" -> "等待确认委派子 Agent";

            case "EXECUTE" -> {
                String command = firstString(arguments, "command", "cmd");
                yield isBlank(command) ? "等待确认执行操作" : "等待确认执行 " + command;
            }

            default -> "等待确认执行操作";
        };
    }

    /**
     * 工具正在执行时的产品文案。
     */
    public String buildRunningSummary(String action, String toolName, Map<String, Object> data) {
        Map<String, Object> arguments = extractArguments(data);

        return switch (action) {
            case "READ" -> {
                if ("glob".equals(toolName)) {
                    String pattern = firstString(arguments, "pattern");
                    yield isBlank(pattern) ? "正在匹配文件" : "正在匹配文件 " + pattern;
                }

                String path = firstString(arguments, "path", "file_name");
                yield isBlank(path) ? "正在读取文件" : "正在读取 " + path;
            }

            case "SEARCH" -> {
                String keyword = firstString(arguments, "keyword", "query");
                yield isBlank(keyword) ? "正在搜索代码" : "正在搜索 " + keyword;
            }

            case "WRITE" -> {
                String path = firstString(arguments, "path", "file_name");

                yield switch (toolName) {
                    case "delete_file" -> isBlank(path) ? "正在删除文件" : "正在删除 " + path;
                    default -> isBlank(path) ? "正在修改文件" : "正在修改 " + path;
                };
            }

            case "VERIFY" -> {
                if ("run_command".equals(toolName)) {
                    String command = firstString(arguments, "command", "cmd");
                    yield isBlank(command) ? "正在执行验证命令" : "正在执行验证命令 " + command;
                }

                yield "正在验证修改";
            }

            case "DELEGATE" -> buildDelegateRunningSummary(toolName);

            case "EXECUTE" -> {
                String command = firstString(arguments, "command", "cmd");

                yield switch (toolName) {
                    case "parse_java_code" -> "正在解析 Java 代码";
                    case "get_length" -> "正在计算内容长度";
                    default -> isBlank(command) ? "正在执行操作" : "正在执行 " + command;
                };
            }

            default -> "正在执行操作";
        };
    }

    /**
     * 工具执行完成后的产品文案。
     *
     * arguments 来自 TOOL_CALL。
     * result 来自 TOOL_RESULT。
     */
    public String buildCompletedSummary(String action, String toolName, Map<String, Object> arguments, Map<String, Object> result) {
        if (arguments == null) {
            arguments = Collections.emptyMap();
        }

        if (result == null) {
            result = Collections.emptyMap();
        }

        return switch (action) {
            case "READ" -> {
                if ("glob".equals(toolName)) {
                    String pattern = firstString(arguments, "pattern");
                    yield isBlank(pattern) ? "已完成文件匹配" : "已完成文件匹配 " + pattern;
                }

                String path = firstString(arguments, "path", "file_name");
                yield isBlank(path) ? "已完成读取" : "已读取 " + path;
            }

            case "SEARCH" -> {
                String keyword = firstString(arguments, "keyword", "query");
                yield isBlank(keyword) ? "已完成搜索" : "已完成搜索 " + keyword;
            }

            case "WRITE" -> {
                String path = firstString(arguments, "path", "file_name");

                if (!isBlank(path)) {
                    yield "delete_file".equals(toolName) ? "已删除 " + path : "已修改 " + path;
                }

                yield "已完成文件修改";
            }

            case "EXECUTE" -> {
                String command = firstString(arguments, "command", "cmd");

                yield switch (toolName) {
                    case "parse_java_code" -> "已完成 Java 代码解析";
                    case "get_length" -> "已完成长度计算";
                    default -> isBlank(command) ? "执行完成" : "已执行 " + command;
                };
            }

            case "VERIFY" -> buildVerificationSummary(toolName, arguments, result);

            case "DELEGATE" -> buildDelegateSummary(toolName, result);

            default -> "操作完成";
        };
    }

    /**
     * 子 Agent 执行中的产品文案。
     */
    public String buildDelegateRunningSummary(String toolName) {
        return switch (toolName) {
            case "run_explorer" -> "Explorer Agent 正在执行";
            case "run_fixer" -> "Fixer Agent 正在执行";
            default -> resolveDelegateAgentName(toolName) + " 正在执行";
        };
    }

    /**
     * 子 Agent 等待确认时的产品文案。
     */
    public String buildDelegateWaitingSummary(String toolName) {
        return switch (toolName) {
            case "run_explorer" -> "正在调用 Explorer Agent";
            case "run_fixer" -> "正在调用 Fixer Agent";
            default -> "正在调用 " + resolveDelegateAgentName(toolName);
        };
    }

    /**
     * 提取子 Agent 最终结果的面向用户摘要。
     *
     * 支持以下结构：
     *
     * 1. TOOL_RESULT.result = AgentResult
     *
     * {
     *   "success": true,
     *   "agent_name": "Explorer",
     *   "result": {
     *      "status": "SUCCESS",
     *      "summary": "..."
     *   }
     * }
     *
     * 2. TOOL_RESULT.result = 直接业务结果
     *
     * {
     *   "status": "SUCCESS",
     *   "summary": "..."
     * }
     *
     * 3. result 为 JSON String。
     */
    public String buildDelegateSummary(String toolName, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return buildDelegateFallbackSummary(toolName);
        }

        Map<String, Object> agentResult = extractNestedResult(data);

        if (agentResult == null || agentResult.isEmpty()) {
            Object summary = data.get("summary");

            if (hasText(summary)) {
                return String.valueOf(summary);
            }

            Object content = data.get("content");

            if (hasText(content)) {
                return buildPlainDelegateSummary(String.valueOf(content));
            }

            Object result = data.get("result");

            if (result instanceof String text && !text.isBlank()) {
                return buildPlainDelegateSummary(text);
            }

            return buildDelegateFallbackSummary(toolName);
        }

        return extractDelegateBusinessSummary(agentResult, toolName);
    }

    /**
     * 从 TOOL_RESULT 中提取 AgentResult。
     *
     * data:
     *
     * {
     *   "toolCallId": "...",
     *   "tool": "run_explorer",
     *   "result": {
     *      "success": true,
     *      "agent_name": "Explorer",
     *      "result": {
     *          "status": "SUCCESS",
     *          "summary": "..."
     *      }
     *   }
     * }
     *
     * 返回 AgentResult.result。
     */
    private Map<String, Object> extractNestedResult(Map<String, Object> data) {
        Object outerResult = data.get("result");

        Map<String, Object> resultMap = toMap(outerResult);

        if (resultMap == null) {
            return null;
        }

        /*
         * AgentResult：
         *
         * {
         *   success
         *   agent_name
         *   result
         *   iterations
         *   metadata
         * }
         */
        Object nestedResult = resultMap.get("result");

        Map<String, Object> businessResult = toMap(nestedResult);

        if (businessResult != null) {
            return businessResult;
        }

        /*
         * 已经是业务结果：
         *
         * {
         *   status
         *   summary
         *   findings
         * }
         */
        return resultMap;
    }

    /**
     * 从子 Agent 业务结果中提取真正展示给用户的摘要。
     */
    private String extractDelegateBusinessSummary(Map<String, Object> result, String toolName) {
        if (result == null || result.isEmpty()) {
            return buildDelegateFallbackSummary(toolName);
        }

        Object summary = result.get("summary");

        if (hasText(summary)) {
            return String.valueOf(summary);
        }

        Object content = result.get("content");

        if (hasText(content)) {
            return buildPlainDelegateSummary(String.valueOf(content));
        }

        return buildStructuredDelegateSummary(result, toolName);
    }

    /**
     * 对缺少 summary 的结构化子 Agent 结果做兜底压缩。
     */
    private String buildStructuredDelegateSummary(Map<String, Object> result, String toolName) {
        Object findings = result.get("findings");

        if (findings instanceof Collection<?> collection && !collection.isEmpty()) {
            return resolveDelegateAgentName(toolName) + " 已完成分析，确认 " + collection.size() + " 项关键信息";
        }

        Object changes = result.get("changes");

        if (changes instanceof Collection<?> collection && !collection.isEmpty()) {
            return resolveDelegateAgentName(toolName) + " 已完成处理，涉及 " + collection.size() + " 项变更";
        }

        Object files = result.get("files");

        if (files instanceof Collection<?> collection && !collection.isEmpty()) {
            return resolveDelegateAgentName(toolName) + " 已完成分析，涉及 " + collection.size() + " 个文件";
        }

        Object riskPoints = result.get("risk_points");

        if (riskPoints instanceof Collection<?> collection && !collection.isEmpty()) {
            return resolveDelegateAgentName(toolName) + " 已完成分析，发现 " + collection.size() + " 个风险点";
        }

        return buildDelegateFallbackSummary(toolName);
    }

    /**
     * 子 Agent 返回普通文本时做长度压缩。
     */
    private String buildPlainDelegateSummary(String content) {
        if (isBlank(content)) {
            return null;
        }

        String normalized = content.trim();

        if (normalized.length() <= 300) {
            return normalized;
        }

        return normalized.substring(0, 300) + "...";
    }

    /**
     * Delegate 没有可展示结果时的兜底文案。
     */
    private String buildDelegateFallbackSummary(String toolName) {
        return resolveDelegateAgentName(toolName) + " 执行完成";
    }

    /**
     * 将 Map / JSON String 统一转换为 Map。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }

        if (value instanceof String text && !text.isBlank()) {
            return parseJsonMap(text);
        }

        return null;
    }

    /**
     * run_command 的验证结果展示。
     */
    private String buildVerificationSummary(String toolName, Map<String, Object> arguments, Map<String, Object> result) {
        if (!"run_command".equals(toolName)) {
            return "验证完成";
        }

        String command = firstString(arguments, "command", "cmd");
        boolean success = Boolean.TRUE.equals(result.get("success"));

        Object exitCodeValue = result.get("exit_code");
        String exitCode = exitCodeValue == null ? null : String.valueOf(exitCodeValue);

        String detail = extractVerificationDetail(result);

        String prefix = success ? "已执行验证命令" : "验证命令执行失败";

        String commandText = isBlank(command) ? "" : " " + command;

        String statusText = success ? "，通过" : "，失败";

        String exitText = isBlank(exitCode) ? "" : "（exit code " + exitCode + "）";

        if (isBlank(detail)) {
            return prefix + commandText + statusText + exitText;
        }

        return prefix + commandText + statusText + exitText + " · " + detail;
    }

    /**
     * 从 stdout / stderr 中提取对用户有意义的验证摘要。
     */
    private String extractVerificationDetail(Map<String, Object> result) {
        String stdout = toStringValue(result.get("stdout"));
        String stderr = toStringValue(result.get("stderr"));

        String detail = extractImportantOutput(stdout);

        if (isBlank(detail)) {
            detail = extractImportantOutput(stderr);
        }

        return detail;
    }

    /**
     * 提取测试结果、构建结果或最后一条有效输出。
     */
    private String extractImportantOutput(String output) {
        if (isBlank(output)) {
            return null;
        }

        String normalized = output.replace("\r", "").trim();
        String[] lines = normalized.split("\n");

        // 优先提取测试结果
        for (String line : lines) {
            String text = line.trim();

            if (text.matches(".*Tests run:.*")) {
                return limit(text, 160);
            }
        }

        // Maven / Gradle 等构建结果
        for (String line : lines) {
            String text = line.trim();

            if (text.contains("BUILD SUCCESS") || text.contains("BUILD FAILURE")) {
                return limit(text, 160);
            }
        }

        // 没有标准测试结果时，取最后一条有效输出
        for (int i = lines.length - 1; i >= 0; i--) {
            String text = lines[i].trim();

            if (!text.isBlank()) {
                return limit(text, 160);
            }
        }

        return null;
    }

    private String limit(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength) + "...";
    }

    /**
     * 根据工具执行结果判断 Block 状态。
     */
    public String resolveResultStatus(String eventStatus, Map<String, Object> data) {
        if (!isBlank(eventStatus)) {
            switch (eventStatus.trim().toUpperCase()) {
                case "ERROR", "FAILED", "FAIL" -> {
                    return "failed";
                }
            }
        }

        Object result = data == null ? null : data.get("result");

        if (result instanceof Map<?, ?> resultMap) {
            return resolveResultStatusFromMap(resultMap);
        }

        if (result instanceof String text && !text.isBlank()) {
            try {
                Object parsed = objectMapper.readValue(text, Object.class);

                if (parsed instanceof Map<?, ?> resultMap) {
                    return resolveResultStatusFromMap(resultMap);
                }
            } catch (Exception ignored) {
            }
        }

        return "completed";
    }

    private String resolveResultStatusFromMap(Map<?, ?> resultMap) {
        Object errorType = resultMap.get("error_type");

        if ("USER_REJECTED".equalsIgnoreCase(String.valueOf(errorType))) {
            return "failed";
        }

        Object success = resultMap.get("success");

        if (Boolean.FALSE.equals(success)) {
            return "failed";
        }

        return "completed";
    }

    /**
     * 普通事件状态 -> UI Block 状态。
     */
    public String resolveGenericStatus(String eventStatus) {
        if (isBlank(eventStatus)) {
            return "completed";
        }

        return switch (eventStatus.trim().toUpperCase()) {
            case "THINKING", "EXECUTING", "RUNNING" -> "running";
            case "ERROR", "FAILED", "FAIL" -> "failed";
            default -> "completed";
        };
    }

    /**
     * 根据持久化文件变更记录更新 Block。
     */
    public void applyFileChange(AgentChatBlockVO block, AgentFileChangeDO fileChange) {
        if (block == null || fileChange == null) {
            return;
        }

        block.setType("file_change");
        block.setAction("WRITE");
        block.setFilePath(fileChange.getFilePath());
        block.setOperation(fileChange.getOperation());
        block.setAddedLines(fileChange.getAddedLines());
        block.setRemovedLines(fileChange.getRemovedLines());
        block.setDiffId(fileChange.getDiffId());
        block.setSummary(buildFileChangeSummary(fileChange));
    }

    /**
     * 根据 TOOL_RESULT 中的 file_change 结果更新 Block。
     */
    public void applyFileChange(AgentChatBlockVO block, Map<String, Object> data, String diffId) {
        Object resultObject = data == null ? null : data.get("result");

        if (!(resultObject instanceof Map<?, ?> resultMap)) {
            return;
        }

        if (!"file_change".equals(resultMap.get("type"))) {
            return;
        }

        FileChangeVO change = parseFileChange(resultMap);

        if (change == null) {
            return;
        }

        block.setType("file_change");
        block.setAction("WRITE");
        block.setFilePath(change.getFilePath());
        block.setOperation(change.getOperation());
        block.setAddedLines(change.getAddedLines());
        block.setRemovedLines(change.getRemovedLines());
        block.setDiffId(diffId);
        block.setChangeSummary(change.getDiff());
        block.setSummary(buildFileChangeSummary(change));
    }

    private String buildFileChangeSummary(AgentFileChangeDO change) {
        String path = isBlank(change.getFilePath()) ? "文件" : change.getFilePath();

        String operation = change.getOperation() == null ? "" : change.getOperation().trim().toLowerCase();

        return switch (operation) {
            case "created" -> "已创建 " + path;
            case "modified" -> "已修改 " + path;
            case "deleted" -> "已删除 " + path;
            case "renamed" -> "已重命名 " + path;
            default -> "已修改 " + path;
        };
    }

    private String buildFileChangeSummary(FileChangeVO change) {
        String path = isBlank(change.getFilePath()) ? "文件" : change.getFilePath();

        String operation = change.getOperation() == null ? "" : change.getOperation().trim().toLowerCase();

        return switch (operation) {
            case "created" -> "已创建 " + path;
            case "modified" -> "已修改 " + path;
            case "deleted" -> "已删除 " + path;
            case "renamed" -> "已重命名 " + path;
            default -> "已修改 " + path;
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractArguments(Map<String, Object> data) {
        if (data == null) {
            return Collections.emptyMap();
        }

        Object arguments = data.get("arguments");

        if (arguments instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }

        if (arguments instanceof String text && !text.isBlank()) {
            try {
                return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {
                });
            } catch (Exception ignored) {
            }
        }

        return Collections.emptyMap();
    }

    private FileChangeVO parseFileChange(Map<?, ?> resultMap) {
        FileChangeVO change = new FileChangeVO();

        change.setType(toStringValue(resultMap.get("type")));
        change.setFilePath(toStringValue(resultMap.get("filePath")));
        change.setOperation(toStringValue(resultMap.get("operation")));
        change.setAddedLines(toInteger(resultMap.get("addedLines")));
        change.setRemovedLines(toInteger(resultMap.get("removedLines")));
        change.setDiff(toStringValue(resultMap.get("diff")));

        return change;
    }

    private String resolveDelegateAgentName(String toolName) {
        return switch (toolName) {
            case "run_explorer" -> "Explorer";
            case "run_fixer" -> "Fixer";
            default -> "子 Agent";
        };
    }

    private String firstString(Map<String, Object> map, String... keys) {
        if (map == null) {
            return null;
        }

        for (String key : keys) {
            Object value = map.get(key);

            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }

        return null;
    }

    private boolean hasText(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 解析 JSON Object。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(String text) {
        if (isBlank(text)) {
            return null;
        }

        try {
            Object value = objectMapper.readValue(text, Object.class);

            if (value instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }

            /*
             * 兼容 JSON 字符串再次包裹 JSON 的情况。
             */
            if (value instanceof String nested && !nested.equals(text) && !nested.isBlank()) {
                return parseJsonMap(nested);
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}