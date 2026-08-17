import inspect

import httpx, os, json
from App.models.tool_schemas import TOOL_SCHEMAS
from App.services.rag_service import search_manual


class ToolRegistry:
    def __init__(self):
        self.tools = {}
        self.schemas = {} # 每个工具的参数校验器

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

@registry.register(name="verify_java_syntax", description="验证 Java 代码语法是否正确。输入参数: {'code': 'Java 源代码字符串'}")
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
    need_caller = True
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
    need_caller = True
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
    description="列出当前 Workspace 中的文件和目录。输入: {'path': '可选的相对目录'}",
    need_caller=True
)
async def list_files(path: str = "", caller=None) -> list:
    if caller is None:
        raise RuntimeError("list_files 执行失败：缺少 caller Agent")
    workspace = caller.run_context.workspace
    directory = workspace.resolve(path or ".")
    if not directory.exists():
        raise FileNotFoundError(f"路径不存在: {path}")
    if not directory.is_dir():
        raise ValueError(f"不是目录: {path}")
    return [
        {
            "name": p.name,
            "type": "directory" if p.is_dir() else "file"
        }
        for p in sorted(directory.iterdir())
    ]

@registry.register(
    name="read_file",
    description="读取当前 Workspace 中指定文件。输入: {'file_name': '相对文件路径'}",
    need_caller=True
)
async def read_file(file_name: str, caller=None) -> str:
    if caller is None:
        raise RuntimeError("read_file 执行失败：缺少 caller Agent")
    workspace = caller.run_context.workspace
    target = workspace.resolve(file_name)
    if not target.exists():
        raise FileNotFoundError(f"文件不存在: {file_name}")
    if not target.is_file():
        raise ValueError(f"不是文件: {file_name}")
    return target.read_text(encoding="utf-8")

@registry.register(
    name="write_file",
    description="写入当前 Workspace 中指定文件。输入: {'file_name': '相对文件路径', 'content': '文件内容'}",
    need_caller=True
)
async def write_file(file_name: str, content: str, caller=None) -> str:
    if caller is None:
        raise RuntimeError("write_file 执行失败：缺少 caller Agent")
    workspace = caller.run_context.workspace
    target = workspace.resolve(file_name)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")
    return f"文件写入成功: {file_name}"

@registry.register(
    name="search_file",
    description="在当前 Workspace 中搜索文本。输入: {'query': '搜索内容', 'path': '可选目录'}",
    need_caller=True
)
async def search_file(query: str, path: str = "", caller=None) -> list:
    if caller is None:
        raise RuntimeError("search_file 执行失败：缺少 caller Agent")
    workspace = caller.run_context.workspace
    base = workspace.resolve(path or ".")
    results = []
    for file in base.rglob("*"):
        if not file.is_file():
            continue
        try:
            content = file.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        if query in content:
            results.append({
                "file": str(file.relative_to(workspace.root_path))
            })

    return results

@registry.register(
    name="delete_file",
    description=(
        "删除当前 Workspace 中指定的文件。"
        "输入参数: {'file_name': 'Workspace 内的相对文件路径'}"
        "只能删除 Workspace 内的文件，不能删除 Workspace 外部路径。"
    ),
    need_caller=True
)
async def delete_file(file_name: str,caller=None) -> str:
    if caller is None:
        raise RuntimeError("delete_file 执行失败：缺少 caller Agent")
    workspace = caller.run_context.workspace
    target = workspace.resolve(file_name)
    if not target.exists():
        raise FileNotFoundError(f"文件不存在: {file_name}")
    if not target.is_file():
        raise ValueError(f"目标不是文件，拒绝删除: {file_name}")
    try:
        target.unlink()
    except OSError as e:
        raise RuntimeError(f"删除文件失败: {file_name}") from e

    return f"文件删除成功: {file_name}"