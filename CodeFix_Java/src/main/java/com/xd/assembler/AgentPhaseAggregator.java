package com.xd.assembler;

import com.xd.model.vo.AgentChatBlockVO;
import com.xd.model.vo.AgentChatPhaseVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AgentPhaseAggregator {

    public List<AgentChatPhaseVO> aggregate(List<AgentChatBlockVO> activities) {
        List<AgentChatPhaseVO> phases = new ArrayList<>();

        if (activities == null || activities.isEmpty()) {
            return phases;
        }

        AgentChatPhaseVO currentPhase = null;

        for (AgentChatBlockVO activity : activities) {
            if (activity == null) {
                continue;
            }

            /*
             * Narration 是 Agent 面向用户的工作说明，
             * 不负责决定 Phase。
             *
             * 它应该归属于当前 Phase。
             */
            if ("narration".equals(activity.getType())) {
                if (currentPhase == null) {
                    /*
                     * 如果第一条就是 narration，
                     * 暂时创建默认 Analysis Phase。
                     */
                    currentPhase = createPhase("ANALYSIS", activity);
                    phases.add(currentPhase);
                } else {
                    addActivity(currentPhase, activity);
                    updatePhaseStatus(currentPhase, activity);
                }
                continue;
            }

            String phaseType = resolvePhaseType(activity);

            if (currentPhase == null) {
                currentPhase = createPhase(phaseType, activity);
                phases.add(currentPhase);
            } else if (shouldStartNewPhase(currentPhase, activity, phaseType)) {
                updatePhaseEndTime(currentPhase, activity);

                currentPhase = createPhase(phaseType, activity);
                phases.add(currentPhase);
            } else {
                addActivity(currentPhase, activity);
            }

            updatePhaseStatus(currentPhase, activity);
        }

        return phases;
    }

    private AgentChatPhaseVO createPhase(String type, AgentChatBlockVO activity) {
        AgentChatPhaseVO phase = new AgentChatPhaseVO();

        phase.setId(buildPhaseId(activity));
        phase.setType(type);
        phase.setTitle(resolveTitle(type));
        phase.setStatus(resolveInitialStatus(activity));
        phase.setStartTime(activity.getTimestamp());
        phase.setEndTime(activity.getTimestamp());
        phase.getActivities().add(activity);

        return phase;
    }

    private String buildPhaseId(AgentChatBlockVO activity) {
        return "phase:" + activity.getId();
    }

    private void addActivity(
            AgentChatPhaseVO phase,
            AgentChatBlockVO activity) {

        phase.getActivities().add(activity);

        if (activity.getTimestamp() != null) {
            if (phase.getStartTime() == null) {
                phase.setStartTime(activity.getTimestamp());
            }

            phase.setEndTime(activity.getTimestamp());
        }
    }

    private boolean shouldStartNewPhase(
            AgentChatPhaseVO currentPhase,
            AgentChatBlockVO activity,
            String nextPhaseType) {

        String currentType = currentPhase.getType();

        if ("ERROR".equals(nextPhaseType)) {
            return false;
        }

        if ("APPROVAL".equals(nextPhaseType)) {
            return false;
        }

        /*
         * VERIFY -> WRITE
         *
         * 验证后再次修改，
         * 代表重新进入一个新的修复阶段。
         */
        if ("VERIFICATION".equals(currentType)
                && "IMPLEMENTATION".equals(nextPhaseType)) {
            return true;
        }

        /*
         * ANALYSIS -> IMPLEMENTATION
         */
        if ("ANALYSIS".equals(currentType)
                && "IMPLEMENTATION".equals(nextPhaseType)) {
            return true;
        }

        /*
         * ANALYSIS -> VERIFICATION
         */
        if ("ANALYSIS".equals(currentType)
                && "VERIFICATION".equals(nextPhaseType)) {
            return true;
        }

        /*
         * IMPLEMENTATION -> VERIFICATION
         */
        if ("IMPLEMENTATION".equals(currentType)
                && "VERIFICATION".equals(nextPhaseType)) {
            return true;
        }

        /*
         * ANALYSIS -> SUBTASK
         */
        if ("SUBTASK".equals(nextPhaseType)
                && !"SUBTASK".equals(currentType)) {
            return true;
        }

        /*
         * SUBTASK 完成后重新进入其他类型，
         * 创建新的 Phase。
         */
        if ("SUBTASK".equals(currentType)
                && !"SUBTASK".equals(nextPhaseType)) {
            return true;
        }

        /*
         * 相同 Phase 类型默认继续合并。
         */
        return !currentType.equals(nextPhaseType);
    }

    private String resolvePhaseType(AgentChatBlockVO activity) {
        if (activity == null) {
            return "ANALYSIS";
        }

        String action = activity.getAction();

        String status = activity.getStatus();

        if ("cancelled".equals(status)) {
            return "CANCELLED";
        }

        if ("ERROR".equals(action)
                || ("failed".equals(activity.getStatus())
                && "ERROR".equals(activity.getAction()))) {
            return "ERROR";
        }

        if ("WAITING".equals(activity.getStatus())) {
            return resolveWaitingPhase(activity);
        }

        return switch (action) {
            case "READ", "SEARCH" -> "ANALYSIS";
            case "WRITE" -> "IMPLEMENTATION";
            case "VERIFY" -> "VERIFICATION";
            case "DELEGATE" -> "SUBTASK";
            default -> "ANALYSIS";
        };
    }

    private String resolveWaitingPhase(AgentChatBlockVO activity) {
        String action = activity.getAction();

        return switch (action) {
            case "WRITE" -> "IMPLEMENTATION";
            case "VERIFY" -> "VERIFICATION";
            case "DELEGATE" -> "SUBTASK";
            default -> "ANALYSIS";
        };
    }

    private void updatePhaseStatus(
            AgentChatPhaseVO phase,
            AgentChatBlockVO activity) {

        if ("failed".equals(activity.getStatus())) {
            phase.setStatus("failed");
            return;
        }

        if ("waiting".equals(activity.getStatus())) {
            phase.setStatus("waiting");
            return;
        }

        if ("running".equals(activity.getStatus())) {
            phase.setStatus("running");
            return;
        }

        if ("completed".equals(activity.getStatus())) {
            if (!"failed".equals(phase.getStatus())
                    && !"waiting".equals(phase.getStatus())) {
                phase.setStatus("completed");
            }
        }
    }

    private void updatePhaseEndTime(
            AgentChatPhaseVO phase,
            AgentChatBlockVO activity) {

        if (activity.getTimestamp() != null) {
            phase.setEndTime(activity.getTimestamp());
        }
    }

    private String resolveInitialStatus(AgentChatBlockVO activity) {
        if ("waiting".equals(activity.getStatus())) {
            return "waiting";
        }

        if ("running".equals(activity.getStatus())) {
            return "running";
        }

        if ("failed".equals(activity.getStatus())) {
            return "failed";
        }

        return "completed";
    }

    private String resolveTitle(String type) {
        return switch (type) {
            case "ANALYSIS" -> "分析问题";
            case "IMPLEMENTATION" -> "修改代码";
            case "VERIFICATION" -> "验证修改";
            case "SUBTASK" -> "子任务";
            case "ERROR" -> "执行失败";
            default -> "Agent 工作";
        };
    }
}