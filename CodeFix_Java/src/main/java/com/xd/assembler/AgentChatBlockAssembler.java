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

        List<AgentEventDO> sortedEvents = events.stream().filter(event -> event != null).sorted(Comparator.comparing(AgentEventDO::getEventTimestamp, Comparator.nullsLast(Long::compareTo))).toList();

        List<AgentChatBlockVO> blocks = new ArrayList<>();
        Map<String, AgentChatBlockVO> toolBlocks = new HashMap<>();

        /*
         * 当前处于「打开」状态的委派（delegate）Block。
         *
         * 子 Agent（Explorer / Fixer）的事件一定发生在
         * 父 Agent 的 TOOL_CALL(run_explorer/run_fixer)
         * 与 TOOL_RESULT 之间，
         * 因此栈顶就是子 Agent 事件的归属节点。
         */
        Deque<AgentChatBlockVO> delegateStack = new ArrayDeque<>();

        for (AgentEventDO event : sortedEvents) {
            String eventType = normalize(event.getEvent());

            switch (eventType) {
                case "THINK" -> appendBlock(event, assembleNarration(event), blocks, delegateStack);

                case "TOOL_WAITING" -> assembleToolWaiting(event, toolBlocks, blocks, delegateStack);

                case "TOOL_CALL" -> assembleToolCall(event, toolBlocks, blocks, delegateStack);

                case "TOOL_RESULT" -> assembleToolResult(event, toolBlocks, blocks, delegateStack, context);

                case "ERROR" -> appendBlock(event, assembleError(event), blocks, delegateStack);

                case "INTERRUPTED" -> appendBlock(event, assembleInterrupted(event), blocks, delegateStack);

                case "FINISH" -> {
                    // FINISH 不生成 Block。
                    // 整体 Task 的最终结果由其他逻辑负责。
                }

                default -> appendBlock(event, assembleUnknown(event), blocks, delegateStack);
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

    private void assembleToolWaiting(AgentEventDO event, Map<String, AgentChatBlockVO> toolBlocks, List<AgentChatBlockVO> blocks, Deque<AgentChatBlockVO> delegateStack) {
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
            block.setDelegateAgent(resolveDelegateAgentName(toolName));
            block.setStatus("waiting");
            block.setSummary(activityMapper.buildDelegateWaitingSummary(toolName));
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

        appendBlock(event, block, blocks, delegateStack);

        /*
         * 委派节点已打开：
         * 之后的子 Agent 事件都属于这个节点。
         */
        if (isDelegateTool(toolName)) {
            openDelegate(delegateStack, block);
        }
    }

    private void assembleToolCall(AgentEventDO event, Map<String, AgentChatBlockVO> toolBlocks, List<AgentChatBlockVO> blocks, Deque<AgentChatBlockVO> delegateStack) {
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

            /*
             * 注意：
             * 如果这里是第一次看到 TOOL_CALL，
             * 需要先把 Block 放入列表。
             */
            appendBlock(event, block, blocks, delegateStack);
        }

        block.setToolName(toolName);
        block.setArguments(extractArguments(data));
        block.setActionId(event.getActionId());
        block.setRequiresApproval(false);

        if (isDelegateTool(toolName)) {
            block.setType("delegate");
            block.setAction("DELEGATE");
            block.setDelegateAgent(resolveDelegateAgentName(toolName));
            block.setStatus("running");
            block.setSummary(activityMapper.buildDelegateRunningSummary(toolName));
            block.setContent(block.getSummary());
        } else {
            block.setType("action");
            block.setAction(action);
            block.setStatus("running");
            block.setSummary(activityMapper.buildRunningSummary(action, toolName, data));
        }

        appendSourceEvent(block, resolveEventId(event));

        /*
         * 委派节点已打开：
         * 之后的子 Agent 事件都属于这个节点。
         */
        if (isDelegateTool(toolName)) {
            openDelegate(delegateStack, block);
        }
    }

    private void assembleToolResult(AgentEventDO event, Map<String, AgentChatBlockVO> toolBlocks, List<AgentChatBlockVO> blocks, Deque<AgentChatBlockVO> delegateStack, AgentChatAssembleContext context) {
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

            appendBlock(event, block, blocks, delegateStack);
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

        /*
         * Delegate Tool 的结果：
         *
         * Python 返回：
         *
         * AgentResult
         *   └── result
         *       └── summary
         *
         * 这里不再自己解析 JSON，
         * 统一交给 AgentActivityMapper。
         */
        if (isDelegateTool(toolName) || "delegate".equalsIgnoreCase(block.getType()) || "DELEGATE".equalsIgnoreCase(block.getAction())) {

            block.setType("delegate");
            block.setAction("DELEGATE");

            if (isBlank(block.getDelegateAgent())) {
                block.setDelegateAgent(resolveDelegateAgentName(toolName));
            }

            String summary = activityMapper.buildDelegateSummary(toolName, data);

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

        /*
         * 委派结束：关闭委派节点。
         *
         * 后续 Block 不再归属到这个子 Agent。
         */
        if ("delegate".equalsIgnoreCase(block.getType())) {
            closeDelegate(delegateStack, block);
        }
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

    /**
     * 把 Block 放入最终列表。
     *
     * 主 Agent（Cando）产生的 Block 直接进入顶层列表；
     * 子 Agent（Explorer / Fixer）产生的 Block 挂到
     * 当前打开的委派节点下。
     */
    private void appendBlock(AgentEventDO event, AgentChatBlockVO block, List<AgentChatBlockVO> blocks, Deque<AgentChatBlockVO> delegateStack) {
        if (block == null) {
            return;
        }

        AgentChatBlockVO delegate = isSubAgentEvent(event) && !delegateStack.isEmpty() ? delegateStack.peek() : null;

        if (delegate == null) {
            blocks.add(block);
            return;
        }

        if (delegate.getChildren() == null) {
            delegate.setChildren(new ArrayList<>());
        }

        if (!containsIdentity(delegate.getChildren(), block)) {
            delegate.getChildren().add(block);
        }
    }

    /**
     * 事件是否由子 Agent 产生。
     */
    private boolean isSubAgentEvent(AgentEventDO event) {
        return event != null && !isBlank(event.getParentAgent());
    }

    private void openDelegate(Deque<AgentChatBlockVO> delegateStack, AgentChatBlockVO block) {
        if (block == null) {
            return;
        }

        delegateStack.removeIf(item -> item == block);

        delegateStack.push(block);
    }

    private void closeDelegate(Deque<AgentChatBlockVO> delegateStack, AgentChatBlockVO block) {
        if (block == null) {
            return;
        }

        if (delegateStack.removeIf(item -> item == block)) {
            return;
        }

        /*
         * 兜底：
         * TOOL_RESULT 重新创建了委派 Block 时，
         * 按子 Agent 名称关闭对应节点。
         */
        String delegateAgent = block.getDelegateAgent();

        if (!isBlank(delegateAgent)) {
            delegateStack.removeIf(item -> item != null && delegateAgent.equalsIgnoreCase(item.getDelegateAgent()));
        }
    }

    private boolean containsIdentity(List<AgentChatBlockVO> blocks, AgentChatBlockVO block) {
        for (AgentChatBlockVO item : blocks) {
            if (item == block) {
                return true;
            }
        }

        return false;
    }

    private boolean isDelegateTool(String toolName) {
        return "run_explorer".equalsIgnoreCase(toolName) || "run_fixer".equalsIgnoreCase(toolName);
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
        block.setParentAgent(event.getParentAgent());
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(String text) {
        if (isBlank(text)) {
            return null;
        }

        try {
            Object value = objectMapper.readValue(text, Object.class);

            if (value instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }

            if (value instanceof String nested && !nested.equals(text) && !nested.isBlank()) {
                return parseJsonMap(nested);
            }
        } catch (Exception ignored) {
        }

        return null;
    }
}
