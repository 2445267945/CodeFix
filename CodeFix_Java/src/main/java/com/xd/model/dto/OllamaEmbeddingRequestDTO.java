package com.xd.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OllamaEmbeddingRequestDTO {

    private String model;

    private Object input;
}
