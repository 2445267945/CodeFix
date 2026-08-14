package com.xd.controller;

import com.xd.model.vo.AuditRequestVO;
import com.xd.model.vo.AuditResponseVO;
import com.xd.service.impl.AuditHandlerServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/audit")
public class AuditController {

    @Autowired
    private AuditHandlerServiceImpl auditService;

    @PostMapping("/analyze")
    public String analyze(@RequestBody AuditRequestVO request) {
        // 参数校验由Spring Validation自动完成
        return auditService.analyzeCode(request);
    }
}