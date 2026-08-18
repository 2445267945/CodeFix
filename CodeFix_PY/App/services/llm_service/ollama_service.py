# App/agents/client/ollama_llm.py

import json
import logging
from typing import Optional, Any

import httpx

from App.agents.client.llm_client import LLMClient
from App.agents.agent_model.llm_message import LLMMessage
from App.agents.agent_model.llm_response import LLMResponse
from App.agents.agent_model.tool_call import ToolCall
from App.config import config

logger = logging.getLogger(__name__)


class OllamaLLM(LLMClient):

    def __init__(
            self,
            api_key: Optional[str] = None,
            url: Optional[str] = None,
            model: Optional[str] = None,
            max_tokens: Optional[int] = None,
            temperature: Optional[float] = None,
            thinking: Optional[str] = None,
    ):
        self.url = url
        self.model = model
        self.max_tokens = max_tokens
        self.temperature = temperature
        self.thinking = thinking

        self.timeout = config.llm.TIMEOUT or 60

        # Ollama 本地 API 不要求鉴权。
        # OpenAI 兼容接口虽然要求提供 api_key 字段，
        # Ollama 本地实现会忽略它。
        self.api_key = api_key or "ollama"

        self.headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {self.api_key}",
        }

        self.client = httpx.AsyncClient(
            timeout=self.timeout
        )

    async def chat(
            self,
            messages: list[LLMMessage],
            tools: list[dict] | None = None,
    ) -> LLMResponse:

        payload_messages = [
            self.to_ollama_message(message)
            for message in messages
        ]

        payload: dict[str, Any] = {
            "model": self.model,
            "messages": payload_messages,
            "stream": False,
        }

        if self.max_tokens is not None:
            payload["max_tokens"] = self.max_tokens

        if self.temperature is not None:
            payload["temperature"] = self.temperature

        # Ollama OpenAI-compatible API 支持 tools。
        if tools:
            payload["tools"] = [
                {
                    "type": "function",
                    "function": tool,
                }
                for tool in tools
            ]

        # 非 Tool Calling 场景才启用 JSON mode。
        if config.llm.JSON_FORMAT and not tools:
            payload["response_format"] = {
                "type": "json_object"
            }

        # 对于 Ollama 的 reasoning/thinking，
        # 不同模型支持情况不同，因此这里不要像 DeepSeek
        # 一样硬编码 thinking 字段。
        #
        # 当前你的 qwen2.5:3b 如果只是作为 compress model，
        # 可以先不传 thinking。

        try:
            response = await self.client.post(
                self.url,
                headers=self.headers,
                json=payload,
            )

            if response.status_code != 200:
                error_detail = response.text

                logger.error(
                    "Ollama 服务返回错误: status=%s, detail=%s",
                    response.status_code,
                    error_detail,
                )

                raise RuntimeError(
                    f"Ollama API 请求失败: "
                    f"{response.status_code} - {error_detail}"
                )

            result = response.json()

            message = result["choices"][0]["message"]

            content = message.get("content")

            # OpenAI-compatible Ollama 返回格式可能没有
            # reasoning_content，因此统一抽取，但没有就 None。
            reasoning_content = message.get(
                "reasoning_content"
            )

            provider_tool_calls = message.get(
                "tool_calls",
                []
            )

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

            tokens = result.get(
                "usage",
                {}
            )

            logger.info(
                "Ollama 调用成功，消耗 tokens: %s",
                tokens,
            )

            return LLMResponse(
                content=content,
                reasoning_content=reasoning_content,
                tool_calls=tool_calls,
                raw=result,
            )

        except httpx.TimeoutException as e:
            logger.error(
                "Ollama 请求超时",
                exc_info=True,
            )

            raise RuntimeError(
                "Ollama 服务响应超时，请稍后重试"
            ) from e

        except httpx.HTTPError as e:
            logger.error(
                "Ollama HTTP 请求异常",
                exc_info=True,
            )

            raise RuntimeError(
                f"Ollama HTTP 请求异常: {e}"
            ) from e

    async def close(self):
        await self.client.aclose()

    @staticmethod
    def to_ollama_message(
            message: LLMMessage,
    ) -> dict:

        result = {
            "role": message.role,
        }

        if message.content is not None:
            result["content"] = message.content

        if message.reasoning_content is not None:
            result["reasoning_content"] = (
                message.reasoning_content
            )

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
            result["tool_call_id"] = (
                message.tool_call_id
            )

        if message.name is not None:
            result["name"] = message.name

        return result
