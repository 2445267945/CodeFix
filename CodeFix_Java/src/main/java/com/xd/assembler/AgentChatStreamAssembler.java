package com.xd.assembler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xd.context.AgentMessageProcessContext;
import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.vo.AgentChatBlockVO;
import com.xd.model.vo.AgentChatStreamVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class AgentChatStreamAssembler {

    @Autowired
    private AgentActivityMapper activityMapper;
    @Autowired
    private ObjectMapper objectMapper;
    /**
     * toolCallId -> 当前实时 Activity Block
     */
    private final Map<String, AgentChatBlockVO> pendingToolBlocks = new HashMap<>();

    public AgentChatStreamVO assemble(AgentMessageDTO message, AgentMessageProcessContext messageProcessContext) {
        if (message == null) {
            return null;
        }

        String event = normalize(message.getEvent());

        return switch (event) {
            case "THINK" -> assembleNarration(message);
            case "TOOL_WAITING" -> assembleToolWaiting(message);
            case "TOOL_CALL" -> assembleToolCall(message);
            case "TOOL_RESULT" -> assembleToolResult(message, messageProcessContext);
            case "ERROR" -> assembleError(message);
            case "FINISH" -> assembleFinish(message);
            case "INTERRUPTED" -> assembleInterrupted(message);
            default -> assembleUnknown(message);
        };
    }

    private AgentChatStreamVO assembleNarration(AgentMessageDTO message) {
        Map<String, Object> data = safeOutput(message);
        String content = toStringValue(data.get("content"));

        if (isBlank(content)) {
            return null;
        }

        AgentChatBlockVO block = baseBlock(message);
        block.setType("narration");
        block.setAction("THINK");
        block.setStatus("completed");
        block.setSummary(content);
        block.setContent(content);

        return buildAppend(message, block);
    }

    private AgentChatStreamVO assembleToolWaiting(AgentMessageDTO message) {
        Map<String, Object> data = safeOutput(message);
        String toolName = toStringValue(data.get("tool"));
        String toolCallId = toStringValue(data.get("toolCallId"));
        String action = activityMapper.resolveAction(toolName);

        AgentChatBlockVO block = baseBlock(message);

        if (!isBlank(toolCallId)) {
            block.setId(toolCallId);
        }

        block.setToolName(toolName);
        block.setArguments(extractArguments(data));
        block.setActionId(message.getActionId());
        block.setRequiresApproval(Boolean.TRUE.equals(data.get("requiresApproval")));

        if (isDelegateTool(toolName)) {
            block.setType("delegate");
            block.setAction("DELEGATE");
            block.setStatus("waiting");
            block.setSummary(buildDelegateWaitingSummary(toolName));
            block.setContent(block.getSummary());
        } else {
            block.setType("action");
            block.setAction(action);
            block.setStatus("waiting");
            block.setSummary(activityMapper.buildWaitingSummary(action, toolName, data));
        }

        if (!isBlank(toolCallId)) {
            pendingToolBlocks.put(toolCallId, block);
        }

        return buildAppend(message, block);
    }

    private AgentChatStreamVO assembleToolCall(AgentMessageDTO message) {
        Map<String, Object> data = safeOutput(message);
        String toolName = toStringValue(data.get("tool"));
        String toolCallId = toStringValue(data.get("toolCallId"));
        String action = activityMapper.resolveAction(toolName);

        AgentChatBlockVO block = isBlank(toolCallId) ? null : pendingToolBlocks.get(toolCallId);

        if (block == null) {
            block = baseBlock(message);

            if (!isBlank(toolCallId)) {
                block.setId(toolCallId);
            }

            block.setType(isDelegateTool(toolName) ? "delegate" : "action");
            block.setAction(isDelegateTool(toolName) ? "DELEGATE" : action);

            if (!isBlank(toolCallId)) {
                pendingToolBlocks.put(toolCallId, block);
            }
        }

        block.setToolName(toolName);
        block.setArguments(extractArguments(data));
        block.setActionId(message.getActionId());
        block.setRequiresApproval(false);

        if (isDelegateTool(toolName)) {
            block.setType("delegate");
            block.setAction("DELEGATE");
            block.setStatus("running");
            block.setSummary(buildDelegateRunningSummary(toolName));
            block.setContent(block.getSummary());
        } else {
            block.setType("action");
            block.setAction(action);
            block.setStatus("running");
            block.setSummary(activityMapper.buildRunningSummary(action, toolName, data));
        }

        appendSourceEvent(block, resolveMessageId(message));

        return buildAppendOrUpdate(message, block);
    }

    private AgentChatStreamVO assembleToolResult(AgentMessageDTO message, AgentMessageProcessContext messageProcessContext) {
        Map<String, Object> data = safeOutput(message);
        String toolCallId = toStringValue(data.get("toolCallId"));

        if (isBlank(toolCallId)) {
            return null;
        }

        String toolName = toStringValue(data.get("tool"));
        String action = activityMapper.resolveAction(toolName);

        AgentChatBlockVO block = pendingToolBlocks.get(toolCallId);

        if (block == null) {
            block = baseBlock(message);
            block.setId(toolCallId);
            block.setType(isDelegateTool(toolName) ? "delegate" : "action");
            block.setAction(isDelegateTool(toolName) ? "DELEGATE" : action);
            block.setToolName(toolName);
            pendingToolBlocks.put(toolCallId, block);
        }

        Map<String, Object> arguments = block.getArguments();

        if (arguments == null) {
            arguments = Collections.emptyMap();
        }

        if (isBlank(block.getToolName())) {
            block.setToolName(toolName);
        }

        block.setStatus(activityMapper.resolveResultStatus(message.getStatus(), data));
        block.setActionId(message.getActionId());
        block.setRequiresApproval(false);

        appendSourceEvent(block, resolveMessageId(message));

        if (isDelegateTool(toolName) || "delegate".equalsIgnoreCase(block.getType()) || "DELEGATE".equalsIgnoreCase(block.getAction())) {
            block.setType("delegate");
            block.setAction("DELEGATE");

            String summary = buildDelegateResultSummary(data);

            block.setSummary(summary);
            block.setContent(summary);
        } else {
            block.setType("action");
            block.setAction(action);

            Map<String, Object> result = extractResult(data);

            block.setSummary(
                    activityMapper.buildCompletedSummary(
                            action,
                            toolName,
                            arguments,
                            result
                    )
            );

            String diffId = messageProcessContext == null
                    ? null
                    : messageProcessContext.getDiffId();

            activityMapper.applyFileChange(block, data, diffId);
        }

        pendingToolBlocks.remove(toolCallId);

        return buildUpdate(message, block);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractResult(Map<String, Object> data) {
        if (data == null) {
            return Collections.emptyMap();
        }

        Object result = data.get("result");

        if (result instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }

        if (result instanceof String text && !text.isBlank()) {
            Map<String, Object> parsed = parseJsonMap(text);

            if (parsed != null) {
                return parsed;
            }
        }

        return Collections.emptyMap();
    }

    private String buildDelegateWaitingSummary(String toolName) {
        if ("run_explorer".equalsIgnoreCase(toolName)) {
            return "正在调用 Explorer Agent";
        }

        if ("run_fixer".equalsIgnoreCase(toolName)) {
            return "正在调用 Fixer Agent";
        }

        return "正在调用子 Agent";
    }

    private String buildDelegateRunningSummary(String toolName) {
        if ("run_explorer".equalsIgnoreCase(toolName)) {
            return "Explorer Agent 正在执行";
        }

        if ("run_fixer".equalsIgnoreCase(toolName)) {
            return "Fixer Agent 正在执行";
        }

        return "子 Agent 正在执行";
    }

    private String buildDelegateResultSummary(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "子 Agent 执行完成";
        }

        Object result = data.get("result");
        Map<String, Object> resultMap = null;

        if (result instanceof Map<?, ?> map) {
            resultMap = castMap(map);
        } else if (result instanceof String text && !text.isBlank()) {
            resultMap = parseJsonMap(text);

            if (resultMap == null) {
                return buildPlainDelegateSummary(text);
            }
        }

        if (resultMap == null) {
            Object summary = data.get("summary");

            if (summary != null && !String.valueOf(summary).isBlank()) {
                return String.valueOf(summary);
            }

            Object content = data.get("content");

            if (content != null && !String.valueOf(content).isBlank()) {
                return buildPlainDelegateSummary(String.valueOf(content));
            }

            return "子 Agent 执行完成";
        }

        Object summary = resultMap.get("summary");

        if (summary != null && !String.valueOf(summary).isBlank()) {
            return String.valueOf(summary);
        }

        Object content = resultMap.get("content");

        if (content != null && !String.valueOf(content).isBlank()) {
            return buildPlainDelegateSummary(String.valueOf(content));
        }

        return buildStructuredDelegateSummary(resultMap);
    }

    private String buildStructuredDelegateSummary(Map<String, Object> result) {
        Object changes = result.get("changes");

        if (changes instanceof Collection<?> collection && !collection.isEmpty()) {
            return "子 Agent 已完成处理，涉及 " + collection.size() + " 项变更";
        }

        Object riskPoints = result.get("risk_points");

        if (riskPoints instanceof Collection<?> collection && !collection.isEmpty()) {
            return "子 Agent 已完成分析，发现 " + collection.size() + " 个风险点";
        }

        Object files = result.get("files");

        if (files instanceof Collection<?> collection && !collection.isEmpty()) {
            return "子 Agent 已完成分析，涉及 " + collection.size() + " 个文件";
        }

        return "子 Agent 执行完成";
    }

    private String buildPlainDelegateSummary(String content) {
        String normalized = content.trim();

        if (normalized.length() <= 300) {
            return normalized;
        }

        return normalized.substring(0, 300) + "...";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(String text) {
        try {
            Object value = objectMapper.readValue(text, Object.class);

            if (value instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }

            if (value instanceof String nested && !nested.equals(text)) {
                return parseJsonMap(nested);
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private boolean isDelegateTool(String toolName) {
        return "run_explorer".equalsIgnoreCase(toolName) || "run_fixer".equalsIgnoreCase(toolName);
    }

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

    private AgentChatStreamVO assembleFinish(AgentMessageDTO message) {
        return AgentChatStreamVO.builder()
                .taskId(message.getTaskId())
                .runId(message.getRunId())
                .sessionId(message.getSessionId())
                .messageId(message.getMessageId())
                .type("RESULT_REFRESH")
                .block(null)
                .refreshResult(true)
                .timestamp(resolveTimestamp(message))
                .build();
    }

    private AgentChatStreamVO assembleInterrupted(AgentMessageDTO message) {
        AgentChatBlockVO block = baseBlock(message);
        block.setType("status");
        block.setStatus("cancelled");
        block.setLevel("warning");
        block.setTitle("任务已取消");
        block.setContent("用户取消了任务");
        block.setSummary("用户取消了任务");

        return buildStatusAppend(message, block);
    }

    private AgentChatStreamVO buildStatusAppend(AgentMessageDTO message, AgentChatBlockVO block) {
        return AgentChatStreamVO.builder()
                .taskId(message.getTaskId())
                .runId(message.getRunId())
                .sessionId(message.getSessionId())
                .messageId(message.getMessageId())
                .type("BLOCK_APPEND")
                .block(block)
                .refreshResult(true)
                .timestamp(resolveTimestamp(message))
                .build();
    }

    private AgentChatStreamVO assembleUnknown(AgentMessageDTO message) {
        AgentChatBlockVO block = baseBlock(message);
        block.setType("action");
        block.setAction("EXECUTE");
        block.setStatus(activityMapper.resolveGenericStatus(message.getStatus()));
        block.setSummary(buildUnknownSummary(message));

        return buildAppend(message, block);
    }

    private AgentChatBlockVO baseBlock(AgentMessageDTO message) {
        AgentChatBlockVO block = new AgentChatBlockVO();
        block.setId(!isBlank(message.getMessageId()) ? message.getMessageId() : UUID.randomUUID().toString());
        block.setAgent(message.getAgentName());
        block.setSourceEventIds(new ArrayList<>(Collections.singletonList(resolveMessageId(message))));
        block.setTimestamp(resolveTimestamp(message));
        block.setTaskId(message.getTaskId());
        block.setRunId(message.getRunId());
        block.setActionId(message.getActionId());
        return block;
    }

    private void appendSourceEvent(AgentChatBlockVO block, String eventId) {
        if (isBlank(eventId)) {
            return;
        }

        if (block.getSourceEventIds() == null) {
            block.setSourceEventIds(new ArrayList<>());
        }

        if (!block.getSourceEventIds().contains(eventId)) {
            block.getSourceEventIds().add(eventId);
        }
    }

    private Map<String, Object> safeOutput(AgentMessageDTO message) {
        return message.getOutput() == null ? Collections.emptyMap() : message.getOutput();
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

    private String buildUnknownSummary(AgentMessageDTO message) {
        if (!isBlank(message.getThought())) {
            return message.getThought();
        }

        Map<String, Object> output = safeOutput(message);
        return output.isEmpty() ? "Agent 执行中" : output.toString();
    }

    private String buildErrorContent(AgentMessageDTO message) {
        if (!isBlank(message.getThought())) {
            return message.getThought();
        }

        Map<String, Object> output = safeOutput(message);
        return output.isEmpty() ? "Agent 执行失败" : output.toString();
    }

    private String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String resolveMessageId(AgentMessageDTO message) {
        return !isBlank(message.getMessageId()) ? message.getMessageId() : UUID.randomUUID().toString();
    }

    private Long resolveTimestamp(AgentMessageDTO message) {
        return message.getTimestamp();
    }

    private AgentChatStreamVO buildAppend(AgentMessageDTO message, AgentChatBlockVO block) {
        return AgentChatStreamVO.builder()
                .taskId(message.getTaskId())
                .runId(message.getRunId())
                .sessionId(message.getSessionId())
                .messageId(message.getMessageId())
                .type("BLOCK_APPEND")
                .block(block)
                .refreshResult(false)
                .timestamp(resolveTimestamp(message))
                .build();
    }

    private AgentChatStreamVO buildAppendOrUpdate(AgentMessageDTO message, AgentChatBlockVO block) {
        return AgentChatStreamVO.builder()
                .taskId(message.getTaskId())
                .runId(message.getRunId())
                .sessionId(message.getSessionId())
                .messageId(message.getMessageId())
                .type("BLOCK_UPDATE")
                .block(block)
                .refreshResult(false)
                .timestamp(resolveTimestamp(message))
                .build();
    }

    private AgentChatStreamVO buildUpdate(AgentMessageDTO message, AgentChatBlockVO block) {
        return AgentChatStreamVO.builder()
                .taskId(message.getTaskId())
                .runId(message.getRunId())
                .sessionId(message.getSessionId())
                .messageId(message.getMessageId())
                .type("BLOCK_UPDATE")
                .block(block)
                .refreshResult(false)
                .timestamp(resolveTimestamp(message))
                .build();
    }
}