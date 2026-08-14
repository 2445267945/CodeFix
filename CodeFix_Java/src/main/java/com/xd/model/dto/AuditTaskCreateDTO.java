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
}
