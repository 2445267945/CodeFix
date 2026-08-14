package com.xd.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TaskResultVO {
    private String taskId;
    private String runId;
    private String status;
    private String code;
    private String changes;
}