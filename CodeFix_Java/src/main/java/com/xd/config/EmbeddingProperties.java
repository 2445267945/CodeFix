package com.xd.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agent.embedding")
public class EmbeddingProperties {

    private String provider;

    private String model;

    private int dimension;

    private String baseUrl;
}
