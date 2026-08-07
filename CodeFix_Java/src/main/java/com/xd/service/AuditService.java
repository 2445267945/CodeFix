package com.xd.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xd.client.PythonAgentClient;
import com.xd.convert.AIContentConvert;
import com.xd.model.vo.AuditRequestVO;
import com.xd.model.vo.AuditResponseVO;
import com.xd.model.dto.AuditReportDTO;
import com.xd.model.dto.CodeSmellDTO;
import com.xd.util.Md5Utils;
import com.xd.validator.JavaSyntaxValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
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

    @Autowired
    private ObjectMapper objectMapper;

    public AuditResponseVO analyzeCode(AuditRequestVO request) {
        if (request.getCode().isEmpty()) return null;
        // 1. 生成MD5，查缓存
        String md5 = Md5Utils.MD5Digest(request.getCode());
        AuditResponseVO cached = cacheService.get(md5);
        if (cached != null) {
            return cached;
        }
        // 2. 调用Agent
        AuditReportDTO report = handleCode(request.getCode());
        // 3. 封装响应，存入缓存
        AuditResponseVO response = null;
        Map<String, String> map = null;
        try {
            response = aiContentConvert.buildResponse(report);
            map = objectMapper.readValue(response.getSummary(), new TypeReference<Map<String, String>>() {
            });
            response.setFixedCode(map.get("code"));
            response.setSummary(map.get("changes"));
            cacheService.put(md5, response);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException("AI调用异常：", e);
        }
        return response;
    }


    public AuditReportDTO handleCode(String originalCode) {
        // 1.语法校验
        Optional<String> syntaxError = validator.validate(originalCode);
        String question = "";
        if (syntaxError.isPresent()) {
            // 病例 A：代码有语法错误 -> 直接告诉 AI 修语法，跳过规则扫描
            question = "以下 Java 代码存在语法错误，错误信息如下：\n"
                    + syntaxError.get()
                    + "\n请直接修复语法错误，无需关注设计规范。\n代码：\n" + originalCode;
        } else {
            // 病例 B：语法正确 -> 提取规则，让 AI 修复设计问题
            List<CodeSmellDTO> smells = parserService.extractSmells(originalCode);
            question = "代码：\n" + originalCode + "\n预扫描嫌疑点：\n" + smells;
        }
        return pythonClient.callPythonAgent(question);
    }
}
