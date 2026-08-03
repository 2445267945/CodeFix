package com.xd.model.dto;

import com.xd.model.entity.CodeSmell;
import lombok.Data;

import java.util.List;

@Data
public class AuditRequest {

    /**
     * 完整的 Java 源代码（必填）
     */
    private String code;

    /**
     * 文件名（可选）
     */
    private String fileName;

    /**
     * Java 端预扫描的嫌疑点列表（可选）
     */
    private List<CodeSmell> smells;

    /**
     * 项目上下文（可选）
     */
//    private ProjectContext context;
}
