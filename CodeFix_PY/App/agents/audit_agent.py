import logging
from .tool_executor import ToolExecutor
from .prompts import SYSTEM_PROMPT_TEMPLATE  # 导入提示词

logger = logging.getLogger(__name__)

class AuditAgent(ToolExecutor):
    def __init__(self):
        super().__init__()
        self.name = "JavaFixer"
        self.window_size = 30
        self.max_iterations = 5
        self.systemPrompt = SYSTEM_PROMPT_TEMPLATE
        print("Agent已经初始化完成")
