package com.xd.model.vo;

import lombok.Data;

import java.util.List;

@Data
public class WorkspaceTreeVO {

    private String workspaceId;

    private String name;

    private List<WorkspaceTreeNodeVO> children;
}
