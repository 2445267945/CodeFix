package com.xd.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.*;
import com.xd.model.entity.CodeSmell;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import com.github.javaparser.ast.stmt.ForEachStmt;

@Slf4j
@Service
public class CodeParserService {

    public List<CodeSmell> extractSmells(String code) {
        List<CodeSmell> smells = new ArrayList<>();
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
    private void detectNPlusOne(CompilationUnit cu, List<CodeSmell> smells) {
        // 查找所有循环结构（for, while, do-while, foreach）
        cu.findAll(ForStmt.class).forEach(loop -> checkLoopForSelect(loop, smells));
        cu.findAll(WhileStmt.class).forEach(loop -> checkLoopForSelect(loop, smells));
        cu.findAll(DoStmt.class).forEach(loop -> checkLoopForSelect(loop, smells));
        cu.findAll(ForEachStmt.class).forEach(loop -> checkLoopForSelect(loop, smells));
    }

    private void checkLoopForSelect(Statement loop, List<CodeSmell> smells) {
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
                CodeSmell smell = new CodeSmell();
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
    private void detectTransactionalMissingRollback(CompilationUnit cu, List<CodeSmell> smells) {
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            method.getAnnotationByName("Transactional").ifPresent(ann -> {
                // 检查注解参数中是否包含 rollbackFor
                boolean hasRollbackFor = ann.toString().contains("rollbackFor");
                if (!hasRollbackFor) {
                    int line = method.getRange()
                            .map(range -> range.begin.line)
                            .orElse(0);
                    CodeSmell smell = new CodeSmell();
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
    private void detectSystemOutAndPrintStackTrace(CompilationUnit cu, List<CodeSmell> smells) {
        // 查找所有方法调用
        cu.findAll(MethodCallExpr.class).forEach(call -> {
            String callStr = call.toString();
            // 检测 System.out.println
            if (callStr.startsWith("System.out.println")) {
                int line = call.getRange()
                        .map(range -> range.begin.line)
                        .orElse(0);
                CodeSmell smell = new CodeSmell();
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
                CodeSmell smell = new CodeSmell();
                smell.setLineNumber(line);
                smell.setType("PRINT_STACK_TRACE");
                smell.setCodeSnippet(callStr);
                smell.setDescription("使用 e.printStackTrace()，建议改用 log.error() 输出到日志系统");
                smells.add(smell);
            }
        });
    }
}