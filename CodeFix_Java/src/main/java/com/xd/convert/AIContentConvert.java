package com.xd.convert;

import com.xd.model.dto.AuditResponse;
import com.xd.model.entity.AuditReport;
import com.xd.model.entity.CodeIssue;
import com.xd.model.entity.IssueStatistics;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AIContentConvert {

    public AuditResponse buildResponse(AuditReport report) {
        AuditResponse response = new AuditResponse();

        // 1. 防御性处理：如果报告为空，返回错误响应
        if (report == null) {
            response.setCode(-1);
            response.setMessage("分析报告为空，请检查 Python 服务是否正常运行");
            return response;
        }

        // 2. 状态码映射：将字符串状态转为前端友好的整数状态码
        String status = report.getStatus();
        if ("success".equalsIgnoreCase(status)) {
            response.setCode(0);
            response.setMessage("分析完成，已自动生成优化代码");
        } else if ("partial".equalsIgnoreCase(status)) {
            response.setCode(1);
            response.setMessage("分析完成，但自动修复可能存在语法错误，请参考建议手动修改");
        } else { // error 或未知状态
            response.setCode(-1);
            response.setMessage("分析失败，请检查代码内容或稍后重试");
        }

        // 3. 直接复制相同字段
        response.setHealthScore(report.getHealthScore());
        response.setFixedCode(report.getFixedCode());
        response.setSummary(report.getSummary());

        // 4. 复制问题列表并计算统计数据
        List<CodeIssue> issues = report.getIssues();
        // 浅拷贝列表（如果担心外部修改，可防御性拷贝，但通常无妨）
        response.setIssues(issues);

        IssueStatistics statistics = new IssueStatistics();
        if (issues != null && !issues.isEmpty()) {
            int high = 0, medium = 0, low = 0;
            for (CodeIssue issue : issues) {
                String severity = issue.getSeverity();
                if ("HIGH".equalsIgnoreCase(severity)) {
                    high++;
                } else if ("MEDIUM".equalsIgnoreCase(severity)) {
                    medium++;
                } else if ("LOW".equalsIgnoreCase(severity)) {
                    low++;
                }
                // 其他未知等级忽略，或归为 LOW
            }
            statistics.setHighCount(high);
            statistics.setMediumCount(medium);
            statistics.setLowCount(low);
            statistics.setTotalCount(issues.size());
        } else {
            // 没有问题的情况
            statistics.setHighCount(0);
            statistics.setMediumCount(0);
            statistics.setLowCount(0);
            statistics.setTotalCount(0);
        }
        response.setStatistics(statistics);

        return response;
    }
}
