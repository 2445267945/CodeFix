from pydantic import BaseModel, Field

from App.agents.agent_model.llm_message import LLMMessage


class WorkingMemory(BaseModel):
    run_id: str
    task_id: str
    session_id: str
    agent_name: str
    step: int
    status: str
    question: str
    history_summary: str = ""
    recent_messages: list[LLMMessage] = Field(default_factory=list)
