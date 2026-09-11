package com.xd.service;

import java.util.List;

/**
 * Embedding 服务。
 *
 * 负责将文本转换为向量，不关心 Task、Session、Redis 或 Context Retrieval。
 */
public interface EmbeddingService {

    /**
     * 将单条文本转换为 embedding。
     *
     * @param text 待向量化文本
     * @return embedding 向量
     */
    float[] embed(String text);

    /**
     * 批量生成 embedding。
     *
     * @param texts 待向量化文本
     * @return 与输入顺序一致的 embedding 列表
     */
    List<float[]> embed(List<String> texts);
}