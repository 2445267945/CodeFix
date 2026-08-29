from pydantic import ValidationError

from App.agents.agent_model.llm_message import LLMMessage
from App.agents.prompts import COMPRESS_PROMPT_TEMPLATE
from App.models.history_message import HistoryMessage

TOOL_LIMITS = {
    "search_manual": 3,
    "verify_java_syntax": 5,
    "run_explorer": 2,
    "run_fixer": 3,
}


class MessageManager:
    def check_tool_Input(self, tool_name, tool_args, tools_schemas):
        """
        AI给出的参数校验
        :param tool_name:
        :param tool_args:
        :return:
        """
        if tool_name in tools_schemas.schemas:
            try:
                validated_args = tools_schemas.schemas[tool_name](**tool_args)
                return validated_args.model_dump()
            except ValidationError as e:
                # 直接抛出异常，让上层捕获
                raise ValueError(f"参数校验失败: {e.errors()}") from e
        return tool_args

    def checkLoop(self, tool_name, tool_args, action_history) -> bool:
        """
        检测 Agent 是否陷入重复工具调用或工具调用次数过多。

        规则：
        1. 最近 3 次完全相同的工具 + 参数 -> 死循环
        2. 同一个工具调用次数超过该工具限制 -> 死循环
        """
        action_signature = f"{tool_name}:{tool_args}"
        # 先加入当前动作
        action_history.append(action_signature)
        # 只保留最近 5 次
        if len(action_history) > 5:
            action_history.pop(0)
        # 规则1：最近3次完全相同
        if len(action_history) >= 3:
            if len(set(action_history[-3:])) == 1:
                return True
        # 规则2：同一个工具调用次数达到上限
        tool_count = sum(
            1 for action in action_history
            if action.startswith(f"{tool_name}:")
        )
        if tool_count >= TOOL_LIMITS.get(tool_name, 5):
            return True
        return False
