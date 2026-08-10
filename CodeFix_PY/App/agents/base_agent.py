from abc import ABC, abstractmethod

from App.tools.tool_registry import registry
from .agent_state import AgentState
import datetime
from App.infrastructure.memory.message_manager import MessageManager
from .context.agent_context import AgentContext


class BaseAgent(ABC):
    def __init__(self, context: AgentContext, base_message = None):
        self.name = "Default"
        self.context = context # 上下文容器
        self.main_llm = context.main_llm # 任务模型
        self.compress_llm = context.compress_llm # 压缩模型
        self.msg_sender = context.msg_sender # 消息发送器
        self.base_message = base_message # 消息基类
        self.window_size = 10 # 窗口大小
        self.max_iterations = 30  # 防止死循环
        self.messages = []  # 维护对话历史（上下文）
        self.systemPrompt = None # 系统提示词
        self.tools = registry.tools # 工具
        self.tools_schemas = registry # 工具入参规则
        self.final_answer = None # 最终回复
        self.status = AgentState.IDLE # Agent状态
        self.last_time = None # 上一次模型开始执行时间
        self.watch_dog = 30 # 看门狗存活时长上限
        self.manager = MessageManager()

    def add_message(self, role, content):
        self.messages.append({"role": role, "content": content})
        if len(self.messages) > self.window_size:
            self.messages = self.messages[-self.window_size:]

    # def get_context(self):
    #     # 构建模型可理解的上下文格式
    #     context = " ".join([f"{role}:{content}" for role, content in reversed(self.messages)])
    #     return context if context else "系统初始化完成"

    async def run(self, question: str):
        self.status = AgentState.THINKING
        cur_iterations = 0
        tool_desc = registry.get_tools_desc()
        # 构造提示词
        prompt = self.systemPrompt.format(name=self.name, tool_desc=tool_desc, question=question)
        self.add_message("user", prompt)
        self.last_time = datetime.datetime.now()
        # 进入ReAct循环
        while self.status !=  AgentState.FINISHED and cur_iterations < self.max_iterations:
            await self.manager.compress_history_msg(self.window_size, self.messages, self.compress_llm)
            cur_time = datetime.datetime.now()
            # 当前时间 - 过去时间 > 30s(watch_dog) ? 超时 : 未超时更新过去时间;
            if cur_time - self.last_time > datetime.timedelta(seconds=self.watch_dog):
                self.status = AgentState.ERROR
                self.final_answer = f"Error: AI 推理超时（{self.watch_dog}），已强制终止。"
                self.msg_sender.agent_report(self.final_answer, agent=self)
                break
            audit_report = await self.step()
            print(f"当前Agent:{self.name}")
            print(f"===> step{cur_iterations + 1}：【{audit_report[:100]}...】")
            cur_iterations += 1
            if self.status == AgentState.FINISHED:
                self.msg_sender.agent_report(self.final_answer, agent=self)
                break
        self.cleanup()
        self.status = AgentState.IDLE
        return self.final_answer

    @abstractmethod
    def step(self):
        pass
    @abstractmethod
    def cleanup(self):
        pass