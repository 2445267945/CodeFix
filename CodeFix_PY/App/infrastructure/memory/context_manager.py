import logging
from typing import Any

from App.agents.agent_model.llm_message import LLMMessage
from App.agents.prompts import COMPRESS_PROMPT_TEMPLATE
from .context_state import ContextState
from ...agents.client.llm_client import LLMClient

logger = logging.getLogger(__name__)

class ContextManager:
    """
    Agent Context Manager。

    负责：
    1. 初始化当前 Run 上下文
    2. 维护 Message Group
    3. 压缩历史
    4. 裁剪历史
    5. 对外提供当前 LLM Context

    不负责：
    - Tool 参数校验
    - Tool 执行
    - Agent 状态机
    - Permission
    """
    COMPRESS_THRESHOLD = 0.8
    KEEP_CONTEXT_RATIO = 0.4
    TRIM_THRESHOLD = 0.9

    def __init__(self):
        pass

    def initialize(self, state: ContextState, question: str, system_prompt: str, session_context: Any | None = None) -> None:
        if state is None:
            raise ValueError("ContextState 不能为空")
        messages: list[LLMMessage] = []
        messages.append(LLMMessage(role="system", content=system_prompt))
        if session_context is not None:
            session_messages = getattr(session_context, "messages", None)
            if session_messages:
                for item in session_messages:
                    role = getattr(item, "role", None)
                    content = getattr(item, "content", None)
                    if role is None:
                        continue
                    role = str(role).lower()
                    if role not in ("user", "assistant", "system"):
                        continue
                    messages.append(LLMMessage(role=role, content=content))
        if question:
            messages.append(LLMMessage(role="user", content=question))
        state.session_context = session_context
        state.messages = messages

    # =========================================================
    # Token Budget
    # =========================================================
    def estimate_tokens(self, state: ContextState, llm, tools: list[dict] | None = None) -> int:
        if state is None:
            raise ValueError("ContextState 不能为空")
        if llm is None:
            raise ValueError("LLM 不能为空")
        messages = self.get_messages(state)
        try:
            estimated_tokens = llm.count_messages(messages=messages, tools=tools)
            if estimated_tokens is None:
                raise RuntimeError("LLM count_messages() 返回 None")
            return estimated_tokens
        except Exception:
            raise


    # =========================================================
    # Compression
    # =========================================================
    async def compress_if_needed(self, state: ContextState, main_llm, compress_llm, tools: list[dict] | None = None) -> None:
        """
        Token-aware Context Compression。

        流程：

        1. 使用执行模型 main_llm 估算当前 Context Token。
        2. 达到 context_window 的压缩阈值后触发压缩。
        3. 按完整 Message Group 从最新消息向前保留。
        4. Tool Call 与对应 Tool Result 不允许拆开。
        5. 被淘汰的历史消息交给 compress_llm 生成 summary。
        6. System Message 永久保留。
        """
        if state is None:
            raise ValueError("ContextState 不能为空")
        if main_llm is None:
            raise ValueError("main_llm 不能为空")
        if compress_llm is None:
            raise ValueError("compress_llm 不能为空")
        messages = state.messages
        if not messages:
            return
        # =========================================================
        # 1. 当前 Context Token
        # =========================================================
        estimated_tokens = self.estimate_tokens(state=state, llm=main_llm, tools=tools)
        compress_limit = int(main_llm.context_window * self.COMPRESS_THRESHOLD)
        # 尚未达到压缩阈值
        if estimated_tokens < compress_limit:
            return
        # =========================================================
        # 2. 计算压缩后希望保留的 Token Budget
        # =========================================================
        keep_tokens = int(main_llm.context_window * self.KEEP_CONTEXT_RATIO)
        # 防止极端配置
        keep_tokens = max(1, keep_tokens)
        # =========================================================
        # 3. 按完整 Message Group 切分
        # =========================================================

        head, tail = self.split_message_groups(messages=messages, keep_tokens=keep_tokens, llm=main_llm, tools=tools)
        # 没有可以压缩的内容
        if not head:
            state.messages = tail
            return
        # =========================================================
        # 4. 历史消息转成摘要输入
        # =========================================================
        head_text = "\n".join(
            self.format_message_for_compression(message)
            for message in head
        )
        compress_prompt = COMPRESS_PROMPT_TEMPLATE.format(history_summary=state.history_summary, head=head_text)

        # =========================================================
        # 5. 使用压缩模型生成 Summary
        # =========================================================
        try:
            summary_response = await compress_llm.chat(messages=[LLMMessage(role="user", content=compress_prompt)], tools=None)
            new_summary = (summary_response.content or state.history_summary)
        except Exception as e:
            print(f"摘要生成失败，保留原历史摘要并继续使用裁剪后的 Context: {e}")
            new_summary = state.history_summary
        # =========================================================
        # 6. 更新 ContextState
        # =========================================================
        state.history_summary = new_summary
        state.messages = tail

        print(
            "[COMPRESS RESULT]",
            f"summary_length={len(state.history_summary or '')}",
            f"message_count={len(state.messages)}",
        )


    # =========================================================
    # Compression Format
    # =========================================================
    def format_message_for_compression(self, message: LLMMessage) -> str:
        parts = [f"role={message.role}"]
        if message.content is not None:
            parts.append(f"content={message.content}")
        if message.reasoning_content:
            parts.append(f"reasoning_content={message.reasoning_content}")
        if message.tool_calls:
            parts.append(
                "tool_calls=" + str(
                    [
                        {"id": tool.id, "name": tool.name, "arguments": tool.arguments}
                        for tool in message.tool_calls
                    ]
                )
            )
        if message.tool_call_id:
            parts.append(f"tool_call_id={message.tool_call_id}")
        return "\n".join(parts)


    # =========================================================
    # Message Groups
    # =========================================================
    def build_message_groups(self, messages: list[LLMMessage] | None = None) -> list[list[LLMMessage]]:
        """
        将 Message 划分成不可拆开的逻辑组。

        规则：

        1. assistant 无 tool_calls
           -> 独立消息组

        2. assistant 有 tool_calls
           -> 与对应 tool messages 组成一个 Group

        3. 普通 user/system
           -> 独立消息组
        """
        if not messages:
            return []
        groups: list[list[LLMMessage]] = []

        i = 0
        while i < len(messages):
            current = messages[i]
            # assistant 发起 Tool Call
            if current.role == "assistant" and current.tool_calls:
                group = [current]
                expected_tool_ids = {
                    tool.id
                    for tool in current.tool_calls
                }

                i += 1
                while (i < len(messages) and messages[i].role == "tool" and messages[i].tool_call_id in expected_tool_ids):
                    group.append(messages[i])
                    expected_tool_ids.discard(messages[i].tool_call_id)
                    i += 1

                groups.append(group)
                continue

            # 普通消息
            groups.append([current])
            i += 1

        return groups

    # =========================================================
    # Split Groups
    # =========================================================
    def split_message_groups(self, messages: list[LLMMessage], keep_tokens: int, llm, tools: list[dict] | None = None) -> tuple[list[LLMMessage], list[LLMMessage]]:
        """
        按 Token Budget 切分完整 Message Group。

        head：
            旧消息，进入历史摘要。

        tail：
            最新消息，继续提供给 LLM。

        规则：
        1. system message 永久保留。
        2. assistant tool_call + tool result 必须作为完整 Group。
        3. 从最新 Group 开始向前选择。
        4. 加入后超过 keep_tokens，则停止。
        5. 如果单个 Group 本身超过 Budget，也完整保留。
        """
        if not messages:
            return [], []
        if llm is None:
            raise ValueError("LLM 不能为空")
        if keep_tokens <= 0:
            raise ValueError("keep_tokens 必须大于 0")
        groups = self.build_message_groups(messages)
        permanent_groups: list[list[LLMMessage]] = []
        normal_groups: list[list[LLMMessage]] = []
        # =========================================================
        # 1. System Message 永久保留
        # =========================================================
        for group in groups:
            if (len(group) == 1 and group[0].role == "system"):
                permanent_groups.append(group)
            else:
                normal_groups.append(group)
        # =========================================================
        # 2. 从最新 Group 开始向前保留
        # =========================================================
        kept_groups_reversed: list[list[LLMMessage]] = []
        for group in reversed(normal_groups):
            candidate_groups = (permanent_groups + list(reversed(kept_groups_reversed)) + [group])
            candidate_messages = [
                message
                for candidate_group in candidate_groups
                for message in candidate_group
            ]
            estimated_tokens = llm.count_messages(messages=candidate_messages, tools=tools)
            if estimated_tokens <= keep_tokens:
                kept_groups_reversed.append(group)
                continue
            # 当前已有 tail，继续向前会超过预算
            if kept_groups_reversed:
                break
            # 单个完整 Group 就已经超过预算。
            # 不能拆 Tool Call / Tool Result。
            kept_groups_reversed.append(group)
            break
        kept_groups = list(reversed(kept_groups_reversed))

        # =========================================================
        # 3. 构造 tail
        # =========================================================
        tail = [
            message
            for group in permanent_groups + kept_groups
            for message in group
        ]
        # =========================================================
        # 4. 其余消息进入 head
        # =========================================================
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

    # =========================================================
    # Trim
    # =========================================================

    def trim_messages(self, messages: list[LLMMessage], llm, tools: list[dict] | None = None) -> list[LLMMessage]:
        """
        Token-aware Hard Trim。

        用于 Context 的第二道安全保护。

        与 compress_if_needed() 不同：
        - 不调用压缩模型
        - 不生成 history_summary
        - 只保留最新的完整 Message Group

        System Message 永久保留。
        Tool Call 与 Tool Result 不允许拆开。
        """
        if not messages:
            return []
        if llm is None:
            raise ValueError("LLM 不能为空")
        current_tokens = llm.count_messages(messages=messages, tools=tools)
        trim_limit = int(llm.context_window * self.TRIM_THRESHOLD)
        # 没有超过硬裁剪阈值
        if current_tokens <= trim_limit:
            return messages
        keep_tokens = int(llm.context_window * self.KEEP_CONTEXT_RATIO)
        keep_tokens = max(1, keep_tokens)
        _, tail = self.split_message_groups(messages=messages, keep_tokens=keep_tokens, llm=llm, tools=tools)
        return tail

    # =========================================================
    # Public Context Access
    # =========================================================

    def get_messages(self, state: ContextState) -> list[LLMMessage]:
        """
        构建当前真正提供给 LLM 的 Context。
        最终结构：

        system
          ↓
        history_summary（如果存在）
          ↓
        当前 Run working messages
        """
        if state is None:
            raise ValueError("ContextState 不能为空")
        if not state.messages:
            return []
        result: list[LLMMessage] = []

        # =========================================================
        # 1. System Message
        # =========================================================

        system_messages = [
            message
            for message in state.messages
            if message.role == "system"
        ]
        result.extend(system_messages)

        # =========================================================
        # 2. History Summary
        # =========================================================
        if state.history_summary.strip():
            result.append(
                LLMMessage(
                    role="system",
                    content=(
                        "以下是此前对话与 Agent 工作过程的历史摘要，"
                        "仅用于帮助你理解当前任务上下文：\n\n"
                        f"{state.history_summary}"
                    ),
                )
            )

        # =========================================================
        # 3. 当前 Working Context
        # =========================================================
        result.extend(
            message
            for message in state.messages
            if message.role != "system"
        )
        return result