package com.xd.model.dto;


import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ReportMetadataDTO {

    /**
     * 本次分析的唯一ID（用于日志追踪）
     */
    private String analysisId;

    /**
     * 分析总耗时（毫秒）
     */
    private Long elapsedMs;

    /**
     * Python Agent 推理轮次数（ReAct 循环次数）
     */
    private Integer agentIterations;

    /**
     * 是否命中缓存
     */
    private Boolean fromCache;

    /**
     * 分析时间
     */
    private LocalDateTime analyzedAt;

    /**
     * 代码文件的 MD5（用于缓存 Key）
     */
    private String codeMd5;
}