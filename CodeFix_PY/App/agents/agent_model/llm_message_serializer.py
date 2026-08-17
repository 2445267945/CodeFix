from dataclasses import asdict, is_dataclass

from App.agents.agent_model.llm_message import LLMMessage
from App.agents.agent_model.tool_call import ToolCall

# 序列话
def serialize_message(message: LLMMessage) -> dict:
    if is_dataclass(message):
        return asdict(message)
    if hasattr(message, "model_dump"):
        return message.model_dump()
    if isinstance(message, dict):
        return message
    raise TypeError(f"不支持的消息类型: {type(message)}")

# 反序列化
def deserialize_message(data: dict) -> LLMMessage:
    tool_calls_data = data.get("tool_calls", [])

    tool_calls = [
        ToolCall(
            id=item["id"],
            name=item["name"],
            arguments=item.get(
                "arguments",
                {}
            ),
        )
        for item in tool_calls_data
    ]
    return LLMMessage(
        role=data["role"],
        content=data.get("content"),
        reasoning_content=data.get("reasoning_content"),
        tool_calls=tool_calls,
        tool_call_id=data.get( "tool_call_id"),
        name=data.get("name"),
    )