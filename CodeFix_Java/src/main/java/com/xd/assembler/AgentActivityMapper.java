package com.xd.assembler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xd.model.entity.AgentFileChangeDO;
import com.xd.model.vo.AgentChatBlockVO;
import com.xd.model.vo.FileChangeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

            // 验证
            case "verify_java_syntax" -> "VERIFY";

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
    public String buildWaitingSummary(
            String action,
            String toolName,
            Map<String, Object> data
    ) {
        Map<String, Object> arguments = extractArguments(data);

        return switch (action) {
            case "READ" -> {
                if ("glob".equals(toolName)) {
                    String pattern = firstString(arguments, "pattern");

                    yield isBlank(pattern)
                            ? "等待确认匹配文件"
                            : "等待确认匹配文件 " + pattern;
                }

                String path = firstString(arguments, "path", "file_name");

                yield isBlank(path)
                        ? "等待确认读取文件"
                        : "等待确认读取 " + path;
            }

            case "SEARCH" -> {
                String keyword = firstString(arguments, "keyword", "query");

                yield isBlank(keyword)
                        ? "等待确认搜索代码"
                        : "等待确认搜索 " + keyword;
            }

            case "WRITE" -> {
                String path = firstString(arguments, "path", "file_name");

                yield switch (toolName) {
                    case "delete_file" ->
                            isBlank(path)
                                    ? "等待确认删除文件"
                                    : "等待确认删除 " + path;

                    default ->
                            isBlank(path)
                                    ? "等待确认修改文件"
                                    : "等待确认修改 " + path;
                };
            }

            case "VERIFY" ->
                    "等待确认执行验证";

            case "DELEGATE" ->
                    "等待确认委派子 Agent";

            case "EXECUTE" -> {
                String command = firstString(arguments, "command", "cmd");

                yield isBlank(command)
                        ? "等待确认执行操作"
                        : "等待确认执行 " + command;
            }

            default ->
                    "等待确认执行操作";
        };
    }

    /**
     * 工具正在执行时的产品文案。
     */
    public String buildRunningSummary(
            String action,
            String toolName,
            Map<String, Object> data
    ) {
        Map<String, Object> arguments = extractArguments(data);

        return switch (action) {
            case "READ" -> {
                if ("glob".equals(toolName)) {
                    String pattern = firstString(arguments, "pattern");

                    yield isBlank(pattern)
                            ? "正在匹配文件"
                            : "正在匹配文件 " + pattern;
                }

                String path = firstString(arguments, "path", "file_name");

                yield isBlank(path)
                        ? "正在读取文件"
                        : "正在读取 " + path;
            }

            case "SEARCH" -> {
                String keyword = firstString(arguments, "keyword", "query");

                yield isBlank(keyword)
                        ? "正在搜索代码"
                        : "正在搜索 " + keyword;
            }

            case "WRITE" -> {
                String path = firstString(arguments, "path", "file_name");

                yield switch (toolName) {
                    case "delete_file" ->
                            isBlank(path)
                                    ? "正在删除文件"
                                    : "正在删除 " + path;

                    default ->
                            isBlank(path)
                                    ? "正在修改文件"
                                    : "正在修改 " + path;
                };
            }

            case "VERIFY" ->
                    "正在验证修改";

            case "DELEGATE" ->
                    "正在委派子 Agent";

            case "EXECUTE" -> {
                String command = firstString(arguments, "command", "cmd");

                yield switch (toolName) {
                    case "parse_java_code" ->
                            "正在解析 Java 代码";

                    case "get_length" ->
                            "正在计算内容长度";

                    default ->
                            isBlank(command)
                                    ? "正在执行操作"
                                    : "正在执行 " + command;
                };
            }

            default ->
                    "正在执行操作";
        };
    }

    /**
     * 工具执行完成后的产品文案。
     *
     * 注意：
     * TOOL_RESULT 本身通常不再包含 arguments，
     * 调用方需要传入该 Tool Call 对应的 arguments。
     */
    public String buildCompletedSummary(
            String action,
            String toolName,
            Map<String, Object> arguments
    ) {
        if (arguments == null) {
            arguments = Collections.emptyMap();
        }

        return switch (action) {
            case "READ" -> {
                if ("glob".equals(toolName)) {
                    String pattern = firstString(arguments, "pattern");

                    yield isBlank(pattern)
                            ? "已完成文件匹配"
                            : "已完成文件匹配 " + pattern;
                }

                String path = firstString(arguments, "path", "file_name");

                yield isBlank(path)
                        ? "已完成读取"
                        : "已读取 " + path;
            }

            case "SEARCH" -> {
                String keyword = firstString(arguments, "keyword", "query");

                yield isBlank(keyword)
                        ? "已完成搜索"
                        : "已完成搜索 " + keyword;
            }

            case "WRITE" -> {
                String path = firstString(arguments, "path", "file_name");

                if (!isBlank(path)) {
                    yield "delete_file".equals(toolName)
                            ? "已删除 " + path
                            : "已修改 " + path;
                }

                yield "已完成文件修改";
            }

            case "EXECUTE" -> {
                String command = firstString(arguments, "command", "cmd");

                yield switch (toolName) {
                    case "parse_java_code" ->
                            "已完成 Java 代码解析";

                    case "get_length" ->
                            "已完成长度计算";

                    default ->
                            isBlank(command)
                                    ? "执行完成"
                                    : "已执行 " + command;
                };
            }

            case "VERIFY" ->
                    "验证完成";

            case "DELEGATE" ->
                    "子 Agent 执行完成";

            default ->
                    "操作完成";
        };
    }

    /**
     * 根据工具执行结果判断 Block 状态。
     */
    public String resolveResultStatus(
            String eventStatus,
            Map<String, Object> data
    ) {
        if (!isBlank(eventStatus)) {
            switch (eventStatus.trim().toUpperCase()) {
                case "ERROR", "FAILED", "FAIL" -> {
                    return "failed";
                }
            }
        }

        Object result = data == null
                ? null
                : data.get("result");

        if (result instanceof Map<?, ?> resultMap) {
            Object errorType = resultMap.get("error_type");

            if ("USER_REJECTED".equalsIgnoreCase(String.valueOf(errorType))) {
                return "failed";
            }

            Object success = resultMap.get("success");

            if (Boolean.FALSE.equals(success)) {
                return "failed";
            }
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
    public void applyFileChange(
            AgentChatBlockVO block,
            AgentFileChangeDO fileChange
    ) {
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
    public void applyFileChange(
            AgentChatBlockVO block,
            Map<String, Object> data,
            String diffId
    ) {
        Object resultObject = data == null
                ? null
                : data.get("result");

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
        String path = isBlank(change.getFilePath())
                ? "文件"
                : change.getFilePath();

        String operation = change.getOperation() == null
                ? ""
                : change.getOperation().trim().toLowerCase();

        return switch (operation) {
            case "created" -> "已创建 " + path;
            case "modified" -> "已修改 " + path;
            case "deleted" -> "已删除 " + path;
            case "renamed" -> "已重命名 " + path;
            default -> "已修改 " + path;
        };
    }

    private String buildFileChangeSummary(FileChangeVO change) {
        String path = isBlank(change.getFilePath())
                ? "文件"
                : change.getFilePath();

        String operation = change.getOperation() == null
                ? ""
                : change.getOperation().trim().toLowerCase();

        return switch (operation) {
            case "created" -> "已创建 " + path;
            case "modified" -> "已修改 " + path;
            case "deleted" -> "已删除 " + path;
            case "renamed" -> "已重命名 " + path;
            default -> "已修改 " + path;
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractArguments(
            Map<String, Object> data
    ) {
        if (data == null) {
            return Collections.emptyMap();
        }

        Object arguments = data.get("arguments");

        if (arguments instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }

        if (arguments instanceof String text && !text.isBlank()) {
            try {
                return objectMapper.readValue(
                        text,
                        new TypeReference<Map<String, Object>>() {}
                );
            } catch (Exception ignored) {
            }
        }

        return Collections.emptyMap();
    }

    private FileChangeVO parseFileChange(
            Map<?, ?> resultMap
    ) {
        FileChangeVO change = new FileChangeVO();

        change.setType(
                toStringValue(resultMap.get("type"))
        );

        change.setFilePath(
                toStringValue(resultMap.get("filePath"))
        );

        change.setOperation(
                toStringValue(resultMap.get("operation"))
        );

        change.setAddedLines(
                toInteger(resultMap.get("addedLines"))
        );

        change.setRemovedLines(
                toInteger(resultMap.get("removedLines"))
        );

        change.setDiff(
                toStringValue(resultMap.get("diff"))
        );

        return change;
    }

    private String firstString(
            Map<String, Object> map,
            String... keys
    ) {
        for (String key : keys) {
            Object value = map.get(key);

            if (value != null
                    && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }

        return null;
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
        return value == null
                ? null
                : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}