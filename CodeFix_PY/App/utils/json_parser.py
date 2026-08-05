import json
from typing import Dict, Any

def parse_llm_response(response: str) -> Dict[str, Any]:
    """
    解析 LLM 返回的 JSON 格式响应。
    要求 LLM 必须输出合法的 JSON 对象，包含以下可能字段：
    - thought: str（可选）
    - action: str（工具名称）
    - action_input: dict（工具参数）
    - finish: bool（是否完成）
    - answer: str（完成时的最终答案）

    Returns:
        统一格式的字典：
        - {"type": "finish", "content": "最终答案"}
        - {"type": "action", "action": "工具名", "action_input": "JSON字符串"}
        - {"type": "thought", "content": "思考内容"}
        - {"type": "error", "message": "错误信息"}
    """
    if not response or not response.strip():
        return {"type": "error", "message": "LLM 返回了空响应"}

    try:
        data = json.loads(response)
    except json.JSONDecodeError:
        # 如果输出不是合法 JSON，尝试提取其中的 JSON 部分（兜底）
        import re
        json_match = re.search(r'\{.*\}', response, re.DOTALL)
        if json_match:
            try:
                data = json.loads(json_match.group())
            except:
                return {"type": "error", "message": "返回内容包含非法 JSON"}
        else:
            return {"type": "error", "message": "返回内容不是合法 JSON"}

    # 1. 判断是否为完成信号
    if data.get("finish") is True:
        answer_data = data.get("answer", "")
        # 如果 answer 是字典（即包含 code 和 changes），将其转为 JSON 字符串
        if isinstance(answer_data, dict):
            content = json.dumps(answer_data, ensure_ascii=False)
        else:
            content = str(answer_data)
        return {
            "type": "finish",
            "content": content  # 现在包含完整的 JSON 字符串
        }

    # 2. 判断是否为工具调用
    if "action" in data:
        action_name = data["action"]
        action_input = data.get("action_input", {})
        # 将 action_input 转为 JSON 字符串，以便后续 act 方法解析
        try:
            action_input_str = json.dumps(action_input, ensure_ascii=False)
        except:
            action_input_str = "{}"
        return {
            "type": "action",
            "action": action_name,
            "action_input": action_input_str
        }

    # 3. 既不是 finish 也不是 action，当作思考
    return {
        "type": "thought",
        "content": data.get("thought", response)
    }