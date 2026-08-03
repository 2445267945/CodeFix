package com.xd.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.xd.model.dto.AuditResponse;
import com.xd.model.entity.CodeSmell;
import com.xd.validator.JavaSyntaxValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api")
public class ValidateController {
    @Autowired
    private JavaSyntaxValidator validator;

    @PostMapping("/validate")
    public Map<String, Object> validate(@RequestBody String code) {
        log.info("接收校验请求：" + code);
        Map<String, String> codeMap = (Map) JSON.parse(code);
        Optional<String> error = validator.validate(codeMap.get("code"));
        Map<String, Object> result = new HashMap<>();
        if (error.isPresent()) {
            result.put("valid", false);
            result.put("error", error.get());
        } else {
            result.put("valid", true);
        }
        return result;
    }

}
