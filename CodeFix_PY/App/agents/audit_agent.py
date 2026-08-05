import logging
from typing import Optional

from .tool_executor import ToolExecutor
from ..services.llm_service import LLMService

logger = logging.getLogger(__name__)

class AuditAgent(ToolExecutor):
    def __init__(self, name: str, main_llm: Optional[LLMService], compress_llm: Optional[LLMService], window_size: int, max_iterations: int, systemPrompt: str):
        super().__init__()
        self.name = name
        self.window_size = window_size
        self.max_iterations = max_iterations
        self.main_llm = main_llm
        self.compress_llm = compress_llm
        self.systemPrompt = systemPrompt
        print(f"{name} Agent已经初始化完成")
