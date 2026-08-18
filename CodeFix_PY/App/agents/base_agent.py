import json
from abc import ABC, abstractmethod

from App.tools.tool_registry import registry
from .agent_model.llm_message import LLMMessage
from .agent_model.llm_response import LLMResponse
from .agent_state import AgentState
import datetime
from App.infrastructure.memory.message_manager import MessageManager
from .context.agent_context import AgentContext
from .context.agent_run_context import AgentRunContext
from ..infrastructure.memory.working_memory import WorkingMemory
from ..models.agent_result import AgentResult
from ..models.session_context import SessionContext


class BaseAgent(ABC):
    def __init__(self, context: AgentContext, run_context: AgentRunContext, base_message=None,
                 parent_agent: str | None = None):
        self.name = "Default"
        self.parent_agent = parent_agent  # 父agent名字
        self.allowed_tools: tuple[str, ...] = ()  # 当前agent允许使用的工具
        self.context = context  # 上下文容器（全局）
        self.run_context = run_context  # 单次执行的上下文容器（局部）
        self.main_llm = context.main_llm  # 任务模型
        self.compress_llm = context.compress_llm  # 压缩模型
        self.msg_sender = context.msg_sender  # 消息发送器
        self.working_memory_store = context.working_memory_store  # 工作内容记忆snapshot
        self.base_message = base_message  # 消息基类
        self.window_size = 50  # 窗口大小
        self.max_iterations = 30  # 防止死循环
        self.current_step = 0  # 当前步数
        self.messages: list[LLMMessage] = []  # 维护对话历史（上下文）
        self.systemPrompt = None  # 系统提示词
        self.tools = registry.tools  # 工具
        self.tools_schemas = registry  # 工具入参规则
        self.final_answer = None  # 最终回复
        self.status = AgentState.IDLE  # Agent状态
        self.last_time = None  # 上一次模型开始执行时间
        self.watch_dog = 30  # 看门狗存活时长上限
        self.history_summary = ""
        self.manager = MessageManager()

    def add_user_message(self, content: str) -> None:
        self.messages.append(LLMMessage(role="user", content=content))
        self.messages = self.manager.trim_messages(self.window_size, self.messages)

    def add_assistant_message(self, response: LLMResponse) -> None:
        self.messages.append(
            LLMMessage(
                role="assistant",
                content=response.content,
                reasoning_content=response.reasoning_content,
                tool_calls=response.tool_calls,
            )
        )
        self.messages = self.manager.trim_messages(self.window_size, self.messages)

    def add_tool_message(self, tool_call_id: str, result: object) -> None:
        content = result if isinstance(result, str) else json.dumps(result, ensure_ascii=False)
        self.messages.append(LLMMessage(role="tool", tool_call_id=tool_call_id, content=content))
        self.messages = self.manager.trim_messages(self.window_size, self.messages)

    async def run(self, question: str, resume: bool = False, session_context: SessionContext | None = None):
        self.status = AgentState.THINKING
        restored = False
        try:
            if resume:
                restored = await self.restore_working_memory()
                if not restored:
                    raise RuntimeError(f"任务 {self.base_message.task_id} " f"没有可恢复的 checkpoint")
            if not restored:
                # START / RETRY：从全新上下文开始
                self.build_initial_messages(question=question, session_context=session_context)
            self.last_time = datetime.datetime.now()
            # 进入ReAct循环
            while self.status not in (AgentState.FINISHED, AgentState.ERROR):
                self.current_step += 1
                self.history_summary, self.messages = await self.manager.compress_history_msg(self.window_size,
                                                                                              self.messages,
                                                                                              self.compress_llm,
                                                                                              self.history_summary)
                cur_time = datetime.datetime.now()
                # 当前时间 - 过去时间 > 30s(watch_dog) ? 超时 : 未超时更新过去时间;
                if cur_time - self.last_time > datetime.timedelta(seconds=self.watch_dog):
                    self.status = AgentState.ERROR
                    self.final_answer = {"error": f"Error: AI 推理超时（{self.watch_dog}），已强制终止。"}
                    self.msg_sender.agent_report(agent=self, event="ERROR", output=self.final_answer)
                    break
                await self.step()
                await self.checkpoint_working_memory()
                if self.current_step >= self.max_iterations:
                    self.status = AgentState.ERROR
                    self.final_answer = {"error": f"Error: 超过最大推理步数 {self.max_iterations}"}
                    break
                if self.status is AgentState.FINISHED:
                    await self.clear_working_memory()
                    self.cleanup()
                    self.status = AgentState.IDLE
                    return AgentResult.ok(agent_name=self.name, result=self.final_answer, iterations=self.current_step)
            return AgentResult.fail(agent_name=self.name, result=self.final_answer, iterations=self.current_step)
        except Exception as e:
            self.status = AgentState.ERROR
            if self.final_answer is None:
                self.final_answer = {"error": f"{self.name} 执行失败", "status": self.status.value}
            print(f"出现错误：{e}")
            return AgentResult.fail(agent_name=self.name, result=self.final_answer, iterations=self.current_step)

    # 恢复记忆
    async def restore_working_memory(self) -> bool:
        if self.base_message is None:
            return False
        memory = await self.working_memory_store.load(
            session_id=self.base_message.session_id,
            task_id=self.base_message.task_id,
            run_id=self.base_message.run_id,
            agent_name=self.name
        )
        if memory is None:
            return False
        self.current_step = memory.step
        self.history_summary = memory.history_summary
        self.messages = memory.recent_messages
        # 恢复后重新进入推理状态
        self.status = AgentState.THINKING
        return True

    # 构建初始化记忆
    def build_initial_messages(self, question: str, session_context: SessionContext | None = None) -> None:
        """
        构建新 Run 的初始消息。
        结构：
        system
          ↓
        session history
          ↓
        current user question
        """
        messages = []
        # 1. System Prompt
        tool_desc = registry.get_tools_desc(allowed_tools=self.allowed_tools)
        system_prompt = self.systemPrompt.format(name=self.name, tool_desc=tool_desc)
        messages.append(LLMMessage(role="system", content=system_prompt))
        # 2. Session 历史
        if session_context:
            for item in session_context.messages:
                role = item.role.lower()
                if role not in ("user", "assistant", "system"):
                    continue
                messages.append(LLMMessage(role=role, content=item.content))
        # 3. 当前用户问题
        if question:
            messages.append(LLMMessage(role="user", content=question))
        self.messages = messages

    # 存储当前步骤的工作快照
    async def checkpoint_working_memory(self) -> None:
        if self.base_message is None:
            return
        memory = WorkingMemory(
            run_id=self.base_message.run_id,
            task_id=self.base_message.task_id,
            session_id=self.base_message.session_id,
            agent_name=self.name,
            step=self.current_step,
            status=self.status.value,
            question=self.base_message.question,
            summary=self.history_summary,
            recent_messages=self.messages,
        )
        await self.working_memory_store.save(memory)

    async def clear_working_memory(self) -> None:
        if self.base_message is None:
            return
        await self.working_memory_store.clear(
            session_id=self.base_message.session_id,
            task_id=self.base_message.task_id,
            run_id=self.base_message.run_id,
            agent_name=self.name,
        )

    @abstractmethod
    def step(self):
        pass

    @abstractmethod
    def cleanup(self):
        pass
