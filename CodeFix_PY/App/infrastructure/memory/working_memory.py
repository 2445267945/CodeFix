from pydantic import BaseModel, Field


class WorkingMemory(BaseModel):
    run_id: str
    task_id: str
    session_id: str
    agent_name: str
    step: int
    status: str
    question: str
    history_summary: str = ""
    recent_messages: list[dict] = Field(default_factory=list)