import json
import logging

from App.utils.json_parser import parse_llm_response
from .agent_state import AgentState
from .react_agent import ReActAgent
import datetime

logger = logging.getLogger(__name__)

class ToolExecutor(ReActAgent):
    def __init__(self):
        super().__init__()  # 调用父类的构造函数
        self.cur_tool_name = None
        self.cur_tool_args = None
        self.action_history = []


    async def think(self):
        if self.main_llm == None:
            logger.error("LLM 未实例化")
            return
        # 1. 获取 LLM 响应
        response = await self.main_llm.chat(self.messages)
        self.add_message("assistant", response)
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
            # print(f"{parsed['message']}")
            self.add_message("user", "你刚才没有输出有效指令，请根据任务输出 Action 或 Finish。")
            return False
        else:  # thought
            # 只输出了思考，不执行工具，等待下一轮
            # print(f"AI 思考中，等待下一轮指令...")
            return False

    async def act(self):
        tool_name = self.cur_tool_name
        tool_args = self.cur_tool_args
        tool_res = ""
        print(f"使用工具{tool_name}，传入参数{tool_args[:100]}")
        if self.manager.checkLoop(tool_name, tool_args, self.action_history):
            self.status = AgentState.ERROR
            return "Error: AI陷入死循环"
        result_output = ""  # 统一存放最终要返回的内容
        try:
            # 1. 解析 JSON
            args = json.loads(tool_args)
            # 2. 调用校验方法
            validated_args = self.manager.check_tool_Input(tool_name, args, self.tools_schemas)
            # 3. 执行工具
            func = self.tools[tool_name]["func"]
            tool_res = await func(**validated_args)
            # 看门狗续命
            self.last_time += datetime.timedelta(seconds=self.watch_dog)
        except json.JSONDecodeError as e:
            tool_res = f"Error: JSON解析失败 - {str(e)}"
        except ValueError as e:
            tool_res = f"Error: 参数校验失败 - {str(e)}"
        except Exception as e:
            self.status = AgentState.ERROR
            tool_res = f"Error: 工具执行失败 - {str(e)}"
        finally:
            # 统一收尾：无论成功或失败，都将结果（或错误信息）包装成 Observation 加入历史
            self.add_message("user", f"Observation: {tool_res}")
            return tool_res



    def cleanup(self):
        self.messages.clear()
