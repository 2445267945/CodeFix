from abc import ABC, abstractmethod
from App.tools.tool_registry import registry
from .agent_state import AgentState

class BaseAgent(ABC):
    def __init__(self):
        self.name = ""
        self.window_size = 0
        self.max_iterations = 0  # 防止死循环
        self.messages = []  # 维护对话历史（上下文）
        self.systemPrompt = ""
        self.tools = registry.tools
        self.final_answer = None
        self.status = AgentState.IDLE

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
        while cur_iterations < self.max_iterations and self.status !=  AgentState.FINISHED:
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