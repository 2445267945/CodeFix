import json

from pydantic import ValidationError


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
        检测 Agent 是否陷入重复工具调用。

        规则：
        最近 3 次完全相同的工具 + 参数 -> 判定为死循环。
        """
        action_signature = self.build_action_signature(
            tool_name,
            tool_args
        )
        action_history.append(action_signature)

        if len(action_history) > 5:
            action_history.pop(0)

        if len(action_history) >= 3:
            return len(set(action_history[-3:])) == 1

        return False
    def build_action_signature(self, tool_name, tool_args):
        normalized_args = json.dumps(
            tool_args,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":")
        )
        return f"{tool_name}:{normalized_args}"
