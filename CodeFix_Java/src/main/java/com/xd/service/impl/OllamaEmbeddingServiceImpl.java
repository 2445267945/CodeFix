package com.xd.service.impl;

import com.xd.config.EmbeddingProperties;
import com.xd.model.dto.OllamaEmbeddingRequestDTO;
import com.xd.model.dto.OllamaEmbeddingResponseDTO;
import com.xd.service.EmbeddingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class OllamaEmbeddingServiceImpl implements EmbeddingService {

    @Autowired
    private EmbeddingProperties properties;

    private final RestClient restClient = RestClient.builder().build();

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Embedding text cannot be empty");
        OllamaEmbeddingRequestDTO request = new OllamaEmbeddingRequestDTO(properties.getModel(), text);
        OllamaEmbeddingResponseDTO response = restClient.post()
                .uri(properties.getBaseUrl() + "/api/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(OllamaEmbeddingResponseDTO.class);

        if (response == null || response.getEmbeddings() == null || response.getEmbeddings().isEmpty()) {
            throw new IllegalStateException("Embedding service returned empty result");
        }

        return toFloatArray(response.getEmbeddings().get(0));
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();
        OllamaEmbeddingRequestDTO request = new OllamaEmbeddingRequestDTO(properties.getModel(), texts);
        OllamaEmbeddingResponseDTO response = restClient.post()
                .uri(properties.getBaseUrl() + "/api/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(OllamaEmbeddingResponseDTO.class);

        if (response == null || response.getEmbeddings() == null) {
            throw new IllegalStateException("Embedding service returned empty result");
        }

        return response.getEmbeddings().stream().map(this::toFloatArray).toList();
    }

    private float[] toFloatArray(List<Float> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
        return result;
    }
}