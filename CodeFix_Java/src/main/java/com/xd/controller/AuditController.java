package com.xd.controller;

import com.xd.model.dto.AuditRequest;
import com.xd.model.dto.AuditResponse;
import com.xd.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/audit")
public class AuditController {

    @Autowired
    private AuditService auditService;

    @PostMapping("/analyze")
    public AuditResponse  analyze(@RequestBody AuditRequest request) {
        // 参数校验由Spring Validation自动完成
        return auditService.analyzeCode(request);
    }
}