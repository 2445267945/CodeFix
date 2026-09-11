package com.xd.initializer;

import com.xd.config.EmbeddingProperties;
import com.xd.config.MemoryVectorProperties;
import io.lettuce.core.RedisCommandExecutionException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class RedisTaskMemoryIndexInitializer {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private MemoryVectorProperties vectorProperties;
    @Autowired
    private EmbeddingProperties embeddingProperties;

    @PostConstruct
    public void initialize() {
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            try {
                connection.execute("FT.CREATE",
                        serialize(vectorProperties.getIndexName()),
                        serialize("ON"),
                        serialize("HASH"),
                        serialize("PREFIX"),
                        serialize("1"),
                        serialize(vectorProperties.getKeyPrefix()),
                        serialize("SCHEMA"),
                        serialize("taskId"),
                        serialize("TAG"),
                        serialize("sessionId"),
                        serialize("TAG"),
                        serialize("workspaceId"),
                        serialize("TAG"),
                        serialize("createdAt"),
                        serialize("NUMERIC"),
                        serialize("SORTABLE"),
                        serialize("updatedAt"),
                        serialize("NUMERIC"),
                        serialize("SORTABLE"),
                        serialize("embedding"),
                        serialize("VECTOR"),
                        serialize("FLAT"),
                        serialize("6"),
                        serialize("TYPE"),
                        serialize("FLOAT32"),
                        serialize("DIM"),
                        serialize(String.valueOf(embeddingProperties.getDimension())),
                        serialize("DISTANCE_METRIC"),
                        serialize(vectorProperties.getDistanceMetric()));
            } catch (Exception e) {

            }
            return null;
        });
    }

    private byte[] serialize(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}