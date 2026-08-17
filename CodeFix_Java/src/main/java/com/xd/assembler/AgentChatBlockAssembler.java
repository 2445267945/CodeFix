package com.xd.assembler;

import com.alibaba.fastjson2.JSON;
import com.xd.model.entity.AgentEventDO;
import com.xd.model.vo.AgentChatBlockVO;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class AgentChatBlockAssembler {

    public List<AgentChatBlockVO> assemble(List<AgentEventDO> events) {

        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }

        List<AgentEventDO> sortedEvents = events.stream()
                .sorted(Comparator
                        .comparing(
                                AgentEventDO::getEventTimestamp,
                                Comparator.nullsLast(Long::compareTo)
                        )
                )
                .toList();

        List<AgentChatBlockVO> blocks = new ArrayList<>();

        /*
         * TOOL_CALL -> TOOL_RESULT
         * 通过 toolCallId 进行关联。
         */
        Map<String, AgentChatBlockVO> pendingToolBlocks =
                new HashMap<>();

        for (AgentEventDO event : sortedEvents) {

            String eventType = normalize(event.getEvent());

            switch (eventType) {

                case "THINK":
                    assembleThink(event, blocks);
                    break;

                case "TOOL_CALL":
                    AgentChatBlockVO toolBlock =
                            assembleToolCall(event);

                    if (toolBlock != null) {
                        blocks.add(toolBlock);

                        String toolCallId =
                                extractToolCallId(event.getOutput());

                        if (!isBlank(toolCallId)) {
                            pendingToolBlocks.put(
                                    toolCallId,
                                    toolBlock
                            );
                        }
                    }
                    break;

                case "TOOL_RESULT":
                    handleToolResult(
                            event,
                            pendingToolBlocks
                    );
                    break;

                case "ERROR":
                    AgentChatBlockVO errorBlock =
                            assembleError(event);

                    if (errorBlock != null) {
                        blocks.add(errorBlock);
                    }
                    break;

                case "FINISH":
                    /*
                     * FINISH 不进入 Block。
                     * 最终回答由 ChatMessage -> FinalAnswerVO。
                     */
                    break;

                default:
                    AgentChatBlockVO unknown =
                            assembleUnknown(event);

                    if (unknown != null) {
                        blocks.add(unknown);
                    }
            }
        }

        return blocks;
    }

    /**
     * THINK：
     *
     * reasoning 有内容时才生成 UI Block。
     * THINK 中的 toolCalls 不再转换，
     * 因为后面会有真正的 TOOL_CALL Event。
     */
    private void assembleThink(
            AgentEventDO event,
            List<AgentChatBlockVO> blocks) {

        Map<String, Object> data =
                parseJson(event.getOutput());

        String reasoning =
                toStringValue(data.get("reasoning"));

        if (isBlank(reasoning)) {
            return;
        }

        AgentChatBlockVO block =
                baseBlock(event);

        block.setType("action");
        block.setAction("THINK");
        block.setStatus("completed");
        block.setSummary(reasoning);

        blocks.add(block);
    }

    /**
     * TOOL_CALL：
     *
     * 先生成 running Block，
     * 等 TOOL_RESULT 到来以后更新。
     */
    private AgentChatBlockVO assembleToolCall(
            AgentEventDO event) {

        Map<String, Object> data =
                parseJson(event.getOutput());

        String toolName =
                toStringValue(data.get("tool"));

        String action =
                resolveAction(toolName);

        AgentChatBlockVO block =
                baseBlock(event);

        block.setType("action");
        block.setAction(action);
        block.setStatus("running");

        block.setSummary(
                buildRunningSummary(
                        action,
                        toolName,
                        data
                )
        );

        return block;
    }

    /**
     * TOOL_RESULT：
     *
     * 根据 toolCallId 找到之前的 TOOL_CALL Block，
     * 将其更新为 completed / failed。
     */
    private void handleToolResult(
            AgentEventDO event,
            Map<String, AgentChatBlockVO> pendingToolBlocks) {

        Map<String, Object> data =
                parseJson(event.getOutput());

        String toolCallId =
                toStringValue(data.get("toolCallId"));

        AgentChatBlockVO block =
                pendingToolBlocks.get(toolCallId);

        if (block == null) {
            return;
        }

        // TOOL_RESULT 正常到达 => UI Block 完成
        block.setStatus(
                resolveResultStatus(event)
        );

        block.setSummary(
                buildCompletedSummary(
                        block.getAction(),
                        data
                )
        );

        List<String> sourceEventIds =
                block.getSourceEventIds();

        if (sourceEventIds == null) {
            sourceEventIds = new ArrayList<>();
            block.setSourceEventIds(sourceEventIds);
        }

        sourceEventIds.add(
                String.valueOf(event.getId())
        );

        applyFileChange(block, data);
    }

    private AgentChatBlockVO assembleError(
            AgentEventDO event) {

        AgentChatBlockVO block =
                baseBlock(event);

        block.setType("review");
        block.setAction("ERROR");
        block.setStatus("failed");
        block.setLevel("error");
        block.setTitle("执行失败");
        block.setContent(
                safeOutput(event.getOutput())
        );
        block.setSummary("Agent 执行失败");

        return block;
    }

    private AgentChatBlockVO assembleUnknown(
            AgentEventDO event) {

        AgentChatBlockVO block =
                baseBlock(event);

        block.setType("action");
        block.setAction("EXECUTE");
        block.setStatus(
                resolveStatus(event)
        );
        block.setSummary(
                safeOutput(event.getOutput())
        );

        return block;
    }

    private AgentChatBlockVO baseBlock(
            AgentEventDO event) {

        AgentChatBlockVO block =
                new AgentChatBlockVO();

        block.setId(
                event.getMessageId() != null
                        ? event.getMessageId()
                        : String.valueOf(event.getId())
        );

        block.setAgent(
                event.getAgentName()
        );

        block.setSourceEventIds(
                new ArrayList<>(
                        Collections.singletonList(
                                String.valueOf(event.getId())
                        )
                )
        );

        block.setTimestamp(
                event.getEventTimestamp()
        );

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

            case "list_files",
                    "read_file" ->
                    "READ";

            case "search_file",
                    "search_manual" ->
                    "SEARCH";

            case "write_file",
                    "delete_file" ->
                    "WRITE";

            case "verify_java_syntax" ->
                    "VERIFY";

            case "parse_java_code",
                    "get_length" ->
                    "EXECUTE";

            case "run_explorer",
                    "run_fixer" ->
                    "DELEGATE";

            default ->
                    "EXECUTE";
        };
    }

    private String buildRunningSummary(
            String action,
            String toolName,
            Map<String, Object> data) {

        Map<String, Object> arguments =
                extractArguments(data);

        return switch (action) {

            case "READ" -> {
                String path =
                        firstString(
                                arguments,
                                "path",
                                "file_name"
                        );

                yield isBlank(path)
                        ? "正在读取文件"
                        : "正在读取 " + path;
            }

            case "SEARCH" -> {
                String keyword =
                        firstString(
                                arguments,
                                "keyword",
                                "query"
                        );

                yield isBlank(keyword)
                        ? "正在搜索代码"
                        : "正在搜索 " + keyword;
            }

            case "WRITE" -> {
                String path =
                        firstString(
                                arguments,
                                "path",
                                "file_name"
                        );

                yield isBlank(path)
                        ? "正在修改文件"
                        : "正在修改 " + path;
            }

            case "VERIFY" ->
                    "正在验证修改";

            default ->
                    "正在执行 " + safeToolName(toolName);
        };
    }

    private String buildCompletedSummary(
            String action,
            Map<String, Object> data) {

        Object result = data.get("result");

        if (result != null) {

            /*
             * 第一版不把完整 JSON result 直接暴露给前端。
             * 后面根据具体 Tool 做人类可读摘要。
             */
            String resultText =
                    String.valueOf(result);

            if (!resultText.isBlank()) {
                return switch (action) {

                    case "READ" ->
                            "已完成读取";

                    case "SEARCH" ->
                            "已完成搜索";

                    case "WRITE" ->
                            "已完成文件修改";

                    case "VERIFY" ->
                            "验证完成";

                    default ->
                            "操作完成";
                };
            }
        }

        return switch (action) {

            case "READ" ->
                    "已完成读取";

            case "SEARCH" ->
                    "已完成搜索";

            case "WRITE" ->
                    "已完成文件修改";

            case "VERIFY" ->
                    "验证完成";

            default ->
                    "操作完成";
        };
    }

    /**
     * 先保留文件变化入口。
     *
     * 当前真实 Event 示例还没有给出 write/edit 的 result 格式，
     * 所以这里暂不强行解析 addedLines/diff 等字段。
     */
    private void applyFileChange(
            AgentChatBlockVO block,
            Map<String, Object> data) {

        if (!"WRITE".equals(block.getAction())) {
            return;
        }

        Object result = data.get("result");

        if (!(result instanceof Map<?, ?> resultMap)) {
            return;
        }

        Object filePath =
                resultMap.get("filePath");

        Object operation =
                resultMap.get("operation");

        Object addedLines =
                resultMap.get("addedLines");

        Object removedLines =
                resultMap.get("removedLines");

        Object diffId =
                resultMap.get("diffId");

        if (filePath != null) {
            block.setType("file_change");
            block.setFilePath(
                    String.valueOf(filePath)
            );
        }

        if (operation != null) {
            block.setOperation(
                    String.valueOf(operation)
            );
        }

        if (addedLines != null) {
            block.setAddedLines(
                    toInteger(addedLines)
            );
        }

        if (removedLines != null) {
            block.setRemovedLines(
                    toInteger(removedLines)
            );
        }

        if (diffId != null) {
            block.setDiffId(
                    String.valueOf(diffId)
            );
        }
    }

    private String extractToolCallId(
            String output) {

        Map<String, Object> data =
                parseJson(output);

        return toStringValue(
                data.get("toolCallId")
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractArguments(
            Map<String, Object> data) {

        Object arguments =
                data.get("arguments");

        if (arguments instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }

        return Collections.emptyMap();
    }

    private String resolveResultStatus(AgentEventDO event) {
        String status = event.getStatus();

        if ("ERROR".equalsIgnoreCase(status)
                || "FAILED".equalsIgnoreCase(status)
                || "FAIL".equalsIgnoreCase(status)) {
            return "failed";
        }

        return "completed";
    }

    private String resolveStatus(
            AgentEventDO event) {

        if (isBlank(event.getStatus())) {
            return "completed";
        }

        return switch (
                event.getStatus().trim().toUpperCase()) {

            case "RUNNING",
                    "EXECUTING" ->
                    "running";

            case "ERROR",
                    "FAILED",
                    "FAIL" ->
                    "failed";

            default ->
                    "completed";
        };
    }

    private Map<String, Object> parseJson(
            String output) {

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
        return value == null
                ? ""
                : value.trim().toUpperCase();
    }

    private String safeOutput(String output) {
        return output == null
                ? ""
                : output;
    }

    private String safeToolName(String toolName) {
        return isBlank(toolName)
                ? "操作"
                : toolName;
    }

    private String firstString(
            Map<String, Object> map,
            String... keys) {

        for (String key : keys) {

            Object value =
                    map.get(key);

            if (value != null
                    && !String.valueOf(value).isBlank()) {

                return String.valueOf(value);
            }
        }

        return null;
    }

    private String toStringValue(
            Object value) {

        return value == null
                ? null
                : String.valueOf(value);
    }

    private Integer toInteger(
            Object value) {

        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        try {
            return Integer.valueOf(
                    String.valueOf(value)
            );
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isBlank(
            String value) {

        return value == null
                || value.isBlank();
    }
}