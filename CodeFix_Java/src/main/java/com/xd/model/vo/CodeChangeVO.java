package com.xd.model.vo;

import lombok.Data;

@Data
public class CodeChangeVO {

    private String filePath;

    private String diff;

    private String summary;

    private String status;
}