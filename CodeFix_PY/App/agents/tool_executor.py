import json
from App.utils.json_parser import parse_llm_response
from App.services.llm_service import LLMService
from .agent_state import AgentState
from .react_agent import ReActAgent

class ToolExecutor(ReActAgent):
    def __init__(self):
        super().__init__()  # 调用父类的构造函数
        self.llm = LLMService()
        self.cur_tool_name = None
        self.cur_tool_args = None

    async def think(self):
        # 1. 获取 LLM 响应
        response = await self.llm.chat(self.messages)
        # print(f"[原始响应]: {response}")
        # 2. 使用抽取出来的解析器
        parsed = parse_llm_response(response)
        # 3. 根据解析结果执行逻辑
        if parsed["type"] == "finish":
            self.status = AgentState.FINISHED
            self.final_answer = parsed["content"]
            return False  # 无需执行工具
        elif parsed["type"] == "action":
            self.cur_tool_name = parsed["action"]
            self.cur_tool_args = parsed["action_input"]
            return True  # 需要执行工具
        elif parsed["type"] == "error":
            # 如果是空响应，追加一条引导消息，让下一轮继续
            print(f"{parsed['message']}")
            self.add_message("user", "你刚才没有输出有效指令，请根据任务输出 Action 或 Finish。")
            return False
        else:  # thought
            # 只输出了思考，不执行工具，等待下一轮
            print(f"AI 思考中，等待下一轮指令...")
            return False

    async def act(self):
        tool_name = self.cur_tool_name
        tool_args = self.cur_tool_args
        try:
            args = json.loads(tool_args)
            func = self.tools[tool_name]["func"]
            self.cur_tool_name = None
            self.cur_tool_args = None
            tool_res = await func(**args)
            self.add_message("assistant", f"Observation:{tool_res}")
            print(f"调用工具:{tool_name}, 入参:{tool_args}")
            return tool_res
        except Exception as e:
            self.status = AgentState.ERROR
            return f"Error: 工具执行失败 - {str(e)}"

    def cleanup(self):
        self.messages.clear()
