import json
import logging
from pydantic import TypeAdapter, ValidationError
from App.agents.decision.agent_decision import AgentDecision

logger = logging.getLogger(__name__)

# AgentDecision 是 Annotated Union
# 使用 TypeAdapter 负责运行时校验和反序列化
agent_decision_adapter = TypeAdapter(AgentDecision)


class AgentDecisionParseError(Exception):
    """LLM 输出无法解析为合法 AgentDecision。"""
    pass


def parse_llm_response(response: str) -> AgentDecision:
    """
    将 LLM 原始响应解析并验证为 AgentDecision。
    流程：
        LLM Response
            ↓
        JSON 解析
            ↓
        AgentDecision Schema 校验
            ↓
        ToolCallDecision / FinishDecision
    注意：
    该函数不负责：
    - 执行工具
    - 修改 Agent 状态
    - 重试 LLM
    - 修复业务逻辑
    """
    if not response or not response.strip():
        raise AgentDecisionParseError("LLM 返回了空响应")
    # 1. JSON 解析
    try:
        data = json.loads(response)
    except json.JSONDecodeError as e:
        raise AgentDecisionParseError(f"LLM 返回的内容不是合法 JSON: {e}") from e
    # 2. 顶层必须是 JSON Object
    if not isinstance(data, dict):
        raise AgentDecisionParseError("LLM 返回的 JSON 顶层结构必须是 object")
    # 3. AgentDecision Schema 校验
    try:
        return agent_decision_adapter.validate_python(data)
    except ValidationError as e:
        logger.warning("LLM 输出不符合 AgentDecision Schema: %s",e)
        raise AgentDecisionParseError(f"LLM 输出不符合 AgentDecision Schema: {e}") from e