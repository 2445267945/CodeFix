package com.xd.assembler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xd.context.AgentChatAssembleContext;
import com.xd.model.entity.AgentEventDO;
import com.xd.model.entity.AgentFileChangeDO;
import com.xd.model.vo.AgentChatBlockVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class AgentChatBlockAssembler {

    private final AgentActivityMapper activityMapper;
    private final ObjectMapper objectMapper;

    public AgentChatBlockAssembler(AgentActivityMapper activityMapper, ObjectMapper objectMapper) {
        this.activityMapper = activityMapper;
        this.objectMapper = objectMapper;
    }

    public List<AgentChatBlockVO> assemble(List<AgentEventDO> events, AgentChatAssembleContext context) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }

        List<AgentEventDO> sortedEvents = events.stream()
                .filter(event -> event != null)
                .sorted(Comparator.comparing(AgentEventDO::getEventTimestamp, Comparator.nullsLast(Long::compareTo)))
                .toList();

        List<AgentChatBlockVO> blocks = new ArrayList<>();
        Map<String, AgentChatBlockVO> toolBlocks = new HashMap<>();

        for (AgentEventDO event : sortedEvents) {
            String eventType = normalize(event.getEvent());

            switch (eventType) {
                case "THINK" -> {
                    // THINK 是 Agent 内部推理事件，不进入产品 Activity。
                }
                case "TOOL_WAITING" -> assembleToolWaiting(event, toolBlocks, blocks);
                case "TOOL_CALL" -> assembleToolCall(event, toolBlocks, blocks);
                case "TOOL_RESULT" -> assembleToolResult(event, toolBlocks, blocks, context);
                case "ERROR" -> blocks.add(assembleError(event));
                case "INTERRUPTED" -> blocks.add(assembleInterrupted(event));
                case "FINISH" -> {
                    /*
                     * FINISH 不进入 Block。
                     * 最终回答来自 ChatMessage。
                     */
                    break;
                }
                default -> blocks.add(assembleUnknown(event));
            }
        }

        return blocks;
    }

    private AgentChatBlockVO assembleInterrupted(AgentEventDO event) {
        AgentChatBlockVO block = baseBlock(event);
        block.setType("status");
        block.setStatus("cancelled");
        block.setLevel("warning");
        block.setTitle("任务已取消");
        block.setContent("用户取消了任务");
        block.setSummary("用户取消了任务");
        return block;
    }

    private void assembleToolWaiting(AgentEventDO event, Map<String, AgentChatBlockVO> toolBlocks, List<AgentChatBlockVO> blocks) {

        Map<String, Object> data = safeOutput(event);

        String toolName = toStringValue(data.get("tool"));
        String toolCallId = toStringValue(data.get("toolCallId"));
        String action = activityMapper.resolveAction(toolName);

        AgentChatBlockVO block = baseBlock(event);

        if (!isBlank(toolCallId)) {
            block.setId(toolCallId);
            toolBlocks.put(toolCallId, block);
        }

        block.setType("action");
        block.setAction(action);
        block.setStatus("waiting");
        block.setSummary(activityMapper.buildWaitingSummary(action, toolName, data));
        block.setActionId(event.getActionId());
        block.setRequiresApproval(true);

        blocks.add(block);
    }

    private void assembleToolCall(AgentEventDO event, Map<String, AgentChatBlockVO> toolBlocks, List<AgentChatBlockVO> blocks) {
        Map<String, Object> data = safeOutput(event);
        String toolName = toStringValue(data.get("tool"));
        String toolCallId = toStringValue(data.get("toolCallId"));
        String action = activityMapper.resolveAction(toolName);

        AgentChatBlockVO block = isBlank(toolCallId) ? null : toolBlocks.get(toolCallId);

        if (block == null) {
            block = baseBlock(event);

            if (!isBlank(toolCallId)) {
                block.setId(toolCallId);
                toolBlocks.put(toolCallId, block);
            }

            blocks.add(block);
        }

        block.setType("action");
        block.setAction(action);
        block.setStatus("running");
        block.setSummary(activityMapper.buildRunningSummary(action, toolName, data));
        block.setActionId(event.getActionId());
        block.setRequiresApproval(false);
        appendSourceEvent(block, resolveEventId(event));
    }

    private void assembleToolResult(
            AgentEventDO event,
            Map<String, AgentChatBlockVO> toolBlocks,
            List<AgentChatBlockVO> blocks,
            AgentChatAssembleContext context) {

        Map<String, Object> data = safeOutput(event);
        String toolName = toStringValue(data.get("tool"));
        String toolCallId = toStringValue(data.get("toolCallId"));
        String action = activityMapper.resolveAction(toolName);

        AgentChatBlockVO block = isBlank(toolCallId) ? null : toolBlocks.get(toolCallId);

        if (block == null) {
            block = baseBlock(event);

            if (!isBlank(toolCallId)) {
                block.setId(toolCallId);
                toolBlocks.put(toolCallId, block);
            }

            block.setType("action");
            block.setAction(action);
            blocks.add(block);
        }

        block.setAction(action);
        block.setStatus(activityMapper.resolveResultStatus(event.getStatus(), data));
        block.setSummary(activityMapper.buildCompletedSummary(action, toolName, data, block));
        block.setActionId(event.getActionId());
        block.setRequiresApproval(false);
        appendSourceEvent(block, resolveEventId(event));

        AgentFileChangeDO fileChange = resolveFileChange(event, context);

        if (fileChange != null) {
            activityMapper.applyFileChange(block, fileChange);
        }
    }

    private AgentFileChangeDO resolveFileChange(AgentEventDO event, AgentChatAssembleContext context) {
        if (context == null || context.getFileChanges() == null) {
            return null;
        }

        return context.getFileChanges().get(event.getId());
    }

    private AgentChatBlockVO assembleError(AgentEventDO event) {
        AgentChatBlockVO block = baseBlock(event);
        block.setType("review");
        block.setAction("ERROR");
        block.setStatus("failed");
        block.setLevel("error");
        block.setTitle("执行失败");
        block.setContent(buildErrorContent(event));
        block.setSummary("Agent 执行失败");
        return block;
    }

    private AgentChatBlockVO assembleUnknown(AgentEventDO event) {
        AgentChatBlockVO block = baseBlock(event);
        block.setType("action");
        block.setAction("EXECUTE");
        block.setStatus(activityMapper.resolveGenericStatus(event.getStatus()));
        block.setSummary(buildUnknownSummary(event));
        return block;
    }

    private AgentChatBlockVO baseBlock(AgentEventDO event) {
        AgentChatBlockVO block = new AgentChatBlockVO();
        block.setId(event.getId() == null ? UUID.randomUUID().toString() : String.valueOf(event.getId()));
        block.setAgent(event.getAgentName());
        block.setTaskId(event.getTaskId());
        block.setRunId(event.getRunId());
        block.setActionId(event.getActionId());
        block.setTimestamp(event.getEventTimestamp());
        block.setSourceEventIds(new ArrayList<>(Collections.singletonList(resolveEventId(event))));
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

    private Map<String, Object> safeOutput(AgentEventDO event) {
        if (event == null || isBlank(event.getOutput())) {
            return Collections.emptyMap();
        }

        try {
            return objectMapper.readValue(event.getOutput(), new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private String buildUnknownSummary(AgentEventDO event) {
        Map<String, Object> output = safeOutput(event);

        if (output.isEmpty()) {
            return "Agent 执行中";
        }

        return output.toString();
    }

    private String buildErrorContent(AgentEventDO event) {
        Map<String, Object> output = safeOutput(event);

        Object error = output.get("error");

        if (error != null && !String.valueOf(error).isBlank()) {
            return String.valueOf(error);
        }

        Object message = output.get("message");

        if (message != null && !String.valueOf(message).isBlank()) {
            return String.valueOf(message);
        }

        Object result = output.get("result");

        if (result instanceof Map<?, ?> resultMap) {
            Object resultMessage = resultMap.get("message");

            if (resultMessage != null && !String.valueOf(resultMessage).isBlank()) {
                return String.valueOf(resultMessage);
            }
        }

        return "Agent 执行失败";
    }

    private String resolveEventId(AgentEventDO event) {
        return event.getId() == null ? UUID.randomUUID().toString() : String.valueOf(event.getId());
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
}