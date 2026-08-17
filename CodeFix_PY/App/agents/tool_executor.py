import json
import logging

from App.utils.json_parser import parse_llm_response, AgentDecisionParseError
from .agent_model.llm_response import LLMResponse
from .agent_model.tool_call import ToolCall
from .agent_state import AgentState
from .context.agent_context import AgentContext
from .context.agent_run_context import AgentRunContext
from .decision.agent_decision import ToolCallDecision, FinishDecision
from .react_agent import ReActAgent
import datetime

from App.models.enum.agent_event import AgentEvent

logger = logging.getLogger(__name__)

class ToolExecutor(ReActAgent):

    def __init__(self, context: AgentContext, run_context: AgentRunContext, base_message = None, parent_agent: str | None = None):
        super().__init__(context, run_context, base_message, parent_agent)
        self.cur_tool_calls: list[ToolCall] = []
        self.action_history = []


    async def think(self):
        self.status = AgentState.THINKING
        print("=== THINK START ===")
        if self.main_llm is None:
            self.final_answer = (f"{self.name} 的 LLM 未实例化")
            self.status = AgentState.ERROR
            self.msg_sender.agent_report(agent=self, event=AgentEvent.ERROR, output={"error": self.final_answer})
            return False
        try:
            # 1. 获取当前 Agent 有权限使用的工具
            tool_definitions = (self.tools_schemas.get_tool_definitions(self.allowed_tools))
            # 2. 调用 LLM
            print(f"当前AI：{self.name}")
            print(
                f"准备调用 LLM，messages={len(self.messages)}"
            )
            response: LLMResponse = await self.main_llm.chat(messages=self.messages, tools=tool_definitions)
            print(f"LLM content：{response.content}")
            print(f"LLM reasoning："f"{response.reasoning_content}")
            print(f"LLM tool_calls："f"{response.tool_calls}")
        except Exception as e:
            logger.exception(f"{self.name} LLM 调用失败")
            self.status = AgentState.ERROR
            self.final_answer = {"error": f"LLM 调用失败: {str(e)}"}
            self.msg_sender.agent_report(agent=self, event=AgentEvent.ERROR, output=self.final_answer)
            return False
        self.add_assistant_message(response)
        # 4. 如果模型产生 Tool Calls
        if response.tool_calls:
            self.cur_tool_calls = response.tool_calls
            self.msg_sender.agent_report(agent=self, event=AgentEvent.THINK,
                output={
                    "reasoning": response.reasoning_content,
                    "toolCalls": [
                        {
                            "toolCallId": tool_call.id,
                            "tool": tool_call.name,
                            "arguments": tool_call.arguments,
                        }
                        for tool_call in response.tool_calls
                    ]
                }
            )
            return True
        # 5. 没有 Tool Call
        self.status = AgentState.FINISHED
        self.final_answer = (response.content or "")
        self.msg_sender.agent_report(agent=self, event=AgentEvent.FINISH,
             output={
                "content": response.content or "",
                "reasoning": (
                    response.reasoning_content
                )
            }
        )
        return False


    async def act(self):
        print("=== ACT START ===")
        if not self.cur_tool_calls:
            self.status = AgentState.ERROR
            result = {"success": False, "error_type": "NO_TOOL_CALL", "message": "当前没有可执行的 Tool Call"}
            self.msg_sender.agent_report(agent=self, event=AgentEvent.ERROR, output=result)
            return result
        results = []
        for tool_call in self.cur_tool_calls:
            tool_id = tool_call.id
            tool_name = tool_call.name
            tool_args = tool_call.arguments
            self.status = AgentState.EXECUTING
            self.msg_sender.agent_report(agent=self, event=AgentEvent.TOOL_CALL,
                output={
                    "toolCallId": tool_id,
                    "tool": tool_name,
                    "arguments": tool_args,
                }
            )
            print(f"使用工具 {tool_name}，" f"toolCallId={tool_id}，" f"传入参数={tool_args}")
            # 1. 死循环检测
            if self.manager.checkLoop(tool_name, tool_args, self.action_history):
                tool_res = {"success": False, "error_type": "LOOP_DETECTED", "tool": tool_name, "message": "AI陷入死循环"}
            else:
                try:
                    # 2. 参数校验
                    validated_args = self.manager.check_tool_Input(tool_name, tool_args, self.tools_schemas)
                    # 3. 执行工具
                    tool_res = await self.tools_schemas.execute(tool_name=tool_name, args=validated_args, caller=self)
                    print(f"工具 {tool_name} 执行结果：{tool_res}")
                    # 4. 看门狗
                    self.last_time += datetime.timedelta(seconds=self.watch_dog)
                except json.JSONDecodeError as e:
                    tool_res = {"success": False, "error_type": "JSON_PARSE_ERROR", "tool": tool_name, "message": str(e)}
                except ValueError as e:
                    tool_res = {"success": False, "error_type": "TOOL_ARGUMENT_ERROR", "tool": tool_name, "message": str(e)}
                except PermissionError as e:
                    tool_res = {"success": False, "error_type": "TOOL_PERMISSION_ERROR", "tool": tool_name, "message": str(e)}
                except Exception as e:
                    print("Tool 执行异常: tool=%s",tool_name)
                    tool_res = {"success": False, "error_type": "TOOL_EXECUTION_ERROR", "tool": tool_name, "message": str(e)}

            # 5. 标准 Tool Message
            self.add_tool_message(tool_call_id=tool_id, result=tool_res)
            print(f"Tool Message 已加入历史：" f"tool_call_id={tool_id}")
            self.msg_sender.agent_report(agent=self,event=AgentEvent.TOOL_RESULT,
                output={
                    "toolCallId": tool_id,
                    "tool": tool_name,
                    "result": tool_res,
                }
            )
            results.append(tool_res)
        # 本轮执行完毕，下一次 run loop 再进入 think()
        self.cur_tool_call = []
        print("=== ACT END ===")
        return results

    def cleanup(self):
        self.messages.clear()
        self.action_history.clear()
