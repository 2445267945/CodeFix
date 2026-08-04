import re
from typing import Dict, Any, Optional


import re
from typing import Dict, Any

def _extract_first_json(raw: str) -> str:
    """
    从字符串中提取第一个完整的 JSON 对象（或数组），忽略后面所有垃圾字符。
    用于清洗 LLM 输出的 Action Input 中可能包含的多余括号或解释文字。
    """
    raw = raw.strip()
    if not raw:
        return ""

    # 找到第一个 { 或 [
    start = raw.find('{')
    if start == -1:
        start = raw.find('[')
    if start == -1:
        return ""

    # 用栈匹配括号，找到第一个完整的 JSON
    stack = []
    for i, ch in enumerate(raw[start:], start):
        if ch in '{[':
            stack.append(ch)
        elif ch in '}]':
            if not stack:
                continue  # 忽略多余的结束括号
            top = stack.pop()
            if (top == '{' and ch != '}') or (top == '[' and ch != ']'):
                return ""  # 括号不匹配，不是合法 JSON
            if not stack:
                # 找到了匹配的结束括号
                return raw[start:i+1]
    # 如果遍历完没有闭合，返回原始字符串（交给 json.loads 处理）
    return raw[start:]

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

    # 1. 【最高优先级】匹配 Action 和 Action Input
    action_match = re.search(r"Action:\s*(.*)", response)
    input_match = re.search(r"Action Input:\s*(.*)", response, re.DOTALL)

    if action_match and input_match:
        action_name = action_match.group(1).strip()
        raw_input = input_match.group(1).strip()

        # 清洗代码块
        if raw_input.startswith("```json"):
            raw_input = raw_input[7:]
        if raw_input.endswith("```"):
            raw_input = raw_input[:-3]
        raw_input = raw_input.strip()

        # 提取第一个完整 JSON
        clean_json = _extract_first_json(raw_input)

        return {
            "type": "action",
            "action": action_name,
            "action_input": clean_json
        }

    # 2. 【次优先级】如果没有 Action，再匹配 Finish
    finish_match = re.search(r"Finish:\s*(.*)", response, re.DOTALL)
    if finish_match:
        return {
            "type": "finish",
            "content": finish_match.group(1).strip()
        }

    # 3. 既没有 Action 也没有 Finish，当作思考
    return {
        "type": "thought",
        "content": response
    }
