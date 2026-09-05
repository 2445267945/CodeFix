package com.xd.assembler;

import com.xd.context.AgentMessageProcessContext;
import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.vo.AgentChatBlockVO;
import com.xd.model.vo.AgentChatStreamVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class AgentChatStreamAssembler {

    @Autowired
    private AgentActivityMapper activityMapper;

    /**
     * toolCallId -> 当前实时 Activity Block
     *
     * 用于：
     *
     * TOOL_WAITING
     *      ↓
     * TOOL_CALL
     *      ↓
     * TOOL_RESULT
     *
     * 始终更新同一个 Block。
     */
    private final Map<String, AgentChatBlockVO> pendingToolBlocks = new HashMap<>();

    public AgentChatStreamVO assemble(AgentMessageDTO message, AgentMessageProcessContext messageProcessContext) {
        if (message == null) {
            return null;
        }

        String event = normalize(message.getEvent());

        return switch (event) {
            case "THINK" -> null;
            case "TOOL_WAITING" -> assembleToolWaiting(message);
            case "TOOL_CALL" -> assembleToolCall(message);
            case "TOOL_RESULT" -> assembleToolResult(message, messageProcessContext);
            case "ERROR" -> assembleError(message);
            case "FINISH" -> assembleFinish(message);
            case "INTERRUPTED" -> assembleInterrupted(message);
            default -> assembleUnknown(message);
        };
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

        block.setType("action");
        block.setAction(action);
        block.setStatus("waiting");
        block.setSummary(activityMapper.buildWaitingSummary(action, toolName, data));

        /*
         * actionId 来自 AgentMessageDTO 顶层，
         * 不存在于 output。
         */
        block.setActionId(message.getActionId());

        block.setRequiresApproval(Boolean.TRUE.equals(data.get("requiresApproval")));

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

            block.setType("action");
            block.setAction(action);

            if (!isBlank(toolCallId)) {
                pendingToolBlocks.put(toolCallId, block);
            }
        }

        block.setStatus("running");
        block.setSummary(activityMapper.buildRunningSummary(action, toolName, data));
        block.setActionId(message.getActionId());
        block.setRequiresApproval(false);
        appendSourceEvent(block, resolveMessageId(message));

        return buildAppend(message, block);
    }

    private AgentChatStreamVO assembleToolResult(AgentMessageDTO message, AgentMessageProcessContext messageProcessContext) {
        Map<String, Object> data = safeOutput(message);
        String toolCallId = toStringValue(data.get("toolCallId"));
        if (isBlank(toolCallId)) {
            return null;
        }

        String toolName = toStringValue(data.get("tool"));
        String action = activityMapper.resolveAction(toolName);
        System.out.println("[TOOL_RESULT] tool=" + toolName + ", data=" + data);

        /*
         * 优先使用 TOOL_WAITING / TOOL_CALL
         * 已经创建的 Block。
         *
         * 这样可以保留：
         *
         * 正在读取 user.java
         *
         * 而不是重新创建一个空 Block。
         */
        AgentChatBlockVO block = pendingToolBlocks.get(toolCallId);
        if (block == null) {
            block = baseBlock(message);
            block.setId(toolCallId);
            pendingToolBlocks.put(toolCallId, block);
        }
        block.setType("action");
        block.setAction(action);
        block.setStatus(activityMapper.resolveResultStatus(message.getStatus(), data));

        /*
         * actionId 从 AgentMessageDTO 顶层获取。
         */
        block.setActionId(message.getActionId());
        block.setRequiresApproval(false);

        /*
         * 使用当前 Block 已经拥有的上下文生成完成摘要。
         *
         * 例如：
         *
         * 正在读取 user.java
         *      ↓
         * 已读取 user.java
         */
        block.setSummary(activityMapper.buildCompletedSummary(action, toolName, data, block));
        appendSourceEvent(block, resolveMessageId(message));
        String diffId = messageProcessContext == null ? null : messageProcessContext.getDiffId();
        activityMapper.applyFileChange(block, data, diffId);

        /*
         * 工具已经完成。
         *
         * 后续不会再有同一个 toolCallId 的正常事件，
         * 可以从 Pending Map 中移除，避免长期占用。
         */
        pendingToolBlocks.remove(toolCallId);
        return buildUpdate(message, block);
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