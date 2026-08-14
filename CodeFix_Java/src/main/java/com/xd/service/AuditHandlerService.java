package com.xd.service;

import com.xd.model.vo.AuditRequestVO;

public interface AuditHandlerService {
    public String analyzeCode(AuditRequestVO request);
}
