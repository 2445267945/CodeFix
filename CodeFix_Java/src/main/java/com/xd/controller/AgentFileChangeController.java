package com.xd.controller;

import com.xd.model.Result;
import com.xd.model.entity.AgentFileChangeDO;
import com.xd.model.vo.FileDiffVO;
import com.xd.service.AgentFileChangeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/diffs")
public class AgentFileChangeController {

    @Autowired
    private AgentFileChangeService agentFileChangeService;

    @GetMapping("/{diffId}")
    public Result<FileDiffVO> getDiff(@PathVariable String diffId) {
        FileDiffVO byDiffId = agentFileChangeService.getByDiffId(diffId);
        return Result.success(byDiffId);
    }
}
