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
     * TOOL_WAITING
     *      ↓
     * TOOL_CALL
     *      ↓
     * TOOL_RESULT
     *
     * 始终更新同一个 Block。
     */
    private final Map<String, AgentChatBlockVO> pendingToolBlocks = new HashMap<>();

    public AgentChatStreamVO assemble(
            AgentMessageDTO message,
            AgentMessageProcessContext messageProcessContext
    ) {
        if (message == null) {
            return null;
        }

        String event = normalize(message.getEvent());

        return switch (event) {
            case "THINK" -> assembleNarration(message);
            case "TOOL_WAITING" -> assembleToolWaiting(message);
            case "TOOL_CALL" -> assembleToolCall(message);
            case "TOOL_RESULT" -> assembleToolResult(
                    message,
                    messageProcessContext
            );
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

    private AgentChatStreamVO assembleToolWaiting(
            AgentMessageDTO message
    ) {
        Map<String, Object> data = safeOutput(message);

        String toolName = toStringValue(data.get("tool"));
        String toolCallId = toStringValue(data.get("toolCallId"));
        String action = activityMapper.resolveAction(toolName);

        AgentChatBlockVO block = baseBlock(message);

        if (!isBlank(toolCallId)) {
            block.setId(toolCallId);
        }

        /*
         * TOOL_WAITING 本身也可能携带 arguments。
         * 保存下来，后续 TOOL_CALL / TOOL_RESULT 继续复用。
         */
        block.setToolName(toolName);
        block.setArguments(extractArguments(data));

        block.setType("action");
        block.setAction(action);
        block.setStatus("waiting");
        block.setSummary(
                activityMapper.buildWaitingSummary(
                        action,
                        toolName,
                        data
                )
        );

        /*
         * actionId 来自 AgentMessageDTO 顶层。
         */
        block.setActionId(message.getActionId());

        block.setRequiresApproval(
                Boolean.TRUE.equals(
                        data.get("requiresApproval")
                )
        );

        if (!isBlank(toolCallId)) {
            pendingToolBlocks.put(toolCallId, block);
        }

        return buildAppend(message, block);
    }

    private AgentChatStreamVO assembleToolCall(
            AgentMessageDTO message
    ) {
        Map<String, Object> data = safeOutput(message);

        String toolName = toStringValue(data.get("tool"));
        String toolCallId = toStringValue(data.get("toolCallId"));
        String action = activityMapper.resolveAction(toolName);

        AgentChatBlockVO block = isBlank(toolCallId)
                ? null
                : pendingToolBlocks.get(toolCallId);

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

        /*
         * TOOL_CALL 是真正拥有 arguments 的事件。
         *
         * 注意：
         * 保存 arguments，而不是把整个 data 塞进去。
         */
        block.setToolName(toolName);
        block.setArguments(extractArguments(data));

        block.setType("action");
        block.setAction(action);
        block.setStatus("running");

        block.setSummary(
                activityMapper.buildRunningSummary(
                        action,
                        toolName,
                        data
                )
        );

        if (!isBlank(message.getActionId())) {
            block.setActionId(message.getActionId());
        }
        block.setRequiresApproval(false);

        appendSourceEvent(
                block,
                resolveMessageId(message)
        );

        return buildAppendOrUpdate(message, block);
    }

    private AgentChatStreamVO assembleToolResult(
            AgentMessageDTO message,
            AgentMessageProcessContext messageProcessContext
    ) {
        Map<String, Object> data = safeOutput(message);

        String toolCallId = toStringValue(
                data.get("toolCallId")
        );

        if (isBlank(toolCallId)) {
            return null;
        }

        String toolName = toStringValue(data.get("tool"));
        String action = activityMapper.resolveAction(toolName);

        System.out.println(
                "[TOOL_RESULT] tool="
                        + toolName
                        + ", data="
                        + data
        );

        /*
         * 优先使用 TOOL_WAITING / TOOL_CALL
         * 已经创建的 Block。
         */
        AgentChatBlockVO block =
                pendingToolBlocks.get(toolCallId);

        if (block == null) {
            /*
             * 异常兜底：
             * 如果当前 realtime 生命周期中没有找到
             * 对应 TOOL_WAITING / TOOL_CALL，
             * 至少创建一个 Block。
             */
            block = baseBlock(message);

            block.setId(toolCallId);
            block.setType("action");
            block.setAction(action);
            block.setToolName(toolName);

            pendingToolBlocks.put(
                    toolCallId,
                    block
            );
        }

        /*
         * TOOL_RESULT 一般没有 arguments。
         *
         * 所以这里必须使用 Block 在 TOOL_CALL
         * 阶段保存的 arguments。
         */
        Map<String, Object> arguments =
                block.getArguments();

        if (arguments == null) {
            arguments = Collections.emptyMap();
        }

        /*
         * 如果 Block 没有 toolName，
         * 再使用 TOOL_RESULT 中的 toolName 兜底。
         */
        if (isBlank(block.getToolName())) {
            block.setToolName(toolName);
        }

        block.setType("action");
        block.setAction(action);

        block.setStatus(
                activityMapper.resolveResultStatus(
                        message.getStatus(),
                        data
                )
        );

        /*
         * 使用真实 Tool Call arguments
         * 生成最终完成文案。
         */
        Map<String, Object> result = extractResult(data);

        block.setSummary(
                activityMapper.buildCompletedSummary(
                        action,
                        toolName,
                        arguments,
                        result
                )
        );

        if (!isBlank(message.getActionId())) {
            block.setActionId(
                    message.getActionId()
            );
        }

        block.setRequiresApproval(false);

        appendSourceEvent(
                block,
                resolveMessageId(message)
        );

        /*
         * 文件变更信息仍然从 TOOL_RESULT 处理。
         */
        String diffId =
                messageProcessContext == null
                        ? null
                        : messageProcessContext.getDiffId();

        activityMapper.applyFileChange(
                block,
                data,
                diffId
        );

        /*
         * 工具已经完成。
         * 后续正常情况下不会再次使用这个 toolCallId。
         */
        pendingToolBlocks.remove(toolCallId);

        return buildUpdate(message, block);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractResult(
            Map<String, Object> data
    ) {
        if (data == null) {
            return Collections.emptyMap();
        }

        Object result = data.get("result");

        if (result instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }

        return Collections.emptyMap();
    }

    private AgentChatStreamVO assembleError(
            AgentMessageDTO message
    ) {
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

    private AgentChatStreamVO assembleFinish(
            AgentMessageDTO message
    ) {
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

    private AgentChatStreamVO assembleInterrupted(
            AgentMessageDTO message
    ) {
        AgentChatBlockVO block = baseBlock(message);

        block.setType("status");
        block.setStatus("cancelled");
        block.setLevel("warning");
        block.setTitle("任务已取消");
        block.setContent("用户取消了任务");
        block.setSummary("用户取消了任务");

        return buildStatusAppend(
                message,
                block
        );
    }

    private AgentChatStreamVO buildStatusAppend(
            AgentMessageDTO message,
            AgentChatBlockVO block
    ) {
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

    private AgentChatStreamVO assembleUnknown(
            AgentMessageDTO message
    ) {
        AgentChatBlockVO block = baseBlock(message);

        block.setType("action");
        block.setAction("EXECUTE");
        block.setStatus(
                activityMapper.resolveGenericStatus(
                        message.getStatus()
                )
        );
        block.setSummary(
                buildUnknownSummary(message)
        );

        return buildAppend(message, block);
    }

    private AgentChatBlockVO baseBlock(
            AgentMessageDTO message
    ) {
        AgentChatBlockVO block =
                new AgentChatBlockVO();

        block.setId(
                !isBlank(message.getMessageId())
                        ? message.getMessageId()
                        : UUID.randomUUID().toString()
        );

        block.setAgent(
                message.getAgentName()
        );

        block.setSourceEventIds(
                new ArrayList<>(
                        Collections.singletonList(
                                resolveMessageId(message)
                        )
                )
        );

        block.setTimestamp(
                resolveTimestamp(message)
        );

        block.setTaskId(
                message.getTaskId()
        );

        block.setRunId(
                message.getRunId()
        );

        block.setActionId(
                message.getActionId()
        );

        return block;
    }

    private void appendSourceEvent(
            AgentChatBlockVO block,
            String eventId
    ) {
        if (isBlank(eventId)) {
            return;
        }

        if (block.getSourceEventIds() == null) {
            block.setSourceEventIds(
                    new ArrayList<>()
            );
        }

        if (!block.getSourceEventIds().contains(eventId)) {
            block.getSourceEventIds().add(eventId);
        }
    }

    private Map<String, Object> safeOutput(
            AgentMessageDTO message
    ) {
        return message.getOutput() == null
                ? Collections.emptyMap()
                : message.getOutput();
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

        return Collections.emptyMap();
    }

    private String buildUnknownSummary(
            AgentMessageDTO message
    ) {
        if (!isBlank(message.getThought())) {
            return message.getThought();
        }

        Map<String, Object> output =
                safeOutput(message);

        return output.isEmpty()
                ? "Agent 执行中"
                : output.toString();
    }

    private String buildErrorContent(
            AgentMessageDTO message
    ) {
        if (!isBlank(message.getThought())) {
            return message.getThought();
        }

        Map<String, Object> output =
                safeOutput(message);

        return output.isEmpty()
                ? "Agent 执行失败"
                : output.toString();
    }

    private String toStringValue(Object value) {
        return value == null
                ? null
                : String.valueOf(value);
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toUpperCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String resolveMessageId(
            AgentMessageDTO message
    ) {
        return !isBlank(message.getMessageId())
                ? message.getMessageId()
                : UUID.randomUUID().toString();
    }

    private Long resolveTimestamp(
            AgentMessageDTO message
    ) {
        return message.getTimestamp();
    }

    private AgentChatStreamVO buildAppend(
            AgentMessageDTO message,
            AgentChatBlockVO block
    ) {
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

    /**
     * TOOL_CALL 正常情况下复用 TOOL_WAITING 创建的 Block。
     *
     * TOOL_WAITING:
     *      BLOCK_APPEND
     *
     * TOOL_CALL:
     *      BLOCK_UPDATE
     *
     * TOOL_RESULT:
     *      BLOCK_UPDATE
     */
    private AgentChatStreamVO buildAppendOrUpdate(
            AgentMessageDTO message,
            AgentChatBlockVO block
    ) {
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

    private AgentChatStreamVO buildUpdate(
            AgentMessageDTO message,
            AgentChatBlockVO block
    ) {
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