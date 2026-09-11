package com.xd.repository.impl;

import com.xd.model.dto.TaskMemorySearchResultDTO;
import com.xd.model.entity.TaskMemoryDO;
import com.xd.repository.TaskMemoryRepository;
import io.lettuce.core.api.sync.RediSearchCommands;
import io.lettuce.core.search.SearchReply;
import io.lettuce.core.search.arguments.SearchArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class RedisTaskMemoryRepository implements TaskMemoryRepository {

    private static final String INDEX_NAME = "agent_task_memory_idx";
    private static final String KEY_PREFIX = "agent:task-memory:";

    private static final String FIELD_TASK_ID = "taskId";
    private static final String FIELD_SESSION_ID = "sessionId";
    private static final String FIELD_WORKSPACE_ID = "workspaceId";
    private static final String FIELD_WORKSPACE_NAME = "workspaceName";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_UPDATED_AT = "updatedAt";
    private static final String FIELD_EMBEDDING = "embedding";

    private static final String FIELD_DISTANCE = "distance";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RediSearchCommands<byte[], byte[]> binaryRediSearchCommands;

    @Override
    public void save(TaskMemoryDO memory) {
        if (memory == null || memory.getEmbedding() == null || memory.getMetaData() == null || memory.getMetaData().getTaskId() == null || memory.getMetaData().getTaskId().isBlank()) {
            return;
        }
        TaskMemoryDO.MetaData metaData = memory.getMetaData();
        String key = KEY_PREFIX + metaData.getTaskId();
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            byte[] redisKey = serialize(key);
            put(connection, redisKey, FIELD_TASK_ID, metaData.getTaskId());
            put(connection, redisKey, FIELD_SESSION_ID, metaData.getSessionId());
            put(connection, redisKey, FIELD_WORKSPACE_ID, metaData.getWorkspaceId());
            put(connection, redisKey, FIELD_WORKSPACE_NAME, metaData.getWorkspaceName());
            put(connection, redisKey, FIELD_CREATED_AT, formatTime(metaData.getCreatedAt()));
            put(connection, redisKey, FIELD_UPDATED_AT, formatTime(metaData.getUpdatedAt()));
            // VECTOR 字段必须写入 FLOAT32 原始二进制数据
            connection.hashCommands().hSet(redisKey, serialize(FIELD_EMBEDDING), floatArrayToBytes(memory.getEmbedding()));
            return null;
        });
    }

    @Override
    public List<TaskMemorySearchResultDTO> search(String workspaceId, float[] queryVector, int topK) {
        if (workspaceId == null || workspaceId.isBlank() || queryVector == null || queryVector.length == 0 || topK <= 0) {
            return List.of();
        }
        byte[] queryVectorBytes = floatArrayToBytes(queryVector);
        String query = "(@workspaceId:{" + escapeTagValue(workspaceId) + "})=>[KNN " + topK + " @embedding $queryVector AS distance]";

        SearchArgs<byte[], byte[]> args = SearchArgs.<byte[], byte[]>builder()
                .param(serialize("queryVector"), queryVectorBytes)
                .returnField(serialize(FIELD_TASK_ID))
                .returnField(serialize(FIELD_SESSION_ID))
                .returnField(serialize(FIELD_WORKSPACE_ID))
                .returnField(serialize(FIELD_WORKSPACE_NAME))
                .returnField(serialize(FIELD_CREATED_AT))
                .returnField(serialize(FIELD_UPDATED_AT))
                .returnField(serialize(FIELD_DISTANCE))
                .build();

        SearchReply<byte[], byte[]> results = binaryRediSearchCommands.ftSearch(serialize(INDEX_NAME), serialize(query), args);

        List<TaskMemorySearchResultDTO> resultList = new ArrayList<>();
        for (SearchReply.SearchResult<byte[], byte[]> result : results.getResults()) {
            Map<byte[], byte[]> fields = result.getFields();
            TaskMemoryDO.MetaData metaData = TaskMemoryDO.MetaData.builder()
                    .taskId(decodeString(getField(fields, FIELD_TASK_ID)))
                    .sessionId(decodeString(getField(fields, FIELD_SESSION_ID)))
                    .workspaceId(decodeString(getField(fields, FIELD_WORKSPACE_ID)))
                    .workspaceName(decodeString(getField(fields, FIELD_WORKSPACE_NAME)))
                    .createdAt(parseTime(getField(fields, FIELD_CREATED_AT)))
                    .updatedAt(parseTime(getField(fields, FIELD_UPDATED_AT)))
                    .build();
            TaskMemoryDO memory = TaskMemoryDO.builder()
                    .metaData(metaData)
                    .build();
            double distance = parseDouble(getField(fields, FIELD_DISTANCE), 1.0);
            double score = 1.0 - distance;
            resultList.add(new TaskMemorySearchResultDTO(memory, score));
        }
        return resultList;
    }

    private byte[] getField(Map<byte[], byte[]> fields, String fieldName) {
        for (Map.Entry<byte[], byte[]> entry : fields.entrySet()) {
            if (fieldName.equals(decodeString(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Override
    public TaskMemoryDO get(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        return redisTemplate.execute((RedisCallback<TaskMemoryDO>) connection -> {
                    Map<byte[], byte[]> data = connection.hashCommands().hGetAll(serialize(KEY_PREFIX + taskId));
                    if (data == null || data.isEmpty()) {
                        return null;
                    }
                    return convert(data);
                }
        );
    }

    @Override
    public void delete(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        redisTemplate.delete(KEY_PREFIX + taskId);
    }

    private TaskMemoryDO convert(Map<byte[], byte[]> data) {
        String taskId = null;
        String sessionId = null;
        String workspaceId = null;
        String workspaceName = null;
        Long createdAt = null;
        Long updatedAt = null;
        for (Map.Entry<byte[], byte[]> entry : data.entrySet()) {
            String field = decodeString(entry.getKey());
            byte[] value = entry.getValue();
            if (FIELD_TASK_ID.equals(field)) {
                taskId = decodeString(value);
            } else if (FIELD_SESSION_ID.equals(field)) {
                sessionId = decodeString(value);
            } else if (FIELD_WORKSPACE_ID.equals(field)) {
                workspaceId = decodeString(value);
            } else if (FIELD_WORKSPACE_NAME.equals(field)) {
                workspaceName = decodeString(value);
            } else if (FIELD_CREATED_AT.equals(field)) {
                createdAt = parseTime(value);
            } else if (FIELD_UPDATED_AT.equals(field)) {
                updatedAt = parseTime(value);
            } else if (FIELD_EMBEDDING.equals(field)) {
                // embedding 是二进制 FLOAT32，不能按 UTF-8 解码
            }
        }

        TaskMemoryDO.MetaData metaData = TaskMemoryDO.MetaData.builder()
                        .taskId(taskId)
                        .sessionId(sessionId)
                        .workspaceId(workspaceId)
                        .workspaceName(workspaceName)
                        .createdAt(createdAt)
                        .updatedAt(updatedAt)
                        .build();

        return TaskMemoryDO.builder()
                .metaData(metaData)
                .build();
    }

    private void put(RedisConnection connection, byte[] redisKey, String field, String value) {
        if (value == null) {
            return;
        }
        connection.hashCommands().hSet(redisKey, serialize(field), serialize(value));
    }

    /**
     * float[] -> FLOAT32 little-endian bytes
     *
     * 例如：
     * 768 dimensions
     * 768 * 4 = 3072 bytes
     */
    private byte[] floatArrayToBytes(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : vector) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    private byte[] serialize(String value) {
        return value == null ? null : value.getBytes(StandardCharsets.UTF_8);
    }

    private String decodeString(byte[] value) {
        return value == null ? null : new String(value, StandardCharsets.UTF_8);
    }

    private Long parseTime(byte[] value) {
        if (value == null) {
            return null;
        }
        String text = decodeString(value);
        return text == null || text.isBlank() ? null : Long.parseLong(text);
    }

    private double parseDouble(byte[] value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = decodeString(value);
        return text == null || text.isBlank() ? defaultValue : Double.parseDouble(text);
    }

    private String escapeTagValue(String value) {
        return value.replace("\\", "\\\\")
                .replace("-", "\\-");
    }

    private String formatTime(Long time) {
        return time == null ? null : time.toString();
    }
}