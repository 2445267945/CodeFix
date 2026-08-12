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

    def get_tools_desc(self):
        """生成一段话，告诉 AI 有哪些工具"""
        desc_list = []
        for name, info in self.tools.items():
            desc_list.append(f"- {name}: {info['desc']}")
        return "\n".join(desc_list)

    async def execute(self, tool_name: str, args: dict, caller):
        if tool_name not in self.tools:
            raise ValueError(
                f"工具不存在: {tool_name}"
            )
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
    agent = ExplorerAgent(context=caller.context, base_message=caller.base_message)
    return await agent.run(code)

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
    agent = FixerAgent(context=caller.context, base_message=caller.base_message)
    return await agent.run(prompt)



