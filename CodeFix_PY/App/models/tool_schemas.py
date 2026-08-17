from pydantic import BaseModel, Field
from typing import Optional

# ---------- search_manual 的入参规则 ----------
class SearchManualInput(BaseModel):
    query: str = Field(..., description="查询关键词", min_length=1, max_length=400)
    n_results: Optional[int] = Field(3, ge=1, le=10, description="返回结果数量")

# ---------- verify_java_syntax 的入参规则 ----------
class VerifyJavaSyntaxInput(BaseModel):
    code: str = Field(..., description="Java源代码", min_length=1)

# ---------- parse_java_code 的入参规则 ----------
class ParseJavaCodeInput(BaseModel):
    code: str = Field(..., description="Java源代码", min_length=1)

# ---------- run_explorer 的入参规则 ----------
class RunExplorerInput(BaseModel):
    code: str = Field(..., description="Java源代码", min_length=1)

# ---------- run_fixer 的入参规则 ----------
class RunFixerInput(BaseModel):
    code: str = Field(..., min_length=1, description="原始 Java 代码")
    report: dict = Field(..., description="结构化代码分析报告")

class ListFilesInput(BaseModel):
    path: str = Field(default="", description="Workspace 中的相对目录")


class ReadFileInput(BaseModel):
    file_name: str = Field(..., description="Workspace 中的相对文件路径", min_length=1)


class WriteFileInput(BaseModel):
    file_name: str = Field(..., description="Workspace 中的相对文件路径", min_length=1)
    content: str = Field(..., description="要写入的文件内容")

class SearchFileInput(BaseModel):
    query: str = Field(..., description="搜索关键词", min_length=1, max_length=500)
    path: str = Field(default="", description="Workspace 中的搜索范围")

class DeleteFileInput(BaseModel):
    file_name: str = Field(..., description="Workspace 中要删除的相对文件路径", min_length=1, max_length=1000)

# ---------- 工具 Schema 注册表 ----------
TOOL_SCHEMAS = {
    "search_manual": SearchManualInput,
    "verify_java_syntax": VerifyJavaSyntaxInput,
    "parse_java_code": ParseJavaCodeInput,
    "run_explorer": RunExplorerInput,
    "run_fixer": RunFixerInput,

    "list_files": ListFilesInput,
    "read_file": ReadFileInput,
    "write_file": WriteFileInput,
    "search_file": SearchFileInput,
    "delete_file": DeleteFileInput,
}