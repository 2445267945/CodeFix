import httpx
from watchfiles import awatch
from App.models.tool_schemas import TOOL_SCHEMAS
from App.services.rag_service import search_manual

class ToolRegistry:
    def __init__(self):
        self.tools = {}
        self.schemas = {} # 每个工具的参数校验器

    def register(self, name, description):
        """这是一个装饰器，用来把函数注册进工具箱"""

        def decorator(func):
            self.tools[name] = {
                "func": func,
                "desc": description
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


# 创建全局工具箱
registry = ToolRegistry()


# === 定义我们的工具 ===
@registry.register(name="get_length", description="测量字符串长度。输入参数: {'text': '字符串'}")
def get_length(text) -> int:
    return len(text)

@registry.register(name="verify_java_syntax", description="验证 Java 代码语法是否正确。输入参数: {'code': 'Java 源代码字符串'}")
async def verify_java_syntax(code: str) -> str:
    """验证Java代码语法，返回校验结果。如果通过返回成功信息，否则返回具体错误"""
    async with httpx.AsyncClient() as client:
        resp = await client.post(
            "http://localhost:8080/api/validate",
            json={"code": code}
        )
        data = resp.json()
        if data["valid"]:
            return "语法校验通过，代码正确！"
        else:
            return f"语法校验失败：{data['error']}"

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


