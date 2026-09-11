import asyncio
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from App.agents.context.agent_run_context import AgentRunContext
from App.agents.control.execution_gate import ExecutionGate
from App.config import config
from App.infrastructure.files_search.ripgrep_manager import RipgrepManager
from App.models.agent_message import AgentMessage
from App.models.workspace_info import WorkspaceInfo


@dataclass(frozen=True)
class AgentContext:
    main_llm: Any
    compress_llm: Any
    msg_sender: Any
    working_memory_store: Any

    def create_run_context(self, msg: AgentMessage, cancel_event: asyncio.Event):
        workspace = WorkspaceInfo(
            workspace_id=msg.workspace_id,
            root_path=Path(msg.root_path)
        )
        return AgentRunContext(
            task_id=msg.task_id,
            run_id=msg.run_id,
            session_id=msg.session_id,
            workspace=workspace,
            execution_gate=ExecutionGate(),
            rg_manager=RipgrepManager(),
            cancel_event=cancel_event
        )