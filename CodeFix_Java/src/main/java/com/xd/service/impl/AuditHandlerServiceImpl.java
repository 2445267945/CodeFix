package com.xd.service.impl;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xd.convert.AIContentConvert;
import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.dto.AgentTaskMessage;
import com.xd.model.vo.AuditRequestVO;
import com.xd.model.vo.AuditResponseVO;
import com.xd.model.dto.CodeSmellDTO;
import com.xd.mq.MQProducer;
import com.xd.mq.MessageHandler;
import com.xd.service.AuditHandlerService;
import com.xd.util.Md5Utils;
import com.xd.validator.JavaSyntaxValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service("AGENT_STATUS")
public class AuditHandlerServiceImpl implements AuditHandlerService, MessageHandler {
//    @Autowired
//    private PythonAgentClient pythonClient;

    @Autowired
    private CodeParserService parserService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private AIContentConvert aiContentConvert;

    @Autowired
    private JavaSyntaxValidator validator;

    @Autowired
    private MQProducer mqProducer;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public AuditResponseVO analyzeCode(AuditRequestVO request) {
        if (request.getCode().isEmpty()) return null;
        // 1. 生成MD5，查缓存
        String md5 = Md5Utils.MD5Digest(request.getCode());
        AuditResponseVO cached = cacheService.get(md5);
        if (cached != null) {
            return cached;
        }
        // 2. 调用Agent
        String taskId = handleCode(request.getCode());
        // 3. 封装响应，存入缓存
        AuditResponseVO response = null;
        Map<String, String> map = null;
        try {
//            response = aiContentConvert.buildResponse(report);
//            map = objectMapper.readValue(response.getSummary(), new TypeReference<Map<String, String>>() {
//            });
//            response.setFixedCode(map.get("code"));
//            response.setSummary(map.get("changes"));
            cacheService.put(md5, taskId);
        } catch (Exception e) {
            throw new RuntimeException("AI调用异常：", e);
        }
        return response;
    }


    private String handleCode(String originalCode) {
        String taskId = UUID.randomUUID().toString();
        String sessionId = UUID.randomUUID().toString(); // 有登录后改成真实 session

        Optional<String> syntaxError = validator.validate(originalCode);

        AgentTaskMessage msg = new AgentTaskMessage();
        msg.setVersion("1.0");
        msg.setTimestamp(System.currentTimeMillis());
        msg.setMessageId(UUID.randomUUID().toString());
        msg.setTaskId(taskId);
        msg.setSessionId(sessionId);
        msg.setType("AGENT_TASK");   // 与 Python MESSAGE_TYPE_MAP 一致
        msg.setCode(originalCode);

        if (syntaxError.isPresent()) {
            // 病例 1：只修语法
            msg.setSmells(null);
            msg.setQuestion(
                    "以下 Java 代码存在语法错误，错误信息如下：\n"
                            + syntaxError.get()
                            + "\n请直接修复语法错误，无需关注设计规范。\n代码：\n"
                            + originalCode
            );
        } else {
            // 病例 2：带结构化预扫描
            List<CodeSmellDTO> smells = parserService.extractSmells(originalCode);
            msg.setSmells(smells);
            msg.setQuestion(buildQuestionWithSmells(originalCode, smells));
        }

        // 建议：先落库/Redis PENDING，再发 MQ
        // taskStore.savePending(taskId, sessionId);

        mqProducer.send("agent_task_topic", "*", JSON.toJSONString(msg));
        return taskId;
    }

    /** question 给人读；smells 给程序用 */
    private String buildQuestionWithSmells(String code, List<CodeSmellDTO> smells) {
        StringBuilder sb = new StringBuilder();
        sb.append("请审计并修复以下 Java 代码中的问题。\n");
        sb.append("代码：\n").append(code).append("\n");
        if (smells != null && !smells.isEmpty()) {
            sb.append("预扫描嫌疑点（仅供参考）：\n");
            for (CodeSmellDTO s : smells) {
                sb.append("- [行").append(s.getLineNumber()).append("] ")
                        .append(s.getType()).append(": ")
                        .append(s.getDescription()).append("\n");
            }
        } else {
            sb.append("预扫描未发现明确嫌疑点，请做常规规范与隐患检查。\n");
        }
        return sb.toString();
    }

    @Override
    public void handleMsg(String agentMessageJSON) {
        try {
            AgentMessageDTO messageDTO = JSON.parseObject(agentMessageJSON, AgentMessageDTO.class);
            System.out.println("==================");
            System.out.println("🧠 [Agent]：" + messageDTO.getOutput().get("content"));
        } catch (Exception e) {
            log.info("json解析失败", e);
        }
    }
}
