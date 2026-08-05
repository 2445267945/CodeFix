from App.agents.prompts import SYSTEM_PROMPT_TEMPLATE  # 导入提示词
from App.services.llm_factory import LLMFactory
from fastapi import FastAPI
from pydantic import BaseModel
from typing import Optional, List
import uvicorn
from App.agents.audit_agent import AuditAgent

# ---------- 定义请求/响应模型（与 Java 端对齐） ----------
class CodeSmell(BaseModel):
    lineNumber: int
    type: str
    severity: str
    description: Optional[str] = None
    codeSnippet: Optional[str] = None

class AnalyzeRequest(BaseModel):
    code: str                     # 完整 Java 源码
    fileName: Optional[str] = None
    smells: Optional[List[CodeSmell]] = None   # Java 端预扫描的嫌疑点

class CodeIssue(BaseModel):
    lineNumber: int
    type: str
    severity: str
    description: str
    codeSnippet: str
    suggestion: str
    fixedSnippet: Optional[str] = None

class AuditReport(BaseModel):
    status: str
    healthScore: int
    issues: List[CodeIssue]
    fixedCode: str
    summary: str
    metadata: dict

app = FastAPI(title="Java代码审计Agent", version="1.0")

# ---------- 核心分析接口 ----------
@app.post("/analyze", response_model=AuditReport)
async def analyze(request: AnalyzeRequest):
    print(f"收到请求，文件名: {request.fileName}, 代码长度: {len(request.code)}")
    print(f"Java 端预扫描到的嫌疑点: {request.smells}")

    # 1. 构造完整的问题描述，包含代码和预扫描线索
    question = f"""
    请分析以下 Java 代码，是否有什么语法问题或编码隐患，
    代码：
    ```java
    {request.code}
    ```
    以下是预扫描的嫌疑点（仅供参考）：
    {request.smells if request.smells else "暂无"}
    """

    result = await agent.run(question)

    # 必须返回符合 AuditReport 的结构
    return {
        "status": "success",
        "healthScore": 85,
        "issues": [],  # 这里可以先为空，后续根据实际审计结果填充
        "fixedCode": request.code,
        "summary": str(result) if result else "审计完成，无问题",
        "metadata": {"agent_result": str(result)}
    }

# ---------- 启动服务 ----------
if __name__ == "__main__":
    main_llm = LLMFactory.get_llm(4096, 0.1, "mid")
    compress_llm = LLMFactory.get_llm(4096, 0.1, "low")
    agent = AuditAgent("JavaFixer", main_llm, compress_llm, 10, 30, SYSTEM_PROMPT_TEMPLATE)
    uvicorn.run(app, host="0.0.0.0", port=8000)