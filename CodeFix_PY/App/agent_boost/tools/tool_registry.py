import difflib
import re
from pathlib import Path

import httpx, os, json

from App.agent_boost.tool_model.file_change_result import FileChangeResult
from App.agent_boost.tool_model.tool_schemas import TOOL_SCHEMAS
from App.services.rag_service import search_manual
from App.infrastructure.files_search.search_ignore import DEFAULT_SEARCH_IGNORES


MAX_READ_LINES = 500
MAX_GLOB_RESULTS = 100
MAX_GREP_RESULTS = 100
MAX_GREP_LINE_LENGTH = 300


class ToolRegistry:
    def __init__(self):
        self.tools = {}
        self.schemas = {}  # 每个工具的参数校验器

    def register(self, name, description, need_caller=False):
        """这是一个装饰器，用来把函数注册进工具箱"""

        def decorator(func):
            self.tools[name] = {
                "func": func,
                "desc": description,
                "need_caller": need_caller
            }
            if name in TOOL_SCHEMAS:
                self.schemas[name] = TOOL_SCHEMAS[name]
            return func

        return decorator

    def get_tools_desc(self, allowed_tools=None):
        if allowed_tools is None:
            allowed_tools = self.tools.keys()
        desc_list = []
        for name in allowed_tools:
            if name not in self.tools:
                continue
            info = self.tools[name]
            desc_list.append(f"- {name}: {info['desc']}")
        return "\n".join(desc_list)

    # 统一的Tool定义
    def get_tool_definitions(self, allowed_tools: tuple[str, ...]) -> list[dict]:
        definitions = []
        for name in allowed_tools:
            if name not in self.tools:
                continue
            info = self.tools[name]
            schema = self.schemas.get(name)
            if schema is None:
                raise ValueError(f"工具缺少 Schema: {name}")
            definitions.append({
                "name": name,
                "description": info["desc"],
                "parameters": schema.model_json_schema(),
            })
        return definitions

    async def execute(self, tool_name: str, args: dict, caller):
        if tool_name not in self.tools:
            raise ValueError(f"工具不存在: {tool_name}")
        if tool_name not in caller.allowed_tools:
            raise PermissionError(f"Agent {caller.name} 无权使用工具: {tool_name}")
        tool = self.tools[tool_name]
        func = tool["func"]
        # 为了区分调用agent还是调用普通工具
        if tool.get("need_caller", False):
            return await func(**args, caller=caller)
        return await func(**args)


# 创建全局工具箱和llm http请求实例
registry = ToolRegistry()


# === 定义工具 ===
@registry.register(name="get_length", description="测量字符串长度。输入参数: {'text': '字符串'}")
def get_length(text) -> int:
    return len(text)


@registry.register(name="verify_java_syntax",
                   description="验证 Java 代码语法是否正确。输入参数: {'code': 'Java 源代码字符串'}")
async def verify_java_syntax(code: str) -> str:
    """
    验证Java代码语法，返回校验结果。如果通过返回成功信息，否则返回具体错误
    """
    async with httpx.AsyncClient() as client:
        resp = await client.post(
            f"{os.getenv("BACKED_URL")}/api/validate",
            json={"code": code}
        )
        data = resp.json()
        if data["valid"]:
            return "语法校验通过，代码正确！"
        else:
            return f"语法校验失败：{data['error']}"


@registry.register(
    name="parse_java_code",
    description="解析 Java 代码结构，返回类名、方法、循环、注解等详细信息。输入参数: {'code': 'Java 源代码字符串'}"
)
async def parse_java_code(code: str) -> str:
    """
    调用 Java 端 /api/parse 接口，获取代码 AST 结构。
    """
    async with httpx.AsyncClient() as client:
        try:
            resp = await client.post(
                f"{os.getenv("BACKED_URL")}/api/parse",
                json={"code": code},
            )
            data = resp.json()
            # 返回格式化的 JSON 字符串，便于 Agent 阅读
            return json.dumps(data, indent=2, ensure_ascii=False)
        except Exception as e:
            return f"Error: 调用 Java 解析服务失败 - {str(e)}"


