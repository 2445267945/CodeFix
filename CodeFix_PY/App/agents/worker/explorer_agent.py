# app/agents/workers/explorer_agent.py
from ..prompts import EXPLORER_PROMPT_TEMPLATE
from ..tool_executor import ToolExecutor

class ExplorerAgent(ToolExecutor):
    def __init__(self, main_llm, compress_llm):
        super().__init__()
        self.name = "Explorer"
        self.main_llm = main_llm
        self.compress_llm = compress_llm
        self.max_iterations = 3          # 分析任务通常 2-3 轮足够
        self.window_size = 20
        self.systemPrompt = EXPLORER_PROMPT_TEMPLATE