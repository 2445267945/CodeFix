package com.xd.model.enums;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
public enum AgentEventEnum {
    THINK(1, "THINK"),
    FINISH(2, "FINISH"),
    TOOL_CALL(3, "TOOL_CALL"),
    TOOL_WAITING(4, "TOOL_WAITING"),

    TOOL_RESULT(5, "TOOL_RESULT"),
    ERROR(6, "ERROR")
    ;

    public final Integer eventNum;
    public final String eventDesc;

    public AgentEventEnum getByEventNum(String eventNum) {
        Integer i = Integer.parseInt(eventNum);
        for (AgentEventEnum value : AgentEventEnum.values()) {
            if ((value.eventNum ^ i) == 0) {
                return value;
            }
        }
        return null;
    }

    public AgentEventEnum getEventDesc(String desc) {
        for (AgentEventEnum value : AgentEventEnum.values()) {
            if (value.eventDesc.equals(desc)) {
                return value;
            }
        }
        return null;
    }
}
