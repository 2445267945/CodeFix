import json
from abc import ABC, abstractmethod

from .agent_model.llm_message import LLMMessage
from .agent_model.llm_response import LLMResponse
from .agent_model.working_memory import WorkingMemory
from .agent_state import AgentState
import datetime
from App.agents.memory.message_manager import MessageManager
from .context.agent_context import AgentContext
from .context.agent_run_context import AgentRunContext
from App.agents.memory.context_manager import ContextManager
from App.agents.agent_model.context_state import ContextState
from App.agents.metrics.runtime_metrics import RuntimeMetrics
from App.agents.timeout.timeout_manager import TimeoutManager
from ..agent_boost.tools.tool_registry import registry
from ..models.agent_result import AgentResult
from ..models.enum.agent_event import AgentEvent
from ..models.session_context import SessionContext
import logging

logger = logging.getLogger(__name__)

class BaseAgent(ABC):
    def __init__(self, context: AgentContext, run_context: AgentRunContext, base_message=None, parent_agent: str | None = None):
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
        self.max_iterations = 100  # 防止死循环
        self.current_step = 0  # 当前步数
        self.systemPrompt = None  # 系统提示词
        self.tools = registry.tools  # 工具
        self.tools_schemas = registry  # 工具入参规则
        self.final_answer = None  # 最终回复
        self.status = AgentState.IDLE  # Agent状态
        self.timeout_manager = TimeoutManager(llm_timeout=60.0, tool_timeout=120.0)
        self.metrics = RuntimeMetrics()
        self.manager = MessageManager()
        self.context_state = ContextState()
        self.context_manager = ContextManager()

    def add_user_message(self, content: str) -> None:
        self.context_state.messages.append(LLMMessage(role="user", content=content))
        self.context_state.messages = (
            self.context_manager.trim_messages(
                messages=self.context_state.messages,
                llm=self.main_llm,
                tools=self.get_tool_definitions(),
            )
        )

    def add_assistant_message(self, response: LLMResponse) -> None:
        self.context_state.messages.append(LLMMessage( role="assistant", content=response.content, reasoning_content=response.reasoning_content, tool_calls=response.tool_calls))
        self.context_state.messages = (
            self.context_manager.trim_messages(
                messages=self.context_state.messages,
                llm=self.main_llm,
                tools=self.get_tool_definitions(),
            )
        )

    def add_tool_message(self, tool_call_id: str, result: object) -> None:
        content = result if isinstance(result, str) else json.dumps(result, ensure_ascii=False)
        self.context_state.messages.append(LLMMessage(role="tool", tool_call_id=tool_call_id, content=content))
        self.context_state.messages = (
            self.context_manager.trim_messages(
                messages=self.context_state.messages,
                llm=self.main_llm,
                tools=self.get_tool_definitions()
            )
        )

    async def run(self, question: str, resume: bool = False, session_context: SessionContext | None = None):
        self.metrics.start()
        restored = False
        try:
            if resume:
                restored = await self.restore_working_memory()
                if not restored:
                    raise RuntimeError(f"任务 {self.base_message.task_id} " f"没有可恢复的 checkpoint")
            if not restored:
                # START / RETRY：从全新上下文开始
                self.build_initial_context(question=question, session_context=session_context)
            self.last_time = datetime.datetime.now()
            # 进入ReAct循环
            while self.status not in (AgentState.FINISHED, AgentState.ERROR, AgentState.CANCELLED):
                self.current_step += 1
                self.metrics.record_step(self.current_step)
                tool_definitions = self.tools_schemas.get_tool_definitions(self.allowed_tools)
                # 压缩
                compression_result = (
                    await self.context_manager.compress_if_needed(
                        state=self.context_state,
                        main_llm=self.main_llm,
                        compress_llm=self.compress_llm,
                        tools=tool_definitions,
                    )
                )
                if compression_result.compressed:
                    self.metrics.record_compression(before_tokens=compression_result.before_tokens, after_tokens=compression_result.after_tokens)
                await self.step()
                print(f"[CHECKPOINT START] step={self.current_step}")
                await self.checkpoint_working_memory()
                print(f"[CHECKPOINT END] step={self.current_step}")
                if self.run_context.cancel_event.is_set():
                    self.status = AgentState.CANCELLED
                    await self.checkpoint_working_memory()
                    self.msg_sender.agent_report(agent=self, event=AgentEvent.INTERRUPTED, output={"reason": "TASK_CANCELLED"}, runId=self.base_message.run_id)
                    return AgentResult.fail(agent_name=self.name, result={"reason": "TASK_CANCELLED"}, iterations=self.current_step)
                if self.current_step >= self.max_iterations:
                    self.status = AgentState.ERROR
                    self.final_answer = {"error": f"Error: 超过最大推理步数 {self.max_iterations}"}
                    print(self.final_answer)
                    break
                if self.status is AgentState.FINISHED:
                    self.metrics.finish()
                    logger.info("Runtime metrics: agent=%s %s", self.name, self.metrics.to_dict())
                    await self.clear_working_memory()
                    self.cleanup()
                    self.status = AgentState.IDLE
                    return AgentResult.ok(agent_name=self.name, result=self.final_answer, iterations=self.current_step)
            return AgentResult.fail(agent_name=self.name, result=self.final_answer, iterations=self.current_step)
        except Exception as e:
            self.metrics.record_error()
            self.metrics.finish()
            self.status = AgentState.ERROR
            if self.final_answer is None:
                self.final_answer = {"error": f"{self.name} 执行失败", "status": self.status.value}
            logger.exception("Agent 执行出错: agent=%s", self.name)
            return AgentResult.fail(agent_name=self.name, result=self.final_answer, iterations=self.current_step)

    # 恢复记忆
    async def restore_working_memory(self) -> bool:
        if self.base_message is None:
            return False
        memory = await self.working_memory_store.load(
            session_id=self.base_message.session_id,
            task_id=self.base_message.task_id,
            run_id=self.base_message.run_id,
            agent_name=self.name,
        )
        if memory is None:
            return False
        self.current_step = memory.step
        self.context_state.history_summary = (memory.history_summary)
        self.context_state.messages = (memory.recent_messages)
        self.status = AgentState.THINKING
        if memory.workspace_id != self.run_context.workspace.workspace_id:
            raise RuntimeError(
                f"任务 {self.base_message.task_id} 恢复失败："
                f"workspace_id 不一致，"
                f"checkpoint={memory.workspace_id}, "
                f"runtime={self.run_context.workspace.workspace_id}"
            )
        self.context_state.messages.append(
            LLMMessage(
                role="system",
                content=(
                    f"当前 Workspace ID 为 {self.run_context.workspace.workspace_id}。\n"
                    f"当前 Workspace 根目录为：{self.run_context.workspace.root_path}\n"
                    "后续所有文件路径都必须使用相对于当前 Workspace 根目录的路径。"
                    "不要再次在路径前添加 Workspace ID。"
                ),
            )
        )
        return True

    # 构建初始化记忆
    def build_initial_context(self, question: str, session_context=None) -> None:
        tool_desc = registry.get_tools_desc(allowed_tools=self.allowed_tools)
        system_prompt = self.systemPrompt.format(name=self.name, tool_desc=tool_desc)
        self.context_manager.initialize(
            state=self.context_state,
            question=question,
            system_prompt=system_prompt,
            session_context=session_context,
        )

    # 存储当前步骤的工作快照
    async def checkpoint_working_memory(self) -> None:
        print("[CHECKPOINT 1] build memory")
        if self.base_message is None:
            return
        memory = WorkingMemory(
            run_id=self.base_message.run_id,
            task_id=self.base_message.task_id,
            session_id=self.base_message.session_id,
            workspace_id=self.run_context.workspace.workspace_id,
            agent_name=self.name,
            step=self.current_step,
            status=self.status.value,
            question=self.base_message.question,
            history_summary=self.context_state.history_summary,
            recent_messages=self.context_state.messages,
        )
        print("[CHECKPOINT 2] before save")
        await self.working_memory_store.save(memory)
        print("[CHECKPOINT 3] after save")

    async def clear_working_memory(self) -> None:
        if self.base_message is None:
            return
        await self.working_memory_store.clear(
            session_id=self.base_message.session_id,
            task_id=self.base_message.task_id,
            run_id=self.base_message.run_id,
            agent_name=self.name,
        )

    def get_tool_definitions(self) -> list[dict]:
        return self.tools_schemas.get_tool_definitions(
            self.allowed_tools
        )

    def cleanup_context(self) -> None:
        self.context_state.messages.clear()
        self.context_state.history_summary = ""

    @abstractmethod
    def step(self):
        pass

    @abstractmethod
    def cleanup(self):
        pass
