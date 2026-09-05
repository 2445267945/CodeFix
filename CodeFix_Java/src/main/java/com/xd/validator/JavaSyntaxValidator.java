package com.xd.validator;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
public class JavaSyntaxValidator {

    /**
     * 校验 Java 代码语法是否正确
     *
     * @param code 待校验的 Java 代码字符串
     * @return Optional.empty() 表示校验通过；否则返回具体的错误描述（含行号）
     */
    public Optional<String> validate(String code) {
        if (code.isEmpty()) return Optional.of("代码内容为空，无法解析");
        try {
            StaticJavaParser.parse(code);
            return Optional.empty(); // 校验通过，没有错误
        } catch (ParseProblemException e) {
            String errorMsg = e.getMessage().trim();
            int problemStacktraceIdx = errorMsg.indexOf("Problem stacktrace");
            String subErrMsg = errorMsg.substring(0, problemStacktraceIdx);
            log.warn("代码有语法错误: {}", subErrMsg);
            return Optional.of("语法错误" + subErrMsg); // 返回错误详情，供重试使用
        } catch (Exception e) {
            // 4. 兜底捕获其他异常（包括 TokenMgrError 等）
            log.error("解析器发生未知异常: {}", e.getMessage(), e);
            return Optional.of("解析器内部错误: " + e.getMessage());
        }
    }

}