package com.xd.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SessionVO {

    private String sessionId;

    private String title;

    private String workspaceId;

    private Long createdAt;

    private Long updatedAt;
}