from dataclasses import dataclass

from App.models.workspace_info import WorkspaceInfo


@dataclass(frozen=True)
class AgentRunContext:
    task_id: str
    run_id: str
    session_id: str
    workspace: WorkspaceInfo