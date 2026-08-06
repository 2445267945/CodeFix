package com.xd.client;

import com.xd.model.entity.AuditReport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class PythonAgentClient {

    @Autowired
    private RestTemplate restTemplate;

    public AuditReport callPythonAgent(String code) {
        Map<String, Object> request = Map.of("code", code);
        return restTemplate.postForObject("http://localhost:8000/analyze", request, AuditReport.class);
    }
}