import json

import httpx
import logging
from typing import Optional

from App.agents.agent_model.llm_message import LLMMessage
from App.agents.agent_model.llm_response import LLMResponse
from App.agents.agent_model.tool_call import ToolCall
from App.agents.client.llm_client import LLMClient
from App.config import config

logger = logging.getLogger(__name__)


class DeepSeekLLM(LLMClient):
    def __init__(
        self,
        api_key: Optional[str] = None,
        url: Optional[str] = None,
        model: Optional[str] = None,
        max_tokens: Optional[int] = None,
        temperature: Optional[float] = None,
        thinking: Optional[str] = None,
    ):
        self.api_key = api_key or config.llm.API_KEY
        self.en_json_format = config.llm.JSON_FORMAT
        self.timeout = config.llm.TIMEOUT or 60
        self.url = url
        self.model = model
        self.max_tokens = max_tokens
        self.temperature = temperature
        self.thinking = thinking
        if not self.api_key:
            logger.warning("警告：未设置 LLM_API_KEY，AI 调用会失败")
        self.headers = {
            "Content-Type": "application/json"
        }
        if self.api_key:
            self.headers["Authorization"] = (
                f"Bearer {self.api_key}"
            )

        self.client = httpx.AsyncClient(timeout=self.timeout)

    async def chat(self, messages: list[LLMMessage], tools: list[dict] | None = None) -> LLMResponse:
        payload_messages = [
            self.to_deepseek_message(message)
            for message in messages
        ]

        payload = {
            "model": self.model,
            "messages": payload_messages,
            "stream": False,
            "max_tokens": self.max_tokens,
            "temperature": self.temperature,
            "thinking": {
                "type": self.thinking
            },
        }

        if tools:
            payload["tools"] = [
                {"type": "function", "function": tool}
                for tool in tools
            ]

        # 没有指定tools，就指定json_object让llm输出json格式
        if self.en_json_format and not tools:
            payload["response_format"] = {
                "type": "json_object"
            }
        try:
            response = await self.client.post(self.url, headers=self.headers, json=payload)
            if response.status_code != 200:
                error_detail = response.text
                logger.error( "LLM 服务返回错误: status=%s, detail=%s", response.status_code, error_detail)
                raise RuntimeError(f"LLM API 请求失败: " f"{response.status_code} - {error_detail}")

            result = response.json()
            message = result["choices"][0]["message"]
            content = message.get("content")
            reasoning_content = message.get("reasoning_content")
            provider_tool_calls = message.get("tool_calls", [])

            tool_calls = []

            for item in provider_tool_calls:
                arguments_raw = item["function"].get("arguments", "{}")
                if isinstance(arguments_raw, str):
                    try:
                        arguments = json.loads(arguments_raw)
                    except json.JSONDecodeError as e:
                        raise RuntimeError(f"Tool 参数 JSON 解析失败: "f"{item['function'].get('name')}") from e
                else:
                    arguments = arguments_raw
                tool_calls.append(
                    ToolCall(
                        id=item["id"],
                        name=item["function"]["name"],
                        arguments=arguments,
                    )
                )
            return LLMResponse(content=content, reasoning_content=reasoning_content, tool_calls=tool_calls, raw=result)
        except httpx.TimeoutException as e:
            logger.error("LLM 请求超时",exc_info=True)
            raise RuntimeError("AI 服务响应超时，请稍后重试") from e
        except httpx.HTTPError as e:
            logger.error("LLM HTTP 请求异常",exc_info=True)
            raise RuntimeError(f"LLM HTTP 请求异常: {e}") from e
    async def close(self):
        await self.client.aclose()

    # 把消息变成deepseek官方提供的样例的样子，当前2026/8/16
    @staticmethod
    def to_deepseek_message(message: LLMMessage) -> dict:
        result = {"role": message.role}
        if message.content is not None:
            result["content"] = message.content
        if message.reasoning_content is not None:
            result["reasoning_content"] = (message.reasoning_content)
        if message.tool_calls:
            result["tool_calls"] = [
                {
                    "id": tool_call.id,
                    "type": "function",
                    "function": {
                        "name": tool_call.name,
                        "arguments": json.dumps(
                            tool_call.arguments,
                            ensure_ascii=False
                        ),
                    },
                }
                for tool_call in message.tool_calls
            ]
        if message.tool_call_id is not None:
            result["tool_call_id"] = (message.tool_call_id)
        if message.name is not None:
            result["name"] = message.name
        return result