package com.xd.assembler;

import com.alibaba.fastjson2.JSON;
import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.vo.FileChangeVO;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AgentFileChangeAssembler {

    public FileChangeVO parse(AgentMessageDTO message) {

        if (message == null) {
            return null;
        }

        /*
         * 只有 TOOL_RESULT 才可能产生真实文件变更。
         */
        if (!"TOOL_RESULT".equalsIgnoreCase(message.getEvent())) {
            return null;
        }

        Map<String, Object> output = message.getOutput();

        if (output == null || output.isEmpty()) {
            return null;
        }

        /*
         * Python 当前结构：
         *
         * {
         *   "tool": "write_file",
         *   "result": {
         *      "type": "file_change",
         *      "filePath": "...",
         *      "operation": "modified",
         *      "addedLines": 10,
         *      "removedLines": 2,
         *      "diff": "..."
         *   },
         *   "toolCallId": "..."
         * }
         */

        Object resultObject = output.get("result");

        if (resultObject == null) {
            return null;
        }

        /*
         * AgentMessageDTO 当前 output 是 Map<String,Object>。
         *
         * 由于 JSON 反序列化后 result
         * 可能已经是 Map，也可能是 JSON 字符串，
         * 这里兼容两种情况。
         */
        Map<String, Object> result;

        if (resultObject instanceof Map<?, ?> map) {

            result = map.entrySet().stream().collect(java.util.stream.Collectors.toMap(entry -> String.valueOf(entry.getKey()), Map.Entry::getValue));

        } else {

            try {
                result = JSON.parseObject(String.valueOf(resultObject));
            } catch (Exception e) {
                return null;
            }
        }

        /*
         * 当前文件变更协议的识别依据。
         */
        if (!"file_change".equals(String.valueOf(result.get("type")))) {
            return null;
        }

        FileChangeVO fileChange = new FileChangeVO();

        fileChange.setType(stringValue(result.get("type")));

        fileChange.setFilePath(stringValue(result.get("filePath")));

        fileChange.setOperation(stringValue(result.get("operation")));

        fileChange.setAddedLines(integerValue(result.get("addedLines")));

        fileChange.setRemovedLines(integerValue(result.get("removedLines")));

        fileChange.setDiff(stringValue(result.get("diff")));

        /*
         * 基础校验：
         * 文件路径和操作类型缺失，
         * 就认为不是一个有效 FileChange。
         */
        if (isBlank(fileChange.getFilePath()) || isBlank(fileChange.getOperation())) {

            return null;
        }

        return fileChange;
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
