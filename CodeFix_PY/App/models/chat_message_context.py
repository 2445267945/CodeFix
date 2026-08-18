from pydantic import BaseModel, Field


class ChatMessageContext(BaseModel):
    role: str
    content: str
    timestamp: int = 0
