import json
from App.utils.json_parser import parse_llm_response
from pydantic import ValidationError
from App.services.llm_service import LLMService
from .agent_state import AgentState
from .react_agent import ReActAgent
import datetime

class ToolExecutor(ReActAgent):
    def __init__(self):
        super().__init__()  # 调用父类的构造函数
        self.llm = LLMService()
        self.cur_tool_name = None
        self.cur_tool_args = None
        self.action_history = []

    async def think(self):
        # 1. 获取 LLM 响应
        response = await self.llm.chat(self.messages)
        # print(f"[原始响应]: {response}")
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
        print(f"使用工具{tool_name}")
        if self.checkLoop(tool_name, tool_args, self.action_history):
            self.status = AgentState.ERROR
            return "Error: AI陷入死循环"
        result_output = ""  # 统一存放最终要返回的内容
        try:
            # 1. 解析 JSON
            args = json.loads(tool_args)
            # 2. 调用校验方法
            validated_args = self.check_tool_Input(tool_name, args, self.tools_schemas)
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

    def check_tool_Input(self, tool_name, tool_args, tools_schemas):
        """
        AI给出的参数校验
        :param tool_name:
        :param tool_args:
        :return:
        """
        if tool_name in self.tools_schemas.schemas:
            try:
                validated_args = tools_schemas.schemas[tool_name](**tool_args)
                return validated_args.model_dump()
            except ValidationError as e:
                # 直接抛出异常，让上层捕获
                raise ValueError(f"参数校验失败: {e.errors()}") from e

    def checkLoop(self, tool_name, tool_args, action_history) -> bool:
        """
        检测AI是不是重复的调用同一个工具和传入同样的参数，如果是则代表死循环了
        :param tool_name:
        :param tool_args:
        :return:
        """
        action_signature = f"{tool_name}:{tool_args}"  # 生成动作指纹
        # 检测重复
        action_history.append(action_signature)
        if len(action_history) > 3:
            action_history.pop(0)
        if len(action_history) == 3 and len(set(action_history)) == 1:
            # 连续3轮完全一样的动作 → 死循环
            return True
        return False

    def cleanup(self):
        self.messages.clear()
