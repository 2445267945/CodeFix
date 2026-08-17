from pydantic import BaseModel, Field

from App.models.chat_message_context import ChatMessageContext


class SessionContext(BaseModel):
    messages: list[ChatMessageContext] = Field(default_factory=list)