# tools/tool_schemas.py
from pydantic import BaseModel, Field, ValidationError
from typing import Optional, List

# ---------- search_manual 的入参规则 ----------
class SearchManualInput(BaseModel):
    query: str = Field(..., description="查询关键词", min_length=1, max_length=400)
    n_results: Optional[int] = Field(3, ge=1, le=10, description="返回结果数量")

# ---------- verify_java_syntax 的入参规则 ----------
class VerifyJavaSyntaxInput(BaseModel):
    code: str = Field(..., description="Java源代码", min_length=1)

# ---------- 工具 Schema 注册表 ----------
TOOL_SCHEMAS = {
    "search_manual": SearchManualInput,
    "verify_java_syntax": VerifyJavaSyntaxInput,
}