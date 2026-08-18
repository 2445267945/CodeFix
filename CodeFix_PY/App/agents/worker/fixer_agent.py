from ..prompts import FIXER_PROMPT_TEMPLATE
from ..tool_executor import ToolExecutor
from App.agents.manager.agent_tool_manager import AgentToolSet


class FixerAgent(ToolExecutor):
    def __init__(self, context, run_context, base_message, parent_agent):
        super().__init__(context, run_context, base_message, parent_agent)
        self.name = "Fixer"
        self.parent_agent = parent_agent
        self.max_iterations = 6  # 修复可能需要多次校验和修正
        self.window_size = 30
        self.allowed_tools = AgentToolSet.FIXER
        self.systemPrompt = FIXER_PROMPT_TEMPLATE
