package com.xd.service;

import com.xd.model.vo.AuditRequestVO;
import com.xd.model.vo.AuditResponseVO;
import com.xd.mq.MessageHandler;

public interface AuditHandlerService {
    public AuditResponseVO analyzeCode(AuditRequestVO request);
}
