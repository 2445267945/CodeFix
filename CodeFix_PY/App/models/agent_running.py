from dataclasses import dataclass
import asyncio


@dataclass
class AgentRunning:
    task_id: str
    run_id: str
    session_id: str
    agent: "SupervisorAgent"
    task: asyncio.Task
    cancel_event: asyncio.Event
