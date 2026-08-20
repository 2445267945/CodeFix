package com.xd.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkspaceVO {

    private String workspaceId;

    private String name;

    private String status;

    private Long createdAt;

    private Long updatedAt;
}