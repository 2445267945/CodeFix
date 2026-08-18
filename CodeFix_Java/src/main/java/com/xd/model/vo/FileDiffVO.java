package com.xd.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileDiffVO {

    private String diffId;

    private String taskId;

    private String runId;

    private String filePath;

    private String operation;

    private Integer addedLines;

    private Integer removedLines;

    private String diff;

    private Long createdAt;
}
