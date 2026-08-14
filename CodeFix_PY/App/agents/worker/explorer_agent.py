from ..prompts import EXPLORER_PROMPT_TEMPLATE
from ..tool_executor import ToolExecutor

class ExplorerAgent(ToolExecutor):
    def __init__(self, context, base_message, parent_agent):
        super().__init__(context, base_message, parent_agent)
        self.name = "Explorer"
        self.parent_agent = parent_agent
        self.max_iterations = 3          # 分析任务通常 2-3 轮足够
        self.window_size = 20
        self.systemPrompt = EXPLORER_PROMPT_TEMPLATE