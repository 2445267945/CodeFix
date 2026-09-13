package com.xd.assembler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xd.context.AgentChatAssembleContext;
import com.xd.model.entity.AgentEventDO;
import com.xd.model.entity.AgentFileChangeDO;
import com.xd.model.vo.AgentChatBlockVO;
import org.springframework.stereotype.Component;

import java.util.*;

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
                    AgentChatBlockVO block = assembleNarration(event);
                    if (block != null) {
                        blocks.add(block);
                    }
                }
                case "TOOL_WAITING" -> assembleToolWaiting(event, toolBlocks, blocks);
                case "TOOL_CALL" -> assembleToolCall(event, toolBlocks, blocks);
                case "TOOL_RESULT" -> assembleToolResult(event, toolBlocks, blocks, context);
                case "ERROR" -> blocks.add(assembleError(event));
                case "INTERRUPTED" -> blocks.add(assembleInterrupted(event));
                case "FINISH" -> {
                }
                default -> blocks.add(assembleUnknown(event));
            }
        }

        return blocks;
    }

    private AgentChatBlockVO assembleNarration(AgentEventDO event) {
        Map<String, Object> data = safeOutput(event);
        String content = toStringValue(data.get("content"));

        if (isBlank(content)) {
            return null;
        }

        AgentChatBlockVO block = baseBlock(event);
        block.setType("narration");
        block.setAction("THINK");
        block.setStatus("completed");
        block.setSummary(content);
        block.setContent(content);
        return block;
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
        }

        block.setToolName(toolName);
        block.setArguments(extractArguments(data));
        block.setActionId(event.getActionId());

        if (isDelegateTool(toolName)) {
            block.setType("delegate");
            block.setAction("DELEGATE");
            block.setStatus("waiting");
            block.setSummary(buildDelegateWaitingSummary(toolName, data));
            block.setContent(block.getSummary());
        } else {
            block.setType("action");
            block.setAction(action);
            block.setStatus("waiting");
            block.setSummary(activityMapper.buildWaitingSummary(action, toolName, data));
        }

        block.setRequiresApproval(Boolean.TRUE.equals(data.get("requiresApproval")));

        if (!isBlank(toolCallId)) {
            toolBlocks.put(toolCallId, block);
        }

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

        block.setToolName(toolName);
        block.setArguments(extractArguments(data));
        block.setActionId(event.getActionId());
        block.setRequiresApproval(false);

        if (isDelegateTool(toolName)) {
            block.setType("delegate");
            block.setAction("DELEGATE");
            block.setStatus("running");
            block.setSummary(buildDelegateRunningSummary(toolName, data));
            block.setContent(block.getSummary());
        } else {
            block.setType("action");
            block.setAction(action);
            block.setStatus("running");
            block.setSummary(activityMapper.buildRunningSummary(action, toolName, data));
        }

        appendSourceEvent(block, resolveEventId(event));
    }

    private void assembleToolResult(AgentEventDO event, Map<String, AgentChatBlockVO> toolBlocks, List<AgentChatBlockVO> blocks, AgentChatAssembleContext context) {
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

            block.setType(isDelegateTool(toolName) ? "delegate" : "action");
            block.setAction(isDelegateTool(toolName) ? "DELEGATE" : action);
            block.setToolName(toolName);
            blocks.add(block);
        }

        Map<String, Object> arguments = block.getArguments();
        if (arguments == null) {
            arguments = Collections.emptyMap();
        }

        if (isBlank(block.getToolName())) {
            block.setToolName(toolName);
        }

        block.setActionId(event.getActionId());
        block.setStatus(activityMapper.resolveResultStatus(event.getStatus(), data));
        block.setRequiresApproval(false);
        appendSourceEvent(block, resolveEventId(event));

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
            block.setSummary(activityMapper.buildCompletedSummary(action, toolName, arguments, result));

            AgentFileChangeDO fileChange = resolveFileChange(event, context);
            if (fileChange != null) {
                activityMapper.applyFileChange(block, fileChange);
            }
        }

        pendingRemove(toolBlocks, toolCallId);
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

    private String buildDelegateWaitingSummary(String toolName, Map<String, Object> data) {
        String agentName = resolveDelegateAgentName(toolName);

        if ("run_explorer".equalsIgnoreCase(toolName)) {
            return "正在调用 Explorer Agent";
        }

        if ("run_fixer".equalsIgnoreCase(toolName)) {
            return "正在调用 Fixer Agent";
        }

        return "正在调用 " + agentName;
    }

    private String buildDelegateRunningSummary(String toolName, Map<String, Object> data) {
        if ("run_explorer".equalsIgnoreCase(toolName)) {
            return "Explorer Agent 正在执行";
        }

        if ("run_fixer".equalsIgnoreCase(toolName)) {
            return "Fixer Agent 正在执行";
        }

        return resolveDelegateAgentName(toolName) + " 正在执行";
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
        return "run_explorer".equalsIgnoreCase(toolName)
                || "run_fixer".equalsIgnoreCase(toolName);
    }

    private String resolveDelegateAgentName(String toolName) {
        if ("run_explorer".equalsIgnoreCase(toolName)) {
            return "Explorer";
        }

        if ("run_fixer".equalsIgnoreCase(toolName)) {
            return "Fixer";
        }

        return "Sub Agent";
    }

    private void pendingRemove(Map<String, AgentChatBlockVO> toolBlocks, String toolCallId) {
        if (!isBlank(toolCallId)) {
            toolBlocks.remove(toolCallId);
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
            return objectMapper.readValue(event.getOutput(), new TypeReference<>() {
            });
        } catch (Exception e) {
            return Collections.emptyMap();
        }
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
                return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {
                });
            } catch (Exception ignored) {
            }
        }

        return Collections.emptyMap();
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