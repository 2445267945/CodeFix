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

    async def compress_history_msg(self, window_size, messages, llm, history_summary: str = ""):
        """
        当消息数量达到窗口阈值时：

        1. 按完整 Message Group 拆分；
        2. 较旧 Group 压缩成 history_summary；
        3. 最近 Group 原样保留。

        Tool Call 与对应 Tool Result 不允许被拆开。
        """
        compress_threshold = 0.8
        keep_ratio = 0.5
        msg_len = len(messages)
        # 未达到压缩阈值
        if msg_len < window_size * compress_threshold:
            return history_summary, messages
        keep = max(1, round(window_size * keep_ratio))

        # 按完整消息组拆分
        head, tail = self.split_message_groups(messages, keep_messages=keep)
        # 没有可压缩内容
        if not head:
            return history_summary, tail
        # 转成压缩模型容易理解的文本
        head_text = "\n".join(
            self.format_message_for_compression(message)
            for message in head
        )
        compress_prompt = COMPRESS_PROMPT_TEMPLATE.format(history_summary=history_summary, head=head_text)

        try:
            summary_response = await llm.chat(
                messages=[LLMMessage(role="user", content=compress_prompt)],
                tools=None
            )
            new_summary = (summary_response.content or "")
        except Exception as e:
            print(f"摘要生成失败，"f"保留原历史摘要并直接截断: {e}")
            new_summary = history_summary
        return new_summary, tail

    def format_message_for_compression(self, message: LLMMessage) -> str:
        parts = [f"role={message.role}"]

        if message.content is not None:
            parts.append(f"content={message.content}")

        if message.reasoning_content:
            parts.append(f"reasoning_content={message.reasoning_content}")

        if message.tool_calls:
            parts.append(
                "tool_calls=" +
                str([
                    {
                        "id": tool.id,
                        "name": tool.name,
                        "arguments": tool.arguments
                    }
                    for tool in message.tool_calls
                ])
            )
        if message.tool_call_id:
            parts.append(f"tool_call_id={message.tool_call_id}")
        return "\n".join(parts)

    def split_message_groups(self, messages: list[LLMMessage], keep_messages: int) \
            -> tuple[list[LLMMessage], list[LLMMessage]]:
        groups = self.build_message_groups(messages)
        tail_groups = []
        tail_count = 0
        for group in reversed(groups):
            # 第一个 group 即使超过 keep_messages 也必须完整保留
            if (tail_groups and tail_count + len(group) > keep_messages):
                break
            tail_groups.append(group)
            tail_count += len(group)
        tail_groups.reverse()
        tail = [
            message
            for group in tail_groups
            for message in group
        ]
        tail_ids = {
            id(message)
            for message in tail
        }
        head = [
            message
            for message in messages
            if id(message) not in tail_ids
        ]
        return head, tail

    def build_message_groups(self, messages: list[LLMMessage] | None = None) -> list[list[LLMMessage]]:
        """
        将消息划分为不会被拆散的逻辑组。

        规则：
        1. assistant 不带 tool_calls → 独立消息组
        2. assistant 带 tool_calls → 与后续对应的 tool messages 组成一组
        3. 普通 user/system 消息 → 独立消息组
        """
        groups: list[list[LLMMessage]] = []

        i = 0
        while i < len(messages):
            current = messages[i]
            # assistant 发起 Tool Call
            if (current.role == "assistant" and current.tool_calls):
                group = [current]
                expected_tool_ids = {
                    tool_call.id
                    for tool_call in current.tool_calls
                }
                i += 1
                # 收集后续对应的 tool message
                while (i < len(messages) and messages[i].role == "tool" and messages[
                    i].tool_call_id in expected_tool_ids):
                    group.append(messages[i])
                    expected_tool_ids.discard(messages[i].tool_call_id)
                    i += 1
                groups.append(group)
                continue
            # 普通消息
            groups.append([current])
            i += 1
        return groups

    def trim_messages(self, window_size, message) -> None:
        groups = self.build_message_groups(message)
        permanent_groups = []
        normal_groups = []
        for group in groups:
            if (len(group) == 1 and group[0].role == "system"):
                permanent_groups.append(group)
            else:
                normal_groups.append(group)

        # 计算还可以保留多少普通消息
        available = max(1, window_size - sum(len(group) for group in permanent_groups))

        kept_groups = []
        total = 0
        for group in reversed(normal_groups):
            size = len(group)
            if kept_groups and total + size > available:
                break
            kept_groups.append(group)
            total += size
        kept_groups.reverse()

        messages = [
            message
            for group in permanent_groups + kept_groups
            for message in group
        ]
        return messages
