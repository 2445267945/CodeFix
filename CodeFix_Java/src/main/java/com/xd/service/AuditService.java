package com.xd.service;

import com.xd.client.PythonAgentClient;
import com.xd.convert.AIContentConvert;
import com.xd.model.dto.AuditRequest;
import com.xd.model.dto.AuditResponse;
import com.xd.model.entity.AuditReport;
import com.xd.model.entity.CodeSmell;
import com.xd.util.Md5Utils;
import com.xd.validator.JavaSyntaxValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class AuditService {
    @Autowired
    private PythonAgentClient pythonClient;

    @Autowired
    private CodeParserService parserService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private AIContentConvert aiContentConvert;

    @Autowired
    private JavaSyntaxValidator validator;

    public AuditResponse  analyzeCode(AuditRequest request) {
        if (request.getCode().isEmpty()) return null;
        // 1. 生成MD5，查缓存
        String md5 = Md5Utils.MD5Digest(request.getCode());
        AuditResponse cached = cacheService.get(md5);
        if (cached != null) return cached;

        // 2. 调用Python Agent深度分析（含重试）
        AuditReport report = handleCode(request.getCode());
        // 3. 封装响应，存入缓存
        AuditResponse response = aiContentConvert.buildResponse(report);
        cacheService.put(md5, response);
        return response;
    }


    public AuditReport handleCode(String originalCode) {
        // 第一步：语法校验（分诊台）
        Optional<String> syntaxError = validator.validate(originalCode);
        String question = "";
        if (syntaxError.isPresent()) {
            // 病例 A：代码有语法错误 -> 直接告诉 AI 修语法，跳过规则扫描
            question = "以下 Java 代码存在语法错误，错误信息如下：\n"
                    + syntaxError.get()
                    + "\n请直接修复语法错误，无需关注设计规范。\n代码：\n" + originalCode;
        } else {
            // 病例 B：语法正确 -> 提取规则，让 AI 修复设计问题
            List<CodeSmell> smells = parserService.extractSmells(originalCode);
            question = "代码：\n" + originalCode + "\n预扫描嫌疑点：\n" + smells;
        }
        return pythonClient.callPythonAgent(question);
    }
}
