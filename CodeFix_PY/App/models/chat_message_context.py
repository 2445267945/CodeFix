from pydantic import BaseModel


class ChatMessageContext(BaseModel):
    role: str
    content: str
    timestamp: int = 0
