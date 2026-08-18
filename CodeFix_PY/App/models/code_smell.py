# app/models/code_smell.py
from pydantic import BaseModel
from typing import Optional


class CodeSmell(BaseModel):
    """
    代码异味（嫌疑点）—— 与 Java 端完全对齐
    用于接收 Java 端预扫描结果，也用于 Agent 内部传递
    """
    lineNumber: int  # 问题所在行号
    type: str  # 问题类型（如 N+1_QUERY）
    codeSnippet: Optional[str] = None  # 嫌疑代码片段（可选）
    description: Optional[str] = None  # 问题描述（可选）

    class Config:
        # 允许使用字段名作为参数名，且支持额外的未知字段（忽略）
        extra = "ignore"
