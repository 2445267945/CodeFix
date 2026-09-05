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

    public String resolveAction(String toolName) {
        if (isBlank(toolName)) {
            return "EXECUTE";
        }

        return switch (toolName) {
            case "list_files", "read_file" -> "READ";
            case "search_file", "search_manual" -> "SEARCH";
            case "write_file", "delete_file", "apply_patch" -> "WRITE";
            case "verify_java_syntax" -> "VERIFY";
            case "run_explorer", "run_fixer" -> "DELEGATE";
            case "parse_java_code", "get_length" -> "EXECUTE";
            default -> "EXECUTE";
        };
    }

    public String buildWaitingSummary(String action, String toolName, Map<String, Object> data) {
        Map<String, Object> arguments = extractArguments(data);

        return switch (action) {
            case "READ" -> {
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
            case "VERIFY" -> "等待确认执行验证";
            case "DELEGATE" -> "等待确认委派子 Agent";
            default -> "等待确认执行 " + safeToolName(toolName);
        };
    }

    public String buildRunningSummary(String action, String toolName, Map<String, Object> data) {
        Map<String, Object> arguments = extractArguments(data);

        return switch (action) {
            case "READ" -> {
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
            case "VERIFY" -> "正在验证修改";
            case "DELEGATE" -> "正在委派子 Agent";
            default -> "正在执行 " + safeToolName(toolName);
        };
    }

    public String buildCompletedSummary(String action, String toolName, Map<String, Object> data, AgentChatBlockVO block) {
        Map<String, Object> arguments = extractArguments(data);

        return switch (action) {
            case "READ" -> {
                String path = firstString(arguments, "path", "file_name");
                if (!isBlank(path)) {
                    yield "已读取 " + path;
                }

                String summary = block == null ? null : block.getSummary();
                yield isBlank(summary) ? "已完成读取" : replaceRunningPrefix(summary, "已读取");
            }
            case "SEARCH" -> {
                String keyword = firstString(arguments, "keyword", "query");
                if (!isBlank(keyword)) {
                    yield "已完成搜索 " + keyword;
                }

                String summary = block == null ? null : block.getSummary();
                yield isBlank(summary) ? "已完成搜索" : replaceRunningPrefix(summary, "已完成搜索");
            }
            case "WRITE" -> {
                String path = firstString(arguments, "path", "file_name");

                if (!isBlank(path)) {
                    yield switch (toolName) {
                        case "delete_file" -> "已删除 " + path;
                        default -> "已修改 " + path;
                    };
                }

                String summary = block == null ? null : block.getSummary();

                yield isBlank(summary) ? "已完成文件修改" : replaceRunningPrefix(summary, "已修改");
            }
            case "VERIFY" -> "验证完成";
            case "DELEGATE" -> "子 Agent 执行完成";
            default -> "操作完成";
        };
    }

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

    private String replaceRunningPrefix(String summary, String prefix) {
        if (summary.startsWith("正在")) {
            return prefix + summary.substring(2);
        }

        return summary;
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
                return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {});
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

    private String firstString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);

            if (value != null && !String.valueOf(value).isBlank()) {
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
        return value == null ? null : String.valueOf(value);
    }

    private String safeToolName(String toolName) {
        return isBlank(toolName) ? "操作" : toolName;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}