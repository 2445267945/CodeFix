import json
import logging

from App.utils.json_parser import parse_llm_response, AgentDecisionParseError
from .agent_state import AgentState
from .context.agent_context import AgentContext
from .decision.agent_decision import ToolCallDecision, FinishDecision
from .react_agent import ReActAgent
import datetime

from App.models.enum.agent_event import AgentEvent

logger = logging.getLogger(__name__)

class ToolExecutor(ReActAgent):

    def __init__(self, context: AgentContext, base_message = None, parent_agent: str | None = None):
        super().__init__(context, base_message, parent_agent)
        self.cur_tool_name = None
        self.cur_tool_args = None
        self.action_history = []


    async def think(self):
        if self.main_llm == None:
            self.final_answer = f"{self.name} 的 LLM 未实例化"
            self.msg_sender.agent_report(agent = self, event = AgentEvent.ERROR, output = {"error": self.final_answer})
            return
        # 1. 获取 LLM 响应
        llm_messages = []
        if self.history_summary:
            llm_messages.append(self.history_summary.model_dump())
        llm_messages.extend(self.messages)
        print(f"当前AI：{self.name}")
        try:
            response = await self.main_llm.chat(llm_messages)
            print(f"原始响应---：{response}")
        except Exception as e:
            logger.exception(f"{self.name} LLM 调用失败")
            self.status = AgentState.ERROR
            self.final_answer = {"error": f"LLM 调用失败: {str(e)}"}
            self.msg_sender.agent_report(self.final_answer, agent=self, output = self.final_answer)
            return False
        self.add_message("assistant", response)
        # 2. 使用抽取出来的解析器,解析并验证 AgentDecision
        try:
            decision = parse_llm_response(response)
        except AgentDecisionParseError as e:
            self.status = AgentState.ERROR
            self.final_answer = {"errer": f"Agent 输出格式错误: {str(e)}"}
            self.msg_sender.agent_report(agent = self, event = AgentEvent.ERROR, output = self.final_answer)
            return False
        # 4. 判断是Tool Call还是Finish
        if isinstance(decision, ToolCallDecision):
            self.cur_tool_name = decision.tool
            self.cur_tool_args = decision.arguments
            self.msg_sender.agent_report(agent = self,event = AgentEvent.THINK, output = decision.model_dump(by_alias=True))
            return True
        if isinstance(decision, FinishDecision):
            self.status = AgentState.FINISHED
            self.final_answer = decision.answer
            self.msg_sender.agent_report(agent = self, event = AgentEvent.FINISH, output = decision.model_dump(by_alias=True))
            return False

    async def act(self):
        tool_res = None
        tool_name = self.cur_tool_name
        tool_args = self.cur_tool_args
        self.status = AgentState.EXECUTING
        self.msg_sender.agent_report(agent = self, event = AgentEvent.TOOL_CALL, output = {"tool": tool_name, "arguments": tool_args, })
        print(f"使用工具{tool_name}，传入参数{tool_args}")
        if self.manager.checkLoop(tool_name, tool_args, self.action_history):
            self.status = AgentState.ERROR
            return {"error": "Error: AI陷入死循环"}
        try:
            # 1. 工具传入的参数
            args = tool_args
            # 2. 调用校验方法
            validated_args = self.manager.check_tool_Input(tool_name, args, self.tools_schemas)
            # 3. 执行工具
            tool_res = await self.tools_schemas.execute(tool_name = tool_name, args = validated_args, caller = self)
            # 看门狗续命
            self.last_time += datetime.timedelta(seconds = self.watch_dog)
        except json.JSONDecodeError as e:
            tool_res = {"success": False, "error_type": "JSON_PARSE_ERROR", "tool": tool_name, "message": str(e)}
        except ValueError as e:
            tool_res = {"success": False, "error_type": "TOOL_ARGUMENT_ERROR", "tool": tool_name, "message": str(e)}
        except Exception as e:
            self.status = AgentState.ERROR
            tool_res = {"success": False, "error_type": "TOOL_EXECUTION_ERROR", "tool": tool_name, "message": str(e)}
        finally:
            # 统一收尾：无论成功或失败，都将结果（或错误信息）包装成 Observation 加入历史
            self.add_message("user", f"Observation: {tool_res}")
            self.msg_sender.agent_report(agent = self, event = AgentEvent.TOOL_RESULT, output = tool_res)
        return tool_res

    def cleanup(self):
        self.messages.clear()