@registry.register(
    name="search_manual",
    description="""
        "查询《阿里巴巴Java开发手册》中的编码规范条款。"
        "当你对某项Java编码规范（如异常处理、事务管理、集合操作、资源关闭、N+1查询等）不确定时，"
        "使用此工具获取权威原文。输入参数为查询字符串（如 'try-with-resources 使用规范'），"
        "返回匹配的条款原文片段，可作为修复建议的权威依据。"
        输入参数: {'query': '你想了解的规范问题'}"
    """
)
async def search_manual_async(query: str, n_results: int = 3) -> str:
    return search_manual(query, n_results=n_results)


@registry.register(
    name="run_explorer",
    description="调用侦查员 Agent 分析 Java 代码结构。输入: {'code': 'Java源代码'}",
    need_caller=True
)
async def run_explorer(code: str, caller=None) -> str:
    if caller is None:
        raise RuntimeError("run_explorer 执行失败：缺少 caller Agent")
    from App.agents.worker.explorer_agent import ExplorerAgent
    agent = ExplorerAgent(
        context=caller.context,
        run_context=caller.run_context,
        base_message=caller.base_message,
        parent_agent=caller.name
    )
    res = await agent.run(code)
    return res.model_dump()


@registry.register(
    name="run_fixer",
    description="调用修复员 Agent 修复 Java 代码。输入: {'code': '原始代码', 'report': '结构分析报告'}（JSON 格式）",
    need_caller=True
)
async def run_fixer(code: str, report: dict, caller=None) -> str:
    if caller is None:
        raise RuntimeError("run_fixer 执行失败：缺少 caller Agent")
    from App.agents.worker.fixer_agent import FixerAgent
    report_text = json.dumps(report, ensure_ascii=False, indent=2)
    prompt = (f"原始代码：\n"f"{code}\n"
              f"\n"f"结构报告：\n"f"{report_text}")
    agent = FixerAgent(
        context=caller.context,
        run_context=caller.run_context,
        base_message=caller.base_message,
        parent_agent=caller.name
    )
    res = await agent.run(prompt)
    return res.model_dump()


@registry.register(
    name="list_files",
    description=(
        "列出当前 Workspace 中的文件和目录。"
        "输入参数: {'path': '可选的相对目录'}"
    ),
    need_caller=True
)
async def list_files(path: str = "", caller=None) -> list:
    _, _, directory = resolve_workspace_path(caller, path)
    if not directory.exists():
        raise FileNotFoundError(f"路径不存在: {path}")
    if not directory.is_dir():
        raise ValueError(f"不是目录: {path}")

    entries = list(directory.iterdir())
    return [
        {
            "name": p.name,
            "type": "directory" if p.is_dir() else "file"
        }
        for p in sorted(
            entries,
            key=lambda p: (not p.is_dir(), p.name.lower())
        )
    ]


@registry.register(
    name="read_file",
    description=(
        "读取当前 Workspace 中指定文件。"
        "默认读取整个文件，也可以指定行范围。"
        "对于较大文件，优先使用 start_line/end_line。"
        "单次最多读取 500 行。"
        "输入参数: "
        "{'file_name': '相对文件路径', "
        "'start_line': '可选，起始行号，从1开始', "
        "'end_line': '可选，结束行号，包含该行'}"
    ),
    need_caller=True
)
async def read_file(file_name: str, start_line: int | None = None, end_line: int | None = None, caller=None) -> str:
    _, _, target = resolve_workspace_path(caller, file_name)
    if not target.exists():
        raise FileNotFoundError(f"文件不存在: {file_name}")
    if not target.is_file():
        raise ValueError(f"不是文件: {file_name}")
    if start_line is not None and start_line < 1:
        raise ValueError("start_line 必须从 1 开始")
    if end_line is not None and end_line < 1:
        raise ValueError("end_line 必须从 1 开始")
    if (start_line is not None and end_line is not None and start_line > end_line):
        raise ValueError("start_line 不能大于 end_line")
    if (start_line is not None and end_line is not None and end_line - start_line + 1 > MAX_READ_LINES):
        raise ValueError(f"单次最多读取 {MAX_READ_LINES} 行，请缩小 start_line/end_line 范围")
    content = target.read_text(encoding="utf-8")
    if start_line is None and end_line is None:
        lines = content.splitlines()
        if len(lines) > MAX_READ_LINES:
            raise ValueError(f"文件共有 {len(lines)} 行，请使用 start_line/end_line 分段读取，单次最多 {MAX_READ_LINES} 行")
        return content
    lines = content.splitlines()
    start = (start_line or 1) - 1
    end = end_line or len(lines)
    if start >= len(lines):
        return ""
    end = min(end, len(lines))
    return "\n".join(
        f"{index + 1}: {line}"
        for index, line in enumerate(lines[start:end], start=start)
    )


