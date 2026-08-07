# app/agents/workers/fixer_agent.py
from ..prompts import FIXER_PROMPT_TEMPLATE
from ..tool_executor import ToolExecutor

class FixerAgent(ToolExecutor):
    def __init__(self, main_llm, compress_llm):
        super().__init__()
        self.name = "Fixer"
        self.main_llm = main_llm
        self.compress_llm = compress_llm
        self.max_iterations = 6          # 修复可能需要多次校验和修正
        self.window_size = 30
        self.systemPrompt = FIXER_PROMPT_TEMPLATE