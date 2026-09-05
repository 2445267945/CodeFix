//package com.xd.client;
//
//import com.xd.model.dto.AuditReportDTO;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Component;
//import org.springframework.web.client.RestTemplate;
//
//import java.util.Map;
//import java.util.UUID;
//
//@Component
//public class PythonAgentClient {
//
//    @Value("${py.url}")
//    private String pyUrl;
//
//    @Autowired
//    private RestTemplate restTemplate;
//
//    public AuditReportDTO callPythonAgent(String code) {
//        Map<String, Object> request = Map.of("code", code, "taskId", UUID.randomUUID());
//        return restTemplate.postForObject(pyUrl, request, AuditReportDTO.class);
//    }
//}