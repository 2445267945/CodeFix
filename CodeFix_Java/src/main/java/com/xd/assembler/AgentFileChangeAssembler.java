package com.xd.assembler;

import com.alibaba.fastjson2.JSON;
import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.vo.FileChangeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class AgentFileChangeAssembler {

    public FileChangeVO parse(AgentMessageDTO message) {
        try {
            if (message == null || !"TOOL_RESULT".equalsIgnoreCase(message.getEvent())) {
                return null;
            }
            Map<String, Object> output = message.getOutput();
            if (output == null || output.isEmpty()) {
                return null;
            }
            Object resultObject = output.get("result");
            if (!(resultObject instanceof Map<?, ?> result)) {
                return null;
            }
            if (!"file_change".equalsIgnoreCase(stringValue(result.get("type")))) {
                return null;
            }
            String filePath = stringValue(result.get("filePath"));
            String operation = stringValue(result.get("operation"));
            if (isBlank(filePath) || isBlank(operation)) {
                return null;
            }
            FileChangeVO fileChange = new FileChangeVO();
            fileChange.setType(stringValue(result.get("type")));
            fileChange.setFilePath(filePath);
            fileChange.setOperation(operation);
            fileChange.setAddedLines(integerValue(result.get("addedLines")));
            fileChange.setRemovedLines(integerValue(result.get("removedLines")));
            fileChange.setDiff(stringValue(result.get("diff")));
            return fileChange;
        } catch (Exception e) {
            log.warn("FileChange解析异常: taskId={}, runId={}, messageId={}", message != null ? message.getTaskId() : null, message != null ? message.getRunId() : null, message != null ? message.getMessageId() : null, e);
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer integerValue(Object value) {
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
