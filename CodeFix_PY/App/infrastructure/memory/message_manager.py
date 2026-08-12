from pydantic import ValidationError
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


    async def compress_history_msg(self, window_size, messages, llm, history_summary: str = ""):
        """
        检查消息长度，若超过阈值则调用 LLM 压缩旧消"
        :param keep_recent:
        :param messages:
        :param llm:
        :return:
        """
        compress_threshold = 0.7
        keep_ratio = 0.3

        msg_len = len(messages)

        # 未达到阈值，不压缩
        if msg_len / window_size < compress_threshold:
            return history_summary, messages

        keep = max(1, round(window_size * keep_ratio))

        head = messages[:-keep]
        tail = messages[-keep:]

        compress_prompt = COMPRESS_PROMPT_TEMPLATE.format(history_summary=history_summary, head=head)
        try:
            summary_response = await llm.chat([{"role": "user", "content": compress_prompt}])
            new_summary = HistoryMessage(role="system", content=summary_response.strip())
        except Exception as e:
            print(f"摘要生成失败，保留原历史摘要并直接截断: {e}")
            new_summary = history_summary
        return new_summary, tail