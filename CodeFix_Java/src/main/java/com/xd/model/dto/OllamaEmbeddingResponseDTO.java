package com.xd.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class OllamaEmbeddingResponseDTO {
    private String model;

    private List<List<Float>> embeddings;
}
