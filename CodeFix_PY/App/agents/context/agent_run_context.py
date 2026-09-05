import asyncio
from dataclasses import dataclass

from App.agents.control.execution_gate import ExecutionGate
from App.models.workspace_info import WorkspaceInfo
from App.infrastructure.files_search.ripgrep_manager import RipgrepManager

@dataclass(frozen=True)
class AgentRunContext:
    task_id: str
    run_id: str
    session_id: str
    workspace: WorkspaceInfo
    execution_gate: ExecutionGate
    rg_manager: RipgrepManager
    cancel_event: asyncio.Event
