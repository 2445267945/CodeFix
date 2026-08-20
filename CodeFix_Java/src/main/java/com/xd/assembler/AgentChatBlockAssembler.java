package com.xd.assembler;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xd.context.AgentChatAssembleContext;
import com.xd.model.entity.AgentEventDO;
import com.xd.model.entity.AgentFileChangeDO;
import com.xd.model.vo.AgentChatBlockVO;
import com.xd.model.vo.FileChangeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class AgentChatBlockAssembler {

    @Autowired
    private ObjectMapper objectMapper;

    public List<AgentChatBlockVO> assemble(List<AgentEventDO> events, AgentChatAssembleContext context) {

        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }

        if (context == null) {
            context = AgentChatAssembleContext.builder().build();
        }

        List<AgentEventDO> sortedEvents = events.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(AgentEventDO::getEventTimestamp, Comparator.nullsLast(Long::compareTo)))
                .toList();

        List<AgentChatBlockVO> blocks = new ArrayList<>();

        /*
         * TOOL_CALL -> TOOL_RESULT
         *
         * 通过 toolCallId 关联。
         */
        Map<String, AgentChatBlockVO> pendingToolBlocks = new HashMap<>();

        for (AgentEventDO event : sortedEvents) {

            String eventType = normalize(event.getEvent());

            switch (eventType) {

                case "THINK":
                    assembleThink(event, blocks);
                    break;

                case "TOOL_CALL":
                    AgentChatBlockVO toolBlock = assembleToolCall(event);

                    if (toolBlock != null) {
                        blocks.add(toolBlock);

                        String toolCallId = extractToolCallId(event.getOutput());

                        if (!isBlank(toolCallId)) {
                            pendingToolBlocks.put(toolCallId, toolBlock);
                        }
                    }
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
     * THINK：

     * reasoning 有内容时才生成 UI Block。
     * THINK 中的 toolCalls 不再转换，
     * 因为后面会有真正的 TOOL_CALL Event。
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
     * TOOL_CALL：

     * 先生成 running Block，
     * 等 TOOL_RESULT 到来以后更新。
     */
    private AgentChatBlockVO assembleToolCall(AgentEventDO event) {

        Map<String, Object> data = parseJson(event.getOutput());

        String toolName = toStringValue(data.get("tool"));

        String action = resolveAction(toolName);

        AgentChatBlockVO block = baseBlock(event);

        block.setType("action");
        block.setAction(action);
        block.setStatus("running");

        block.setSummary(buildRunningSummary(action, toolName, data));

        return block;
    }

    private void handleToolResult(AgentEventDO event, Map<String, AgentChatBlockVO> pendingToolBlocks, AgentChatAssembleContext context) {

        if (event == null) {
            return;
        }

        Map<String, Object> data = parseJson(event.getOutput());

        if (data == null || data.isEmpty()) {
            return;
        }

        /*
         * TOOL_RESULT 与 TOOL_CALL
         * 通过 toolCallId 关联。
         */
        String toolCallId = extractToolCallId(event.getOutput());

        if (isBlank(toolCallId)) {
            return;
        }

        AgentChatBlockVO block = pendingToolBlocks.get(toolCallId);

        if (block == null) {
            return;
        }

        /*
         * TOOL_RESULT 到达后，
         * 更新原 TOOL_CALL Block 状态。
         */
        block.setStatus(resolveResultStatus(event));

        /*
         * 更新执行摘要。
         */
        block.setSummary(buildCompletedSummary(block.getAction(), data));

        /*
         * 如果 result.type == file_change，
         * 这里继续处理文件变更信息。
         *
         * context 中已经包含：
         *
         * eventId -> AgentFileChangeDO
         *
         * 可以用于历史数据回填 diffId。
         */
        applyFileChange(event, block, data, context);

        /*
         * 当前 Event 记录为来源事件。
         */
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

    private AgentChatBlockVO assembleUnknown(AgentEventDO event) {

        AgentChatBlockVO block = baseBlock(event);

        block.setType("action");
        block.setAction("EXECUTE");
        block.setStatus(resolveStatus(event));
        block.setSummary(safeOutput(event.getOutput()));

        return block;
    }

    private AgentChatBlockVO assembleError(AgentEventDO event) {

        if (event == null) {
            return null;
        }

        AgentChatBlockVO block = new AgentChatBlockVO();

        /*
         * ERROR 属于独立的错误 Block，
         * 不参与 TOOL_CALL / TOOL_RESULT 关联。
         */
        block.setType("error");

        block.setId(String.valueOf(event.getId()));

        block.setAgent(event.getAgentName());

        block.setStatus("failed");

        block.setTimestamp(event.getEventTimestamp());

        /*
         * ERROR Event 的 output 可能是：
         *
         * 1. 普通字符串
         * 2. JSON
         *
         * 当前统一先保留原始内容作为 detail。
         */
        String output = event.getOutput();

        if (output != null && !output.isBlank()) {

            block.setDetail(output);

            /*
             * 尝试从 JSON 中提取更友好的错误信息。
             */
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

    private AgentChatBlockVO baseBlock(AgentEventDO event) {

        AgentChatBlockVO block = new AgentChatBlockVO();

        block.setId(event.getMessageId() != null ? event.getMessageId() : String.valueOf(event.getId()));

        block.setAgent(event.getAgentName());

        block.setSourceEventIds(new ArrayList<>(Collections.singletonList(String.valueOf(event.getId()))));

        block.setTimestamp(event.getEventTimestamp());

        return block;
    }

    /**
     * 当前 Python Tool 名称
     * -> UI Action
     */
    private String resolveAction(String toolName) {

        if (isBlank(toolName)) {
            return "EXECUTE";
        }

        return switch (toolName) {

            case "list_files", "read_file" -> "READ";

            case "search_file", "search_manual" -> "SEARCH";

            case "write_file", "delete_file" -> "WRITE";

            case "verify_java_syntax" -> "VERIFY";

            case "parse_java_code", "get_length" -> "EXECUTE";

            case "run_explorer", "run_fixer" -> "DELEGATE";

            default -> "EXECUTE";
        };
    }

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

    private String buildCompletedSummary(String action, Map<String, Object> data) {

        Object result = data.get("result");

        if (result != null) {

            /*
             * 第一版不把完整 JSON result 直接暴露给前端。
             * 后面根据具体 Tool 做人类可读摘要。
             */
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
     * 先保留文件变化入口。

     * 当前真实 Event 示例还没有给出 write/edit 的 result 格式，
     * 所以这里暂不强行解析 addedLines/diff 等字段。
     */
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

        /*
         * file_change 类型位于 result 内部。
         */
        Object type = resultMap.get("type");

        if (!"file_change".equals(String.valueOf(type))) {
            return;
        }

        /*
         * Python Tool Result
         * -> Java FileChangeVO
         */
        FileChangeVO change = parseFileChange((Map<String, Object>) resultMap);

        if (change == null) {
            return;
        }

        /*
         * 将原来的普通 Tool Block
         * 转换成 FileChange Block。
         */
        block.setType("file_change");

        block.setFilePath(change.getFilePath());

        block.setOperation(change.getOperation());

        block.setAddedLines(change.getAddedLines());

        block.setRemovedLines(change.getRemovedLines());

        /*
         * 历史数据：
         *
         * AgentEvent.id
         *      ↓
         * AgentChatAssembleContext.fileChanges
         *      ↓
         * AgentFileChangeDO
         *      ↓
         * diffId
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

        /*
         * 当前 Python FileChangeResult：
         *
         * {
         *   "type": "file_change",
         *   "filePath": "...",
         *   "operation": "created",
         *   "addedLines": 10,
         *   "removedLines": 0,
         *   "diff": "..."
         * }
         */
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

        /*
         * 基础校验：
         * 文件路径和 operation 是必须的。
         */
        if (isBlank(fileChange.getFilePath()) || isBlank(fileChange.getOperation())) {

            return null;
        }

        return fileChange;
    }

    private String extractToolCallId(String output) {

        Map<String, Object> data = parseJson(output);

        return toStringValue(data.get("toolCallId"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractArguments(Map<String, Object> data) {

        Object arguments = data.get("arguments");

        if (arguments instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }

        return Collections.emptyMap();
    }

    private String resolveResultStatus(AgentEventDO event) {
        String status = event.getStatus();

        if ("ERROR".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status) || "FAIL".equalsIgnoreCase(status)) {
            return "failed";
        }

        return "completed";
    }

    private String resolveStatus(AgentEventDO event) {

        if (isBlank(event.getStatus())) {
            return "completed";
        }

        return switch (event.getStatus().trim().toUpperCase()) {

            case "RUNNING", "EXECUTING" -> "running";

            case "ERROR", "FAILED", "FAIL" -> "failed";

            default -> "completed";
        };
    }

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