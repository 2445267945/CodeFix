package com.xd.controller;

import com.xd.service.CodeParserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
public class JavaParserController {
    @Autowired
    private CodeParserService codeParserService;

    @PostMapping("/parse")
    public Map<String, Object> parse(@RequestBody Map<String, String> payload) {
        String code = payload.get("code");
        if (code == null || code.trim().isEmpty()) {
            return Map.of("error", "代码不能为空");
        }
        return codeParserService.parseStructure(code);
    }
}
