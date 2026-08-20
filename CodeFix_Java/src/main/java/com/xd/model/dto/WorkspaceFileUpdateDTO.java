package com.xd.model.dto;

import lombok.Data;

@Data
public class WorkspaceFileUpdateDTO {

    private String path;

    private String content;
}