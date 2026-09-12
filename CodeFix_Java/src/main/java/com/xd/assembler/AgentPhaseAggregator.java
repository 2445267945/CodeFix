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
        AgentChatPhaseVO current = null;
        for (AgentChatBlockVO activity : activities) {
            if (activity == null) {
                continue;
            }
            if (current == null) {
                current = createPhase(activity);
                phases.add(current);
            } else if (needNewPhase(current, activity)) {
                current = createPhase(activity);
                phases.add(current);
            }
            current.getActivities().add(activity);
            updatePhaseStatus(current, activity);
            updateTime(current, activity);
        }
        return phases;
    }


    /**
     * 判断是否开启新的阶段
     */
    private boolean needNewPhase(AgentChatPhaseVO phase, AgentChatBlockVO activity) {
        String lastAction = getLastAction(phase);
        String currentAction = activity.getAction();
        if (currentAction == null) {
            return false;
        }
        /*
         * VERIFY 永远作为独立阶段
         *
         * WRITE
         *   |
         * VERIFY
         */
        if ("VERIFY".equals(currentAction) && !"VERIFY".equals(lastAction)) {
            return true;
        }
        /*
         * WRITE 后面新的 WRITE
         *
         * 保持一个修改阶段
         */
        if ("WRITE".equals(currentAction) && "WRITE".equals(lastAction)) {
            return false;
        }
        /*
         * READ / SEARCH 阶段
         *
         * 连续探索保持一起
         */
        if (isExplore(lastAction) && isExplore(currentAction)) {
            return false;
        }
        /*
         * 从探索进入修改
         *
         * READ
         *  |
         * WRITE
         */
        if (isExplore(lastAction) && "WRITE".equals(currentAction)) {
            return true;
        }
        /*
         * 修改完成进入其他动作
         */
        if ("WRITE".equals(lastAction) && !"WRITE".equals(currentAction)) {
            return true;
        }
        return false;
    }


    private boolean isExplore(String action) {
        return "READ".equals(action) || "SEARCH".equals(action);
    }


    private String getLastAction(AgentChatPhaseVO phase) {
        List<AgentChatBlockVO> list = phase.getActivities();
        if (list == null || list.isEmpty()) {
            return null;
        }
        AgentChatBlockVO last = list.get(list.size() - 1);
        return last.getAction();
    }


    private AgentChatPhaseVO createPhase(AgentChatBlockVO activity) {
        AgentChatPhaseVO phase = new AgentChatPhaseVO();
        phase.setId("phase:" + activity.getId());
        phase.setSummary(buildPhaseSummary(activity));
        phase.setStatus(activity.getStatus());
        phase.setStartTime(activity.getTimestamp());
        phase.setEndTime(activity.getTimestamp());
        return phase;
    }


    private String buildPhaseSummary(AgentChatBlockVO activity) {
        return switch (activity.getAction()) {
            case "READ", "SEARCH" -> "分析代码结构";
            case "WRITE" -> "修改代码";
            case "VERIFY" -> "验证修改";
            case "DELEGATE" -> "委派 Agent";
            default -> activity.getSummary();
        };
    }


    private void updateTime(AgentChatPhaseVO phase, AgentChatBlockVO activity) {
        if (activity.getTimestamp() != null) {
            phase.setEndTime(activity.getTimestamp());
        }
    }


    private void updatePhaseStatus(AgentChatPhaseVO phase, AgentChatBlockVO activity) {
        if ("failed".equals(activity.getStatus())) {
            phase.setStatus("failed");
            return;
        }
        if ("running".equals(activity.getStatus())) {
            phase.setStatus("running");
            return;
        }
        phase.setStatus("completed");
    }
}