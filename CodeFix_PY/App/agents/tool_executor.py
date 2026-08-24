import json
import logging

import uuid

from App.agents.control.execution_gate import ExecutionDecision
from App.agents.control.tool_policy import (
    ToolPermission,
    get_tool_permission,
)
from .agent_model.llm_response import LLMResponse
from .agent_model.tool_call import ToolCall
from .agent_state import AgentState
from .context.agent_context import AgentContext
from .context.agent_run_context import AgentRunContext
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
            self.msg_sender.agent_report(agent=self, event=AgentEvent.ERROR, output={"error": self.final_answer}, runId=self.base_message.run_id)
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
            self.msg_sender.agent_report(agent=self, event=AgentEvent.ERROR, output=self.final_answer, runId=self.base_message.run_id)
            return False
        self.add_assistant_message(response)
        # 4. 如果模型产生 Tool Calls
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
            },
            runId=self.base_message.run_id
        )
        if response.tool_calls:
            self.cur_tool_calls = response.tool_calls
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
            },
            runId=self.base_message.run_id
        )
        return False


    async def act(self):
        print("=== ACT START ===")
        if not self.cur_tool_calls:
            self.status = AgentState.ERROR
            result = {"success": False, "error_type": "NO_TOOL_CALL", "message": "当前没有可执行的 Tool Call"}
            self.msg_sender.agent_report(agent=self, event=AgentEvent.ERROR, output=result, runId=self.base_message.run_id)
            return result
        results = []
        for tool_call in self.cur_tool_calls:
            tool_id = tool_call.id
            tool_name = tool_call.name
            tool_args = tool_call.arguments

            print(f"使用工具 {tool_name}，" f"toolCallId={tool_id}，" f"传入参数={tool_args}")
            # 1. 死循环检测
            if self.manager.checkLoop(tool_name, tool_args, self.action_history):
                tool_res = {"success": False, "error_type": "LOOP_DETECTED", "tool": tool_name, "message": "AI陷入死循环"}
            else:
                try:
                    # 2. 参数校验
                    validated_args = self.manager.check_tool_Input(tool_name, tool_args, self.tools_schemas)
                    # 3. Runtime 控制 / 人工审批
                    allowed, action_id = await self.before_tool_call(tool_call, validated_args)
                    if not allowed:
                        tool_res = {
                            "success": False,
                            "error_type": "USER_REJECTED",
                            "tool": tool_name,
                            "message": "用户拒绝执行该操作。"
                        }
                        self.add_tool_message(tool_call_id=tool_id, result=tool_res)
                        results.append(tool_res)
                        self.msg_sender.agent_report(
                            agent=self,
                            event=AgentEvent.TOOL_RESULT,
                            output={
                                "actionId": action_id,
                                "toolCallId": tool_id,
                                "tool": tool_name,
                                "result": tool_res,
                            },
                            runId=self.base_message.run_id
                        )
                        continue
                    # 4. 执行工具
                    self.status = AgentState.EXECUTING
                    self.msg_sender.agent_report(agent=self, event=AgentEvent.TOOL_CALL,
                         output={
                             "toolCallId": tool_id,
                             "tool": tool_name,
                             "arguments": validated_args,
                         },
                         runId=self.base_message.run_id
                    )
                    tool_res = await self.tools_schemas.execute(tool_name=tool_name, args=validated_args, caller=self)
                    print(f"工具 {tool_name} 执行结果：{tool_res}")
                    # 5. 看门狗
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

            # 6. 标准 Tool Message
            self.add_tool_message(tool_call_id=tool_id, result=tool_res)
            print(f"Tool Message 已加入历史：" f"tool_call_id={tool_id}")
            self.msg_sender.agent_report(agent=self,event=AgentEvent.TOOL_RESULT,
                output={
                    "toolCallId": tool_id,
                    "tool": tool_name,
                    "result": tool_res,
                },
                runId=self.base_message.run_id
            )
            results.append(tool_res)
        # 本轮执行完毕，下一次 run loop 再进入 think()
        self.cur_tool_calls = []
        print("=== ACT END ===")
        return results

    def cleanup(self):
        self.messages.clear()
        self.action_history.clear()

    async def before_tool_call(self, tool_call: ToolCall, validated_args: dict) -> tuple[bool, str | None]:
        """
        单个 Tool Call 的 Runtime 控制入口。
        返回：
        - (True, None)
            自动允许执行
        - (True, action_id)
            经过确认后允许执行
        - (False, action_id)
            用户拒绝，不执行
        """
        tool_name = tool_call.name
        permission = get_tool_permission(tool_name)

        # 1. 自动允许
        if permission is ToolPermission.AUTO:
            return True, None

        # 2. 需要人工确认
        action_id = str(uuid.uuid4())
        self.status = AgentState.BLOCKED
        self.msg_sender.agent_report(
            agent=self,
            event=AgentEvent.TOOL_WAITING,
            output={
                "toolCallId": tool_call.id,
                "tool": tool_name,
                "arguments": validated_args,
                "requiresApproval": True,
            },
            runId=self.base_message.run_id,
            actionId=action_id
        )

        logger.info("Agent 等待人工确认: runId=%s, actionId=%s, tool=%s", self.base_message.run_id, action_id, tool_name)

        # 3. 真正阻塞当前 Agent 协程
        decision = await self.run_context.execution_gate.wait_for_decision(action_id)

        # 4. 用户批准
        if decision is ExecutionDecision.ALLOW:
            self.status = AgentState.EXECUTING
            logger.info("Agent Action approved: runId=%s, actionId=%s, tool=%s", self.base_message.run_id, action_id, tool_name,)
            return True, action_id

        # 5. 用户拒绝
        if decision is ExecutionDecision.DENY:
            logger.info("Agent Action rejected: runId=%s, actionId=%s, tool=%s", self.base_message.run_id, action_id, tool_name,)
            return False, action_id
        # 理论上不应该到这里
        logger.error("未知 ExecutionDecision: %s", decision)
        self.status = AgentState.ERROR
        return False, action_id