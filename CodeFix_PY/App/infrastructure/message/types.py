from App.models.agent_command_message import AgentCommandMessage
from App.models.agent_message import AgentMessage

MESSAGE_TYPE_MAP = {
    "AGENT_TASK": AgentMessage,
    "AGENT_COMMAND": AgentCommandMessage,
    # "SYSTEM_MSG": SystemMessage,   # 未来扩展
    # "USER_MSG": UserMessage,       # 未来扩展
}
