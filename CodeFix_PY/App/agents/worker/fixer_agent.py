from ..prompts import FIXER_PROMPT_TEMPLATE
from ..tool_executor import ToolExecutor

class FixerAgent(ToolExecutor):
    def __init__(self, context, base_message):
        super().__init__(context, base_message)
        self.name = "Fixer"
        self.max_iterations = 6          # 修复可能需要多次校验和修正
        self.window_size = 30
        self.systemPrompt = FIXER_PROMPT_TEMPLATE