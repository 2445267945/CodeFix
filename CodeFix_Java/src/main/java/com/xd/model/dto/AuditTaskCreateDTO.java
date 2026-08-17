package com.xd.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class AuditTaskCreateDTO {

    private String question;
    private String code;
    private String fileName;
    private List<CodeSmellDTO> smells;
    private String projectId;
    /**
     * 可选：
     *
     * null → 创建一个新的 Session
     *
     * 有值 → 在已有 Session 中创建新的 Task
     */
    private String sessionId;
}
