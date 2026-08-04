from abc import ABC, abstractmethod
from App.tools.tool_registry import registry
from .agent_state import AgentState
import datetime

class BaseAgent(ABC):
    def __init__(self):
        self.name = "Default"
        self.window_size = 10
        self.max_iterations = 30  # 防止死循环
        self.messages = []  # 维护对话历史（上下文）
        self.systemPrompt = ""
        self.tools = registry.tools
        self.tools_schemas = registry
        self.final_answer = None
        self.status = AgentState.IDLE
        self.last_time = None
        self.watch_dog = 30

    def add_message(self, role, content):
        self.messages.append({"role": role, "content": content})
        if len(self.messages) > self.window_size:
            self.messages = self.messages[-self.window_size:]

    def get_context(self):
        # 构建模型可理解的上下文格式
        context = " ".join([f"{role}:{content}" for role, content in reversed(self.messages)])
        return context if context else "系统初始化完成"

    async def run(self, question: str):
        self.status = AgentState.RUNNING
        cur_iterations = 0
        tool_desc = registry.get_tools_desc()
        prompt = self.systemPrompt.format(name=self.name, tool_desc=tool_desc, question=question)
        self.add_message("user", prompt)
        self.last_time = datetime.datetime.now()
        while self.status !=  AgentState.FINISHED and cur_iterations < self.max_iterations:
            cur_time = datetime.datetime.now()
            # 当前时间 - 过去时间 > 30s(watch_dog) ? 超时 : 未超时更新过去时间;
            if cur_time - self.last_time > datetime.timedelta(seconds=self.watch_dog):
                self.status = AgentState.ERROR
                self.final_answer = f"Error: AI 推理超时（{self.watch_dog}），已强制终止。"
                break
            audit_report = await self.step()
            print(f"===> step{cur_iterations + 1}：【{audit_report}】")
            cur_iterations += 1
            if self.status == AgentState.FINISHED:
                break

        self.cleanup()
        return self.final_answer

    @abstractmethod
    def step(self):
        pass
    @abstractmethod
    def cleanup(self):
        pass