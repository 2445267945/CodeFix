from ..prompts import EXPLORER_PROMPT_TEMPLATE
from ..tool_executor import ToolExecutor
from App.agents.manager.agent_tool_manager import AgentToolSet


class ExplorerAgent(ToolExecutor):
    def __init__(self, context, run_context, base_message, parent_agent):
        super().__init__(context, run_context, base_message, parent_agent)
        self.name = "Explorer"
        self.parent_agent = parent_agent
        self.max_iterations = 100  # 分析任务通常 2-3 轮足够
        self.window_size = 50
        self.allowed_tools = AgentToolSet.EXPLORER
        self.systemPrompt = EXPLORER_PROMPT_TEMPLATE
