from ..prompts import FIXER_PROMPT_TEMPLATE
from ..tool_executor import ToolExecutor

class FixerAgent(ToolExecutor):
    def __init__(self, context, base_message, parent_agent):
        super().__init__(context, base_message, parent_agent)
        self.name = "Fixer"
        self.parent_agent = parent_agent
        self.max_iterations = 6          # 修复可能需要多次校验和修正
        self.window_size = 30
        self.systemPrompt = FIXER_PROMPT_TEMPLATE