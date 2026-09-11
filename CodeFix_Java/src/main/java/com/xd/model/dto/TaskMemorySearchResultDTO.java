package com.xd.model.dto;


import com.xd.model.entity.TaskMemoryDO;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TaskMemorySearchResultDTO {
     private TaskMemoryDO memory;
     private double similarity;
}
