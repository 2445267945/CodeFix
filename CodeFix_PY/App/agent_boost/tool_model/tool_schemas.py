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
    task: str = Field(..., description="需要 Explorer 分析的问题或目标，例如类结构、调用链、影响范围、潜在风险", min_length=1)

# ---------- run_fixer 的入参规则 ----------
class RunFixerInput(BaseModel):
    task: str = Field(..., min_length=1, description="需要 Fixer 完成的修改任务，例如需要修改哪个文件、修什么问题")
    report: Optional[dict] = Field(
        default=None,
        description="Explorer 返回的结构化分析报告，可选；未做前置分析时可以不传",
    )

class ListFilesInput(BaseModel):
    path: str = Field(default="", description="Workspace 中的相对目录")

class GlobInput(BaseModel):
    pattern: str = Field(..., description="文件匹配模式，例如 **/*.java、**/*Controller.java", min_length=1, max_length=500)
    path: str = Field(default="", description="Workspace 中的搜索范围")

class ReadFileInput(BaseModel):
    file_name: str = Field(..., description="Workspace 中的相对文件路径", min_length=1, max_length=1000)
    start_line: Optional[int] = Field(default=None, ge=1, description="起始行号，从1开始")
    end_line: Optional[int] = Field(default=None, ge=1, description="结束行号，从1开始")

class WriteFileInput(BaseModel):
    file_name: str = Field(..., description="Workspace 中的相对文件路径", min_length=1, max_length=1000)
    content: str = Field(..., description="要写入的文件内容")

class GrepInput(BaseModel):
    query: str = Field(..., description="搜索文本或正则表达式", min_length=1, max_length=500)
    path: str = Field(default="", description="Workspace 中的搜索范围")
    include: str | None = Field(default=None, description="可选的文件匹配模式，例如 **/*.java")
    regex: bool = Field(default=False, description="是否使用正则表达式。false=普通文本搜索，true=正则搜索。")

class DeleteFileInput(BaseModel):
    file_name: str = Field(..., description="Workspace 中要删除的相对文件路径", min_length=1, max_length=1000)

class ApplyPatchInput(BaseModel):
    file_name: str = Field(..., description="Workspace 中已有文件的相对路径", min_length=1, max_length=1000)
    old_text: str = Field(..., description="文件中需要被精确替换的原始文本", min_length=1)
    new_text: str = Field(..., description="替换后的新文本")


class RunCommandInput(BaseModel):
    command: str = Field(description="需要执行的本地命令")

# ---------- 工具 Schema 注册表 ----------
TOOL_SCHEMAS = {
    "search_manual": SearchManualInput,
    "verify_java_syntax": VerifyJavaSyntaxInput,
    "parse_java_code": ParseJavaCodeInput,
    "run_explorer": RunExplorerInput,
    "run_fixer": RunFixerInput,

    "list_files": ListFilesInput,
    "glob": GlobInput,
    "read_file": ReadFileInput,
    "write_file": WriteFileInput,
    "apply_patch": ApplyPatchInput,
    "grep": GrepInput,
    "delete_file": DeleteFileInput,

    "run_command": RunCommandInput,
}