@registry.register(
    name="glob",
    description=(
        "在 Workspace 中按文件匹配模式查找文件。"
        "例如 **/*.java、**/*Controller.java。"
    ),
    need_caller=True,
)
async def glob(pattern: str, path: str = "", caller=None,):
    try:
        _, workspace_root, search_root = resolve_workspace_path(caller, path)
        rg_manager = caller.run_context.rg_manager
        args = [
            "--files",
            "--hidden",
        ]

        for ignore in DEFAULT_SEARCH_IGNORES:
            args.extend(["--glob", f"!{ignore}"])

        args.extend(["--glob",pattern])

        lines = await rg_manager.run(
            args=args,
            search_root=search_root,
            max_results=MAX_GLOB_RESULTS + 1,
        )

        truncated = len(lines) > MAX_GLOB_RESULTS
        results = []

        for line in lines[:MAX_GLOB_RESULTS]:
            absolute_path = (search_root / line).resolve()
            try:
                relative_path = absolute_path.relative_to(workspace_root)
            except ValueError:
                continue
            results.append({"path": relative_path.as_posix()})

        return {
            "success": True,
            "count": len(results),
            "results": results,
            "truncated": truncated,
        }
    except PermissionError as e:
        return {"success": False, "error_type": "TOOL_PERMISSION_ERROR", "message": str(e)}
    except RuntimeError as e:
        return {"success": False, "error_type": "TOOL_ENVIRONMENT_ERROR", "message": str(e)}
    except Exception as e:
        return {"success": False, "error_type": "TOOL_EXECUTION_ERROR", "message": str(e)}


@registry.register(
    name="write_file",
    description="""
    "在当前 Workspace 中创建新文件。"
    "输入参数: {'file_name': '相对文件路径', 'content': '文件内容'}。"
    "write_file 只用于创建不存在的文件，不用于修改已有文件。"
    "如果目标文件已经存在，请使用 read_file 读取当前内容，"
    "然后使用 apply_patch 修改指定片段。"
    """,
    need_caller=True
)
async def write_file(file_name: str, content: str, caller=None) -> dict:
    if caller is None:
        raise RuntimeError("write_file 执行失败：缺少 caller Agent")
    try:
        workspace = caller.run_context.workspace
        target = workspace.resolve(file_name)
        # write_file 只负责创建新文件。
        if target.exists():
            return {
                "success": False,
                "error_type": "FILE_EXISTS",
                "message": (
                    f"文件已存在：{file_name}。"
                    f"请先使用 read_file 查看文件内容，"
                    f"再使用 apply_patch 修改。"
                )
            }
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")
        # 新建文件：before 为空，after 是新文件内容。
        diff_text, added, removed = build_diff("", content)
        return FileChangeResult(
            file_path=file_name,
            operation="created",
            added_lines=added,
            removed_lines=removed,
            diff=diff_text
        ).model_dump(by_alias=True)
    except Exception as e:
        raise RuntimeError(f"write_file 执行失败: {file_name}") from e


def build_diff(old_content: str, new_content: str):
    old_lines = old_content.splitlines(keepends=True)
    new_lines = new_content.splitlines(keepends=True)
    diff_text = "".join(difflib.unified_diff(old_lines, new_lines, fromfile="before", tofile="after"))
    added_lines = 0
    removed_lines = 0
    for line in difflib.ndiff(old_content.splitlines(), new_content.splitlines()):
        if line.startswith("+ "):
            added_lines += 1
        elif line.startswith("- "):
            removed_lines += 1
    return (diff_text, added_lines, removed_lines)


