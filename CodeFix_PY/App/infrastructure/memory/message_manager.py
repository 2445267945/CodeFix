from pydantic import ValidationError
from App.agents.prompts import COMPRESS_PROMPT_TEMPLATE

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


    async def compress_history_msg(self, window_size, messages, llm):
        """
        检查消息长度，若超过阈值则调用 LLM 压缩旧消"
        :param keep_recent:
        :param messages:
        :param llm:
        :return:
        """
        compress_threshold = 0.7
        keep_recent = window_size * 0.3
        msg_len = len(messages)
        if msg_len / window_size < compress_threshold:
            return
        # 分割消息
        keep = round(keep_recent)
        head = messages[:-keep]
        tail = messages[-keep:]

        # 构造压缩提示词
        compress_prompt = COMPRESS_PROMPT_TEMPLATE.format(head=head)
        # 调用 LLM 生成摘要
        try:
            summary_response = await llm.chat([{"role": "user", "content": compress_prompt}])
            summary_text = summary_response.strip()
        except Exception as e:
            # 若生成摘要失败，降级为简单截断
            print(f"摘要生成失败，使用简单截断: {e}")
            summary_text = f"[系统摘要] 前 {len(head)} 轮对话已压缩。"

        # 重建消息列表：摘要作为 system 消息 + 最近保留的消息
        messages = [{"role": "system", "content": f"【历史摘要】{summary_text}"}] + tail

        print(f"上下文已压缩：丢弃了 {len(head)} 条旧消息，保留最近 {keep} 条。")