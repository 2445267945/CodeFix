package com.xd.assembler;

import com.xd.context.AgentMessageProcessContext;
import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.vo.AgentChatBlockVO;
import com.xd.model.vo.AgentChatStreamVO;
import com.xd.model.vo.FileChangeVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 实时事件 -> Agent IDE 实时流消息

 * AgentMessageDTO：
 * Python -> Java / MQ 内部通信协议
 * AgentChatStreamVO：
 * Java -> 前端 / SSE 产品展示协议
 * 本类只处理单个实时 AgentMessageDTO。
 */
@Component
public class AgentChatStreamAssembler {

    public AgentChatStreamVO assemble(AgentMessageDTO message, AgentMessageProcessContext messageProcessContext) {
        if (message == null) {
            return null;
        }
        String event = normalize(message.getEvent());
        return switch (event) {
            case "THINK" -> assembleThink(message);
            case "TOOL_CALL" -> assembleToolCall(message);
            case "TOOL_RESULT" -> assembleToolResult(message, messageProcessContext);
            case "ERROR" -> assembleError(message);
            case "FINISH" -> assembleFinish(message);
            case "CANCELLED" -> assembleCancelled(message);
            default -> assembleUnknown(message);
        };
    }

    /**
     * THINK
     * 当前 thought 才是实时 THINK 的主要展示内容。
     */
    private AgentChatStreamVO assembleThink(AgentMessageDTO message) {
        String reasoning = message.getThought();
        if (isBlank(reasoning)) {
            return null;
        }
        AgentChatBlockVO block = baseBlock(message);
        block.setType("action");
        block.setAction("THINK");
        block.setStatus("completed");
        block.setSummary(reasoning);
        return buildAppend(message, block);
    }

    /**
     * TOOL_CALL

     * output 已经是 Map，不再进行 JSON 解析。
     */
    private AgentChatStreamVO assembleToolCall(AgentMessageDTO message) {
        Map<String, Object> data = safeOutput(message);
        String toolName = toStringValue(data.get("tool"));
        String toolCallId = toStringValue(data.get("toolCallId"));
        String action = resolveAction(toolName);
        AgentChatBlockVO block = baseBlock(message);
        /*
         * TOOL_CALL 和 TOOL_RESULT
         * 使用同一个 toolCallId。
         */
        if (!isBlank(toolCallId)) {
            block.setId(toolCallId);
        }
        block.setType("action");
        block.setAction(action);
        block.setStatus("running");
        block.setSummary(buildRunningSummary(action, toolName, data));
        return buildAppend(message, block);
    }

    /**
     * TOOL_RESULT

     * 根据 toolCallId 更新之前的 Block。
     */
    private AgentChatStreamVO assembleToolResult(AgentMessageDTO message, AgentMessageProcessContext messageProcessContext) {
        Map<String, Object> data = safeOutput(message);
        String toolCallId = toStringValue(data.get("toolCallId"));
        if (isBlank(toolCallId)) {
            return null;
        }

        String toolName = toStringValue(data.get("tool"));
        AgentChatBlockVO block = baseBlock(message);
        block.setId(toolCallId);
        block.setType("action");
        if (!isBlank(toolName)) {
            block.setAction(resolveAction(toolName));
        }
        block.setStatus(resolveResultStatus(message));
        block.setSummary(buildCompletedSummary(block.getAction(), data));
        block.setSourceEventIds(new ArrayList<>(Collections.singletonList(resolveMessageId(message))));
        applyFileChange(block, data, messageProcessContext);
        return buildUpdate(message, block);
    }

    /**
     * ERROR
     */
    private AgentChatStreamVO assembleError(AgentMessageDTO message) {

        AgentChatBlockVO block = baseBlock(message);

        block.setType("review");
        block.setAction("ERROR");
        block.setStatus("failed");
        block.setLevel("error");
        block.setTitle("执行失败");
        block.setContent(buildErrorContent(message));
        block.setSummary("Agent 执行失败");

        return buildAppend(message, block);
    }

    /**
     * FINISH

     * 不直接生成 FinalAnswer。

     * Root Agent FINISH 时，
     * MQ 消费事务已经负责：

     * saveAssistantMessage()

     * 这里仅通知前端重新获取完整 Result。
     */
    private AgentChatStreamVO assembleFinish(AgentMessageDTO message) {

        return AgentChatStreamVO.builder().taskId(message.getTaskId()).runId(message.getRunId()).sessionId(message.getSessionId()).messageId(message.getMessageId()).type("RESULT_REFRESH").block(null).refreshResult(true).timestamp(resolveTimestamp(message)).build();
    }

    /**
     * CANCELLED
     */
    private AgentChatStreamVO assembleCancelled(AgentMessageDTO message) {

        AgentChatBlockVO block = baseBlock(message);

        block.setType("review");
        block.setAction("ERROR");
        block.setStatus("failed");
        block.setLevel("warning");
        block.setTitle("任务已停止");
        block.setContent(buildErrorContent(message));
        block.setSummary("Agent 执行已停止");

        return buildAppend(message, block);
    }

    /**
     * 未知 Event
     */
    private AgentChatStreamVO assembleUnknown(AgentMessageDTO message) {

        AgentChatBlockVO block = baseBlock(message);

        block.setType("action");
        block.setAction("EXECUTE");
        block.setStatus(resolveGenericStatus(message));
        block.setSummary(buildUnknownSummary(message));

        return buildAppend(message, block);
    }

    /**
     * 创建基础 Block
     */
    private AgentChatBlockVO baseBlock(AgentMessageDTO message) {

        AgentChatBlockVO block = new AgentChatBlockVO();

        block.setId(!isBlank(message.getMessageId()) ? message.getMessageId() : UUID.randomUUID().toString());

        block.setAgent(message.getAgentName());

        block.setSourceEventIds(new ArrayList<>(Collections.singletonList(resolveMessageId(message))));

        block.setTimestamp(resolveTimestamp(message));

        return block;
    }

    /**
     * BLOCK_APPEND
     */
    private AgentChatStreamVO buildAppend(AgentMessageDTO message, AgentChatBlockVO block) {

        return AgentChatStreamVO.builder().taskId(message.getTaskId()).runId(message.getRunId()).messageId(message.getMessageId()).type("BLOCK_APPEND").block(block).refreshResult(false).timestamp(resolveTimestamp(message)).build();
    }

    /**
     * BLOCK_UPDATE
     */
    private AgentChatStreamVO buildUpdate(AgentMessageDTO message, AgentChatBlockVO block) {

        return AgentChatStreamVO.builder().taskId(message.getTaskId()).runId(message.getRunId()).messageId(message.getMessageId()).type("BLOCK_UPDATE").block(block).refreshResult(false).timestamp(resolveTimestamp(message)).build();
    }

    /**
     * Python Tool -> UI Action

     * 与历史 AgentChatBlockAssembler 保持一致。
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

    /**
     * Tool 执行中的摘要。
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

            case "DELEGATE" -> "正在委派子 Agent";

            default -> "正在执行 " + safeToolName(toolName);
        };
    }

    /**
     * Tool 执行完成后的摘要。
     */
    private String buildCompletedSummary(String action, Map<String, Object> data) {

        if (action == null) {
            return "操作完成";
        }

        return switch (action) {

            case "READ" -> "已完成读取";

            case "SEARCH" -> "已完成搜索";

            case "WRITE" -> "已完成文件修改";

            case "VERIFY" -> "验证完成";

            case "DELEGATE" -> "子 Agent 执行完成";

            default -> "操作完成";
        };
    }

    /**
     * File Change
     */
    private void applyFileChange(AgentChatBlockVO block, Map<String, Object> data, AgentMessageProcessContext messageProcessContext) {

        Object resultObject = data.get("result");
        if (!(resultObject instanceof Map<?, ?> resultMap)) {
            return;
        }
        Object type = resultMap.get("type");
        if (!"file_change".equals(type)) {
            return;
        }

        FileChangeVO change = parseFileChange((Map<String, Object>) resultMap);

        if (change == null) {
            return;
        }

        block.setType(String.valueOf(type));
        block.setFilePath(change.getFilePath());
        block.setOperation(change.getOperation());
        block.setAddedLines(change.getAddedLines());
        block.setRemovedLines(change.getRemovedLines());

        block.setDiffId(messageProcessContext.getDiffId());
        // TODO 为来对当前文件操作的说明可以放这里
//        block.setDetail(
//                messageProcessContext.getDiffId()
//        );
    }

    private FileChangeVO parseFileChange(Map<String, Object> resultMap) {

        if (!"file_change".equals(resultMap.get("type"))) {
            return null;
        }

        FileChangeVO change = new FileChangeVO();

        change.setType(toStringValue(resultMap.get("type")));

        change.setFilePath(toStringValue(resultMap.get("filePath")));

        change.setOperation(toStringValue(resultMap.get("operation")));

        change.setAddedLines(toInteger(resultMap.get("addedLines")));

        change.setRemovedLines(toInteger(resultMap.get("removedLines")));

        change.setDiff(toStringValue(resultMap.get("diff")));

        return change;
    }

    /**
     * output 已经是 Map。
     */
    private Map<String, Object> safeOutput(AgentMessageDTO message) {

        return message.getOutput() == null ? Collections.emptyMap() : message.getOutput();
    }

    /**
     * arguments
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
     * Result 状态。
     */
    private String resolveResultStatus(AgentMessageDTO message) {

        String status = message.getStatus();

        if (isBlank(status)) {
            return "completed";
        }

        return switch (status.trim().toUpperCase()) {

            case "ERROR", "FAILED", "FAIL" -> "failed";

            default -> "completed";
        };
    }

    /**
     * 通用状态。
     */
    private String resolveGenericStatus(AgentMessageDTO message) {

        String status = message.getStatus();

        if (isBlank(status)) {
            return "completed";
        }

        return switch (status.trim().toUpperCase()) {

            case "THINKING", "EXECUTING", "RUNNING" -> "running";

            case "ERROR", "FAILED", "FAIL" -> "failed";

            default -> "completed";
        };
    }

    /**
     * 未知事件摘要。
     */
    private String buildUnknownSummary(AgentMessageDTO message) {

        if (!isBlank(message.getThought())) {
            return message.getThought();
        }

        if (!message.getOutput().isEmpty()) {
            return message.getOutput().toString();
        }

        return "Agent 执行中";
    }

    private String buildErrorContent(AgentMessageDTO message) {

        if (!isBlank(message.getThought())) {
            return message.getThought();
        }

        if (!message.getOutput().isEmpty()) {
            return message.getOutput().toString();
        }

        return "Agent 执行失败";
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

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String safeToolName(String toolName) {

        return isBlank(toolName) ? "操作" : toolName;
    }

    private boolean isBlank(String value) {

        return value == null || value.isBlank();
    }

    private String resolveMessageId(AgentMessageDTO message) {

        if (!isBlank(message.getMessageId())) {
            return message.getMessageId();
        }

        return UUID.randomUUID().toString();
    }

    private Long resolveTimestamp(AgentMessageDTO message) {

        return message.getTimestamp();
    }
}