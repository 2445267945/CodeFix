import re
from typing import Dict, Any, Optional


def parse_llm_response(response: str) -> Dict[str, Any]:
    """
    解析 LLM 返回的 ReAct 格式文本，提取其中的指令。

    Args:
        response: LLM 返回的原始字符串

    Returns:
        字典，包含 type 和对应的数据：
        - {"type": "finish", "content": "最终答案"}
        - {"type": "action", "action": "工具名", "action_input": "JSON参数"}
        - {"type": "thought", "content": "思考内容"}  (既无 Finish 也无 Action 时)
        - {"type": "error", "message": "空响应"}     (响应为空时)
    """
    if not response or not response.strip():
        return {"type": "error", "message": "LLM 返回了空响应"}

    # 1. 优先匹配 Finish（一旦完成，无视后面的内容）
    finish_match = re.search(r"Finish:\s*(.*)", response, re.DOTALL)
    if finish_match:
        return {
            "type": "finish",
            "content": finish_match.group(1).strip()
        }

    # 2. 匹配 Action 和 Action Input（必须同时存在）
    action_match = re.search(r"Action:\s*(.*)", response)
    input_match = re.search(r"Action Input:\s*(.*)", response, re.DOTALL)

    if action_match and input_match:
        action_name = action_match.group(1).strip()
        action_args = input_match.group(1).strip()

        # 针对代码块进行清洗（防止 AI 在参数外包裹 ```json）
        if action_args.startswith("```json"):
            action_args = action_args[7:]
        if action_args.endswith("```"):
            action_args = action_args[:-3]

        # 针对 Action Input 里可能包含多余的空格或换行进行清理
        action_args = action_args.strip()

        return {
            "type": "action",
            "action": action_name,
            "action_input": action_args
        }

    # 3. 如果既没有 Finish 也没有 Action，那就纯当它是思考过程
    return {
        "type": "thought",
        "content": response
    }