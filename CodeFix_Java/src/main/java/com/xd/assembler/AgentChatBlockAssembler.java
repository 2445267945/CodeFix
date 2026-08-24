package com.xd.assembler;

import com.alibaba.fastjson2.JSON;
import com.xd.context.AgentChatAssembleContext;
import com.xd.model.entity.AgentEventDO;
import com.xd.model.entity.AgentFileChangeDO;
import com.xd.model.vo.AgentChatBlockVO;
import com.xd.model.vo.FileChangeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class AgentChatBlockAssembler {

    public List<AgentChatBlockVO> assemble(List<AgentEventDO> events, AgentChatAssembleContext context) {

        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }

        if (context == null) {
            context = AgentChatAssembleContext.builder().build();
        }

        List<AgentEventDO> sortedEvents = events.stream().filter(Objects::nonNull).sorted(Comparator.comparing(AgentEventDO::getEventTimestamp, Comparator.nullsLast(Long::compareTo))).toList();

        List<AgentChatBlockVO> blocks = new ArrayList<>();

        /*
         * 一个 Tool Call 在 UI 中对应一个 Block。
         *
         * TOOL_WAITING
         *      ↓
         * TOOL_CALL
         *      ↓
         * TOOL_RESULT
         *
         * 通过 toolCallId 始终关联到同一个 Block。
         */
        Map<String, AgentChatBlockVO> pendingToolBlocks = new HashMap<>();

        for (AgentEventDO event : sortedEvents) {

            String eventType = normalize(event.getEvent());

            switch (eventType) {

                case "THINK":
                    assembleThink(event, blocks);
                    break;

                case "TOOL_WAITING":
                    assembleToolWaiting(event, blocks, pendingToolBlocks);
                    break;

                case "TOOL_CALL":
                    assembleToolCall(event, blocks, pendingToolBlocks);
                    break;

                case "TOOL_RESULT":
                    handleToolResult(event, pendingToolBlocks, context);
                    break;

                case "ERROR":
                    AgentChatBlockVO errorBlock = assembleError(event);

                    if (errorBlock != null) {
                        blocks.add(errorBlock);
                    }
                    break;

                case "FINISH":
                    /*
                     * FINISH 不进入 Block。
                     * 最终回答来自 ChatMessage。
                     */
                    break;

                default:
                    AgentChatBlockVO unknown = assembleUnknown(event);

                    if (unknown != null) {
                        blocks.add(unknown);
                    }
            }
        }

        return blocks;
    }

    /**
     * =========================================================
     * THINK
     * =========================================================
     * <p>
     * reasoning 有内容时才生成 UI Block。
     * <p>
     * THINK 中的 toolCalls 不再转换，
     * 因为真正的 Tool Runtime Event 会在后续出现：
     * <p>
     * TOOL_WAITING / TOOL_CALL
     */
    private void assembleThink(AgentEventDO event, List<AgentChatBlockVO> blocks) {

        Map<String, Object> data = parseJson(event.getOutput());

        String reasoning = toStringValue(data.get("reasoning"));

        if (isBlank(reasoning)) {
            return;
        }

        AgentChatBlockVO block = baseBlock(event);

        block.setType("action");
        block.setAction("THINK");
        block.setStatus("completed");
        block.setSummary(reasoning);

        blocks.add(block);
    }

    /**
     * =========================================================
     * TOOL_WAITING
     * =========================================================
     * <p>
     * Agent 已经决定调用 Tool，
     * 但 Runtime 暂时不能执行。
     * <p>
     * 当前主要场景：
     * <p>
     * 人工审批。
     */
    private void assembleToolWaiting(AgentEventDO event, List<AgentChatBlockVO> blocks, Map<String, AgentChatBlockVO> pendingToolBlocks) {

        Map<String, Object> data = parseJson(event.getOutput());

        String toolName = toStringValue(data.get("tool"));

        String action = resolveAction(toolName);

        AgentChatBlockVO block = baseBlock(event);

        block.setType("action");
        block.setAction(action);

        /*
         * 当前正在等待外部决定。
         */
        block.setStatus("waiting");

        /*
         * Action Runtime Metadata
         */

        block.setRequiresApproval(booleanValue(data.get("requiresApproval")));

        block.setSummary(buildWaitingSummary(action, toolName, data));

        blocks.add(block);

        /*
         * 后续：
         *
         * TOOL_CALL
         * TOOL_RESULT
         *
         * 都需要通过 toolCallId 找到这个 Block。
         */
        String toolCallId = extractToolCallId(event.getOutput());

        if (!isBlank(toolCallId)) {
            pendingToolBlocks.put(toolCallId, block);
        }
    }

    /**
     * =========================================================
     * TOOL_CALL
     * =========================================================
     * <p>
     * 两种情况：
     * <p>
     * 1. 普通 Tool Call
     * 直接创建 running Block。
     * <p>
     * 2. 前面已经存在 TOOL_WAITING
     * 更新原来的 waiting Block，
     * 不创建新的 Block。
     */
    private void assembleToolCall(AgentEventDO event, List<AgentChatBlockVO> blocks, Map<String, AgentChatBlockVO> pendingToolBlocks) {

        Map<String, Object> data = parseJson(event.getOutput());

        String toolCallId = extractToolCallId(event.getOutput());

        /*
         * =====================================================
         * 情况 1：
         * 前面已经存在 TOOL_WAITING
         * =====================================================
         */
        AgentChatBlockVO existingBlock = pendingToolBlocks.get(toolCallId);

        if (existingBlock != null) {

            String toolName = toStringValue(data.get("tool"));

            String action = resolveAction(toolName);

            existingBlock.setAction(action);
            existingBlock.setStatus("running");

            /*
             * 一般情况下如果前面的 TOOL_WAITING 已经是 true，
             * 这里不会覆盖成 false。
             */
            if (data.containsKey("requiresApproval")) {
                existingBlock.setRequiresApproval(booleanValue(data.get("requiresApproval")));
            }

            existingBlock.setSummary(buildRunningSummary(action, toolName, data));

            addSourceEvent(existingBlock, event);

            return;
        }

        /*
         * =====================================================
         * 情况 2：
         * 普通 TOOL_CALL
         * =====================================================
         */
        AgentChatBlockVO block = assembleToolCallBlock(event);

        if (block == null) {
            return;
        }

        blocks.add(block);

        if (!isBlank(toolCallId)) {
            pendingToolBlocks.put(toolCallId, block);
        }
    }

    /**
     * 创建一个普通 TOOL_CALL Block。
     */
    private AgentChatBlockVO assembleToolCallBlock(AgentEventDO event) {

        Map<String, Object> data = parseJson(event.getOutput());

        String toolName = toStringValue(data.get("tool"));

        String action = resolveAction(toolName);

        AgentChatBlockVO block = baseBlock(event);

        block.setType("action");
        block.setAction(action);
        block.setStatus("running");

        block.setActionId(event.getActionId());

        block.setRequiresApproval(booleanValue(data.get("requiresApproval")));

        block.setSummary(buildRunningSummary(action, toolName, data));

        return block;
    }

    /**
     * =========================================================
     * TOOL_RESULT
     * =========================================================
     */
    private void handleToolResult(AgentEventDO event, Map<String, AgentChatBlockVO> pendingToolBlocks, AgentChatAssembleContext context) {

        if (event == null) {
            return;
        }

        Map<String, Object> data = parseJson(event.getOutput());

        if (data == null || data.isEmpty()) {
            return;
        }

        /*
         * TOOL_RESULT 与 TOOL_CALL / TOOL_WAITING
         * 通过 toolCallId 关联。
         */
        String toolCallId = extractToolCallId(event.getOutput());

        if (isBlank(toolCallId)) {
            return;
        }

        AgentChatBlockVO block = pendingToolBlocks.get(toolCallId);

        if (block == null) {
            /*
             * 当前历史中没有对应 Tool Block。
             *
             * 第一版直接忽略。
             */
            return;
        }

        /*
         * TOOL_RESULT 到达以后，
         * 更新同一个 Block 的最终状态。
         */
        block.setStatus(resolveResultStatus(event));

        /*
         * 更新执行摘要。
         */
        block.setSummary(buildCompletedSummary(block.getAction(), data));

        /*
         * 文件修改结果。
         */
        applyFileChange(event, block, data, context);

        /*
         * 当前 Event 记录为来源事件。
         */
        addSourceEvent(block, event);
    }

    /**
     * =========================================================
     * UNKNOWN
     * =========================================================
     */
    private AgentChatBlockVO assembleUnknown(AgentEventDO event) {

        AgentChatBlockVO block = baseBlock(event);

        block.setType("action");
        block.setAction("EXECUTE");
        block.setStatus(resolveStatus(event));
        block.setSummary(safeOutput(event.getOutput()));

        return block;
    }

    /**
     * =========================================================
     * ERROR
     * =========================================================
     */
    private AgentChatBlockVO assembleError(AgentEventDO event) {

        if (event == null) {
            return null;
        }

        AgentChatBlockVO block = new AgentChatBlockVO();

        /*
         * ERROR 属于独立错误 Block。
         *
         * 不参与 TOOL_CALL / TOOL_RESULT 关联。
         */
        block.setType("error");

        block.setId(String.valueOf(event.getId()));

        block.setAgent(event.getAgentName());

        block.setTaskId(event.getTaskId());

        block.setRunId(event.getRunId());

        block.setStatus("failed");

        block.setTimestamp(event.getEventTimestamp());

        String output = event.getOutput();

        if (output != null && !output.isBlank()) {

            block.setDetail(output);

            try {

                Map<String, Object> data = parseJson(output);

                if (data != null && !data.isEmpty()) {

                    Object error = data.get("error");

                    if (error != null) {

                        block.setSummary(String.valueOf(error));

                    } else {

                        Object message = data.get("message");

                        if (message != null) {

                            block.setSummary(String.valueOf(message));

                        } else {

                            block.setSummary("Agent 执行失败");
                        }
                    }

                } else {

                    block.setSummary("Agent 执行失败");
                }

            } catch (Exception e) {

                block.setSummary("Agent 执行失败");
            }

        } else {

            block.setSummary("Agent 执行失败");
        }

        return block;
    }

    /**
     * =========================================================
     * Base Block
     * =========================================================
     */
    private AgentChatBlockVO baseBlock(AgentEventDO event) {

        AgentChatBlockVO block = new AgentChatBlockVO();

        block.setId(event.getMessageId() != null ? event.getMessageId() : String.valueOf(event.getId()));

        block.setAgent(event.getAgentName());

        block.setTaskId(event.getTaskId());

        block.setRunId(event.getRunId());

        block.setSourceEventIds(new ArrayList<>(Collections.singletonList(String.valueOf(event.getId()))));

        block.setTimestamp(event.getEventTimestamp());

        return block;
    }

    /**
     * =========================================================
     * Source Event
     * =========================================================
     */
    private void addSourceEvent(AgentChatBlockVO block, AgentEventDO event) {

        if (block == null || event == null) {
            return;
        }

        List<String> sourceEventIds = block.getSourceEventIds();

        if (sourceEventIds == null) {

            sourceEventIds = new ArrayList<>();

            block.setSourceEventIds(sourceEventIds);
        }

        String eventId = event.getId() == null ? null : String.valueOf(event.getId());

        if (eventId != null && !sourceEventIds.contains(eventId)) {

            sourceEventIds.add(eventId);
        }
    }

    /**
     * =========================================================
     * Tool -> UI Action
     * =========================================================
     */
    private String resolveAction(String toolName) {

        if (isBlank(toolName)) {
            return "EXECUTE";
        }

        return switch (toolName) {

            case "list_files", "read_file" -> "READ";

            case "search_file", "search_manual" -> "SEARCH";

            case "write_file", "delete_file", "apply_patch" -> "WRITE";

            case "verify_java_syntax" -> "VERIFY";

            case "parse_java_code", "get_length" -> "EXECUTE";

            case "run_explorer", "run_fixer" -> "DELEGATE";

            default -> "EXECUTE";
        };
    }

    /**
     * =========================================================
     * Waiting Summary
     * =========================================================
     */
    private String buildWaitingSummary(String action, String toolName, Map<String, Object> data) {

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

                yield isBlank(path) ? "等待确认修改文件" : "等待确认修改 " + path;
            }

            case "VERIFY" -> "等待确认执行验证";

            default -> "等待确认执行 " + safeToolName(toolName);
        };
    }

    /**
     * =========================================================
     * Running Summary
     * =========================================================
     */
    private String buildRunningSummary(String action, String toolName, Map<String, Object> data) {

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

                yield isBlank(path) ? "正在修改文件" : "正在修改 " + path;
            }

            case "VERIFY" -> "正在验证修改";

            default -> "正在执行 " + safeToolName(toolName);
        };
    }

    /**
     * =========================================================
     * Completed Summary
     * =========================================================
     */
    private String buildCompletedSummary(String action, Map<String, Object> data) {

        Object result = data.get("result");

        if (result != null) {

            String resultText = String.valueOf(result);

            if (!resultText.isBlank()) {

                return switch (action) {

                    case "READ" -> "已完成读取";

                    case "SEARCH" -> "已完成搜索";

                    case "WRITE" -> "已完成文件修改";

                    case "VERIFY" -> "验证完成";

                    default -> "操作完成";
                };
            }
        }

        return switch (action) {

            case "READ" -> "已完成读取";

            case "SEARCH" -> "已完成搜索";

            case "WRITE" -> "已完成文件修改";

            case "VERIFY" -> "验证完成";

            default -> "操作完成";
        };
    }

    /**
     * =========================================================
     * File Change
     * =========================================================
     */
    @SuppressWarnings("unchecked")
    private void applyFileChange(AgentEventDO event, AgentChatBlockVO block, Map<String, Object> data, AgentChatAssembleContext context) {

        if (event == null || block == null || data == null) {
            return;
        }

        /*
         * TOOL_RESULT 当前结构：
         *
         * {
         *   "tool": "write_file",
         *   "result": {
         *      "type": "file_change",
         *      "filePath": "...",
         *      "operation": "modified",
         *      "addedLines": 10,
         *      "removedLines": 3,
         *      "diff": "..."
         *   },
         *   "toolCallId": "..."
         * }
         */
        Object resultObject = data.get("result");

        if (!(resultObject instanceof Map<?, ?> resultMap)) {
            return;
        }

        Object type = resultMap.get("type");

        if (!"file_change".equals(String.valueOf(type))) {
            return;
        }

        FileChangeVO change = parseFileChange((Map<String, Object>) resultMap);

        if (change == null) {
            return;
        }

        /*
         * 普通 Action Block
         * -> File Change Block
         */
        block.setType("file_change");

        block.setFilePath(change.getFilePath());

        block.setOperation(change.getOperation());

        block.setAddedLines(change.getAddedLines());

        block.setRemovedLines(change.getRemovedLines());

        /*
         * 历史 Diff
         */
        if (context != null && context.getFileChanges() != null && event.getId() != null) {

            AgentFileChangeDO fileChange = context.getFileChanges().get(event.getId());

            if (fileChange != null) {

                block.setDiffId(fileChange.getDiffId());
            }
        }
    }

    private FileChangeVO parseFileChange(Map<String, Object> resultMap) {

        if (resultMap == null || resultMap.isEmpty()) {
            return null;
        }

        if (!"file_change".equals(String.valueOf(resultMap.get("type")))) {
            return null;
        }

        FileChangeVO fileChange = new FileChangeVO();

        fileChange.setType(stringValue(resultMap.get("type")));

        fileChange.setFilePath(stringValue(resultMap.get("filePath")));

        fileChange.setOperation(stringValue(resultMap.get("operation")));

        fileChange.setAddedLines(integerValue(resultMap.get("addedLines")));

        fileChange.setRemovedLines(integerValue(resultMap.get("removedLines")));

        fileChange.setDiff(stringValue(resultMap.get("diff")));

        if (isBlank(fileChange.getFilePath()) || isBlank(fileChange.getOperation())) {

            return null;
        }

        return fileChange;
    }

    /**
     * =========================================================
     * ToolCall ID
     * =========================================================
     */
    private String extractToolCallId(String output) {

        Map<String, Object> data = parseJson(output);

        return toStringValue(data.get("toolCallId"));
    }

    /**
     * =========================================================
     * Arguments
     * =========================================================
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractArguments(Map<String, Object> data) {

        Object arguments = data.get("arguments");

        if (arguments instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }

        return Collections.emptyMap();
    }

    /**
     * =========================================================
     * Result Status
     * =========================================================
     */
    private String resolveResultStatus(AgentEventDO event) {

        String status = event.getStatus();

        if ("ERROR".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status) || "FAIL".equalsIgnoreCase(status)) {

            return "failed";
        }

        /*
         * Python 用户拒绝：
         *
         * {
         *   "result": {
         *      "error_type": "USER_REJECTED"
         *   }
         * }
         */
        Map<String, Object> data = parseJson(event.getOutput());

        Object result = data.get("result");

        if (result instanceof Map<?, ?> resultMap) {

            Object errorType = resultMap.get("error_type");

            if ("USER_REJECTED".equals(String.valueOf(errorType))) {

                return "failed";
            }
        }

        return "completed";
    }

    /**
     * =========================================================
     * Generic Status
     * =========================================================
     */
    private String resolveStatus(AgentEventDO event) {

        if (isBlank(event.getStatus())) {
            return "completed";
        }

        return switch (event.getStatus().trim().toUpperCase()) {

            case "RUNNING", "EXECUTING" -> "running";

            case "BLOCKED", "WAITING" -> "waiting";

            case "ERROR", "FAILED", "FAIL" -> "failed";

            default -> "completed";
        };
    }

    /**
     * =========================================================
     * JSON
     * =========================================================
     */
    private Map<String, Object> parseJson(String output) {

        if (isBlank(output)) {
            return Collections.emptyMap();
        }

        try {
            return JSON.parseObject(output);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    /**
     * =========================================================
     * Boolean
     * =========================================================
     */
    private Boolean booleanValue(Object value) {

        if (value == null) {
            return false;
        }

        if (value instanceof Boolean bool) {
            return bool;
        }

        return Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * =========================================================
     * Common
     * =========================================================
     */
    private String normalize(String value) {

        return value == null ? "" : value.trim().toUpperCase();
    }

    private String safeOutput(String output) {

        return output == null ? "" : output;
    }

    private String safeToolName(String toolName) {

        return isBlank(toolName) ? "操作" : toolName;
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

    private String toStringValue(Object value) {

        return value == null ? null : String.valueOf(value);
    }

    private boolean isBlank(String value) {

        return value == null || value.isBlank();
    }

    private String stringValue(Object value) {

        return value == null ? null : String.valueOf(value);
    }

    private Integer integerValue(Object value) {

        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}