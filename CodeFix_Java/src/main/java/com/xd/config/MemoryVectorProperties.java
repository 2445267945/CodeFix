package com.xd.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agent.memory.vector")
public class MemoryVectorProperties {

    private String distanceMetric;

    private String indexName;

    private String keyPrefix;
}