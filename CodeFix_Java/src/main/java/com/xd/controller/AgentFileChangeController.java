package com.xd.controller;

import com.xd.model.entity.AgentFileChangeDO;
import com.xd.model.vo.FileDiffVO;
import com.xd.service.AgentFileChangeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit/diffs")
public class AgentFileChangeController {

    @Autowired
    private AgentFileChangeService agentFileChangeService;

    @GetMapping("/{diffId}")
    public FileDiffVO getDiff(@PathVariable String diffId) {
        return agentFileChangeService.getByDiffId(diffId);
    }
}
