package com.xd.controller;

import com.xd.model.vo.AuditRequestVO;
import com.xd.model.vo.AuditResponseVO;
import com.xd.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/audit")
public class AuditController {

    @Autowired
    private AuditService auditService;

    @PostMapping("/analyze")
    public AuditResponseVO analyze(@RequestBody AuditRequestVO request) {
        // 参数校验由Spring Validation自动完成
        return auditService.analyzeCode(request);
    }
}