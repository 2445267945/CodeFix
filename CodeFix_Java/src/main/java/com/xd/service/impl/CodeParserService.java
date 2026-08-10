package com.xd.service.impl;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.*;
import com.xd.model.dto.CodeSmellDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.javaparser.ast.stmt.ForEachStmt;

@Slf4j
@Service
public class CodeParserService {

    public List<CodeSmellDTO> extractSmells(String code) {
        List<CodeSmellDTO> smells = new ArrayList<>();
        try {
            // 1. 解析源代码为抽象语法树（AST）
            CompilationUnit cu = StaticJavaParser.parse(code);

            // 2. 分别检测三类问题
            detectNPlusOne(cu, smells);
            detectTransactionalMissingRollback(cu, smells);
            detectSystemOutAndPrintStackTrace(cu, smells);

        } catch (Exception e) {
            // 如果代码有语法错误，JavaParser 会抛异常，此时返回空列表（或记录日志）
//            log.info("解析代码失败，语法有误: " + e.getMessage());
            log.info("解析代码失败，语法有误");
        }
        return smells;
    }

    // ============================================================
    // 1. 检测 N+1 查询：循环体内调用了 Mapper.select 方法
    // ============================================================
    private void detectNPlusOne(CompilationUnit cu, List<CodeSmellDTO> smells) {
        // 查找所有循环结构（for, while, do-while, foreach）
        cu.findAll(ForStmt.class).forEach(loop -> checkLoopForSelect(loop, smells));
        cu.findAll(WhileStmt.class).forEach(loop -> checkLoopForSelect(loop, smells));
        cu.findAll(DoStmt.class).forEach(loop -> checkLoopForSelect(loop, smells));
        cu.findAll(ForEachStmt.class).forEach(loop -> checkLoopForSelect(loop, smells));
    }

    private void checkLoopForSelect(Statement loop, List<CodeSmellDTO> smells) {
        // 在循环体内查找所有方法调用
        loop.findAll(MethodCallExpr.class).forEach(call -> {
            String callName = call.getNameAsString();
            // 如果方法名包含 "select"（可根据实际 Mapper 命名调整）
            if (callName.toLowerCase().contains("select")) {
                // 获取行号
                int line = call.getRange()
                        .map(range -> range.begin.line)
                        .orElse(0);
                // 构造 CodeSmell 对象
                CodeSmellDTO smell = new CodeSmellDTO();
                smell.setLineNumber(line);
                smell.setType("N+1_QUERY");
                smell.setCodeSnippet(call.toString());
                smell.setDescription("循环内执行 SQL 查询，存在 N+1 性能风险，建议改用批量查询（如 select ... in）");
                smells.add(smell);
            }
        });
    }

    // ============================================================
    // 2. 检测 @Transactional 是否缺少 rollbackFor
    // ============================================================
    private void detectTransactionalMissingRollback(CompilationUnit cu, List<CodeSmellDTO> smells) {
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            method.getAnnotationByName("Transactional").ifPresent(ann -> {
                // 检查注解参数中是否包含 rollbackFor
                boolean hasRollbackFor = ann.toString().contains("rollbackFor");
                if (!hasRollbackFor) {
                    int line = method.getRange()
                            .map(range -> range.begin.line)
                            .orElse(0);
                    CodeSmellDTO smell = new CodeSmellDTO();
                    smell.setLineNumber(line);
                    smell.setType("TRANSACTION_MISUSE");
                    smell.setCodeSnippet("@Transactional");
                    smell.setDescription("@Transactional 未指定 rollbackFor，运行时异常可能无法回滚，建议加上 rollbackFor = Exception.class");
                    smells.add(smell);
                }
            });
        });
    }

    // ============================================================
    // 3. 检测 System.out.println / e.printStackTrace
    // ============================================================
    private void detectSystemOutAndPrintStackTrace(CompilationUnit cu, List<CodeSmellDTO> smells) {
        // 查找所有方法调用
        cu.findAll(MethodCallExpr.class).forEach(call -> {
            String callStr = call.toString();
            // 检测 System.out.println
            if (callStr.startsWith("System.out.println")) {
                int line = call.getRange()
                        .map(range -> range.begin.line)
                        .orElse(0);
                CodeSmellDTO smell = new CodeSmellDTO();
                smell.setLineNumber(line);
                smell.setType("SYSTEM_OUT_PRINT");
                smell.setCodeSnippet(callStr);
                smell.setDescription("使用 System.out.println 输出日志，建议使用 @Slf4j + log.info()");
                smells.add(smell);
            }
            // 检测 e.printStackTrace()
            if (callStr.contains("printStackTrace")) {
                int line = call.getRange()
                        .map(range -> range.begin.line)
                        .orElse(0);
                CodeSmellDTO smell = new CodeSmellDTO();
                smell.setLineNumber(line);
                smell.setType("PRINT_STACK_TRACE");
                smell.setCodeSnippet(callStr);
                smell.setDescription("使用 e.printStackTrace()，建议改用 log.error() 输出到日志系统");
                smells.add(smell);
            }
        });
    }

    /**
     * 解析 Java 代码结构，返回包名、类名、导入、方法、循环、字段等信息。
     */
    public Map<String, Object> parseStructure(String code) {
        Map<String, Object> result = new HashMap<>();
        try {
            CompilationUnit cu = StaticJavaParser.parse(code);

            // 包名
            cu.getPackageDeclaration().ifPresent(pkg ->
                    result.put("packageName", pkg.getNameAsString())
            );

            // 类名（取第一个类）
            cu.findAll(ClassOrInterfaceDeclaration.class).stream().findFirst()
                    .ifPresent(clazz -> {
                        result.put("className", clazz.getNameAsString());
                        List<String> classAnnotations = clazz.getAnnotations().stream()
                                .map(ann -> ann.getNameAsString())
                                .collect(Collectors.toList());
                        if (!classAnnotations.isEmpty()) {
                            result.put("classAnnotations", classAnnotations);
                        }
                    });

            // 导入列表
            List<String> imports = cu.findAll(ImportDeclaration.class).stream()
                    .map(imp -> imp.getNameAsString())
                    .collect(Collectors.toList());
            if (!imports.isEmpty()) {
                result.put("imports", imports);
            }

            // 方法详情
            List<Map<String, Object>> methods = cu.findAll(MethodDeclaration.class).stream()
                    .map(method -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("name", method.getNameAsString());
                        m.put("returnType", method.getType().asString());
                        // 参数列表
                        List<String> params = method.getParameters().stream()
                                .map(p -> p.getType().asString() + " " + p.getNameAsString())
                                .collect(Collectors.toList());
                        m.put("parameters", params);
                        // 行号范围
                        method.getRange().ifPresent(range -> {
                            m.put("lineStart", range.begin.line);
                            m.put("lineEnd", range.end.line);
                        });
                        // 注解
                        List<String> annotations = method.getAnnotations().stream()
                                .map(ann -> ann.getNameAsString())
                                .collect(Collectors.toList());
                        if (!annotations.isEmpty()) {
                            m.put("annotations", annotations);
                        }
                        return m;
                    })
                    .collect(Collectors.toList());
            result.put("methods", methods);

            // 循环结构（行号标记）
            List<Map<String, Object>> loops = new ArrayList<>();
            cu.findAll(ForStmt.class).forEach(loop -> {
                Map<String, Object> l = new HashMap<>();
                l.put("type", "for");
                loop.getRange().ifPresent(r -> l.put("line", r.begin.line));
                loops.add(l);
            });
            cu.findAll(ForEachStmt.class).forEach(loop -> {
                Map<String, Object> l = new HashMap<>();
                l.put("type", "foreach");
                loop.getRange().ifPresent(r -> l.put("line", r.begin.line));
                loops.add(l);
            });
            cu.findAll(WhileStmt.class).forEach(loop -> {
                Map<String, Object> l = new HashMap<>();
                l.put("type", "while");
                loop.getRange().ifPresent(r -> l.put("line", r.begin.line));
                loops.add(l);
            });
            cu.findAll(DoStmt.class).forEach(loop -> {
                Map<String, Object> l = new HashMap<>();
                l.put("type", "do-while");
                loop.getRange().ifPresent(r -> l.put("line", r.begin.line));
                loops.add(l);
            });
            if (!loops.isEmpty()) {
                result.put("loops", loops);
            }

            // 字段信息（可选）
            List<Map<String, Object>> fields = cu.findAll(FieldDeclaration.class).stream()
                    .flatMap(field -> field.getVariables().stream().map(var -> {
                        Map<String, Object> f = new HashMap<>();
                        f.put("name", var.getNameAsString());
                        f.put("type", field.getCommonType().asString());
                        var.getRange().ifPresent(r -> f.put("line", r.begin.line));
                        return f;
                    }))
                    .collect(Collectors.toList());
            if (!fields.isEmpty()) {
                result.put("fields", fields);
            }

        } catch (Exception e) {
            log.error("解析代码结构失败: {}", e.getMessage());
            result.put("error", "解析失败: " + e.getMessage());
        }
        return result;
    }


}