@registry.register(
    name="grep",
    description=(
        "在 Workspace 中搜索文本、类名、方法名、字段、配置项、错误信息或其他代码内容。"
        "默认按普通文本搜索，query 会按字面文本匹配，不支持 |、.* 等正则语法。"
        "需要同时匹配多个模式或使用正则表达式时，设置 regex=true。"
    ),
    need_caller=True,
)
async def grep(query: str, path: str = "", include: str | None = None, regex: bool = False, caller=None,):
    try:
        _, workspace_root, search_root = resolve_workspace_path(caller, path)
        rg_manager = caller.run_context.rg_manager
        args = [
            "--line-number",
            "--no-heading",
            "--color",
            "never",
            "--hidden",
        ]
        for ignore in DEFAULT_SEARCH_IGNORES:
            args.extend(["--glob", f"!{ignore}"])
        if not regex:
            args.append("--fixed-strings")
        if include:
            args.extend(["--glob",include])

        args.append(query)
        lines = await rg_manager.run(args=args, search_root=search_root, max_results=MAX_GREP_RESULTS + 1)
        truncated = len(lines) > MAX_GREP_RESULTS
        results = []
        for line in lines[:MAX_GREP_RESULTS]:
            match = re.match(r"^(.*):(\d+):(.*)$", line)
            if not match:
                continue
            file_name, line_number, content = match.groups()
            try:
                line_number = int(line_number)
            except ValueError:
                continue
            absolute_path = Path(file_name).resolve()
            try:
                relative_path = absolute_path.relative_to(workspace_root)
            except ValueError:
                continue

            results.append({
                "file": relative_path.as_posix(),
                "line": line_number,
                "content": content[:MAX_GREP_LINE_LENGTH],
            })

        return {
            "success": True,
            "count": len(results),
            "results": results,
            "truncated": truncated,
        }
    except PermissionError as e:
        return {"success": False, "error_type": "TOOL_PERMISSION_ERROR", "message": str(e)}
    except RuntimeError as e:
        return {"success": False, "error_type": "TOOL_ENVIRONMENT_ERROR", "message": str(e)}
    except Exception as e:
        return {"success": False, "error_type": "TOOL_EXECUTION_ERROR", "message": str(e)}


@registry.register(
    name="delete_file",
    description=(
        "删除当前 Workspace 中指定的文件。"
        "输入参数: {'file_name': 'Workspace 内的相对文件路径'}"
        "只能删除 Workspace 内的文件，不能删除 Workspace 外部路径。"
    ),
    need_caller=True
)
async def delete_file(file_name: str, caller=None) -> dict:
    if caller is None:
        raise RuntimeError("delete_file 执行失败：缺少 caller Agent")
    workspace = caller.run_context.workspace
    target = workspace.resolve(file_name)
    if not target.exists():
        raise FileNotFoundError(f"文件不存在: {file_name}")
    if not target.is_file():
        raise ValueError(f"目标不是文件，拒绝删除: {file_name}")
    try:
        # 删除前读取原始内容
        old_content = target.read_text(encoding="utf-8")
        target.unlink()
    except OSError as e:
        raise RuntimeError(f"删除文件失败: {file_name}") from e

    # 删除文件：
    # before 有内容
    # after 为空
    diff_text, added_lines, removed_lines = build_diff(old_content, "")
    return FileChangeResult(
        file_path=file_name,
        operation="deleted",
        added_lines=added_lines,
        removed_lines=removed_lines,
        diff=diff_text
    ).model_dump(by_alias=True)


@registry.register(
    name="apply_patch",
    description=(
        "修改当前 Workspace 中已有文件的一段精确文本。"
        "输入参数: "
        "{'file_name': '相对文件路径', 'old_text': '文件中需要被替换的原始文本', 'new_text': '替换后的文本'}。"
        "文件必须已经存在。"
        "old_text 必须在文件中精确匹配一次。"
        "如果文件不存在，请使用 write_file 创建。"
    ),
    need_caller=True
)
async def apply_patch(file_name: str, old_text: str, new_text: str, caller=None) -> dict:
    if caller is None:
        raise RuntimeError("apply_patch 执行失败：缺少 caller Agent")
    if not old_text:
        return {
            "success": False,
            "error_type": "INVALID_PATCH",
            "message": "old_text 不能为空。"
        }
    try:
        workspace = caller.run_context.workspace
        target = workspace.resolve(file_name)
        # apply_patch 只负责修改已有文件。
        if not target.exists():
            return {
                "success": False,
                "error_type": "FILE_NOT_FOUND",
                "message": f"文件不存在：{file_name}。如果需要创建文件，请使用 write_file。"
            }
        if not target.is_file():
            return {
                "success": False,
                "error_type": "NOT_A_FILE",
                "message": (f"目标不是普通文件，无法执行 apply_patch：{file_name}")
            }
        old_content = target.read_text(encoding="utf-8")
        occurrences = old_content.count(old_text)

        # 0 次：模型提供的上下文已经过期，或者文本写错。
        if occurrences == 0:
            return {
                "success": False,
                "error_type": "PATCH_TARGET_NOT_FOUND",
                "message": f"未找到需要修改的目标文本：{file_name}。请先使用 read_file 获取最新内容后再重试。"
            }

        # 多次：无法确定到底要改哪一个。
        if occurrences > 1:
            return {
                "success": False,
                "error_type": "PATCH_TARGET_AMBIGUOUS",
                "message": f"目标文本在 {file_name} 中出现 {occurrences} 次，无法安全修改。请提供更精确的 old_text。"
            }

        new_content = old_content.replace(old_text, new_text, 1)

        # 实际没有产生变化。
        if new_content == old_content:
            return {
                "success": False,
                "error_type": "NO_CHANGE",
                "message": f"apply_patch 未产生任何文件变化：{file_name}"
            }

        target.write_text(new_content, encoding="utf-8")
        diff_text, added, removed = build_diff(old_content, new_content)

        return FileChangeResult(
            file_path=file_name,
            operation="modified",
            added_lines=added,
            removed_lines=removed,
            diff=diff_text
        ).model_dump(by_alias=True)

    except Exception as e:
        raise RuntimeError(f"apply_patch 执行失败: {file_name}") from e

COMMAND_TIMEOUT = 120
MAX_COMMAND_OUTPUT = 10000
@registry.register(
    name="run_command",
    description=(
        "在当前 Workspace 中执行项目相关命令。"
        "用于运行构建、测试、代码检查、脚本等操作。"
        "不要用于查看文件结构、读取文件或搜索代码。"
        "输入参数: {'command':'需要执行的命令'}"
    ),
    need_caller=True,
)
async def run_command(command: str, caller=None) -> dict:
    import asyncio
    if caller is None:
        raise RuntimeError("run_command 执行失败：缺少 caller Agent")
    workspace = caller.run_context.workspace
    cwd = workspace.root_path
    try:
        process = await asyncio.create_subprocess_shell(
            command,
            cwd=str(cwd),
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )

        stdout, stderr = await asyncio.wait_for(
            process.communicate(),
            timeout=COMMAND_TIMEOUT
        )

        stdout_text = decode_output(stdout)
        stderr_text = decode_output(stderr)
        return {
            "success": process.returncode == 0,
            "command": command,
            "exit_code": process.returncode,
            "stdout": stdout_text[:MAX_COMMAND_OUTPUT],
            "stderr": stderr_text[:MAX_COMMAND_OUTPUT],
            "truncated": (
                len(stdout_text) > MAX_COMMAND_OUTPUT
                or len(stderr_text) > MAX_COMMAND_OUTPUT
            )
        }
    except asyncio.TimeoutError:
        process.kill()
        await process.wait()
        return {
            "success": False,
            "command": command,
            "error_type": "TIMEOUT",
            "message": f"命令执行超过 {COMMAND_TIMEOUT} 秒"
        }
    except Exception as e:
        return {
            "success": False,
            "command": command,
            "error_type": "COMMAND_EXECUTION_ERROR",
            "message": str(e),
        }

def decode_output(data: bytes) -> str:
    for encoding in ("utf-8", "gbk", "gb18030", "cp936"):
        try:
            return data.decode(encoding)
        except UnicodeDecodeError:
            continue
    return data.decode("utf-8", errors="replace")

def resolve_workspace_path(caller, path: str = ""):
    if caller is None:
        raise RuntimeError("Workspace 工具执行失败：缺少 caller Agent")

    workspace = caller.run_context.workspace
    root = workspace.root_path.resolve()
    target = workspace.resolve(path or ".").resolve()

    try:
        target.relative_to(root)
    except ValueError:
        raise PermissionError(
            f"路径超出 Workspace 范围：{path}"
        )

    return workspace, root, target
