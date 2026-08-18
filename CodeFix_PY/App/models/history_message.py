from pydantic import BaseModel


class HistoryMessage(BaseModel):
    role: str
    content: str
