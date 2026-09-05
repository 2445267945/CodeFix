import json

import httpx
import logging
from typing import Optional

from transformers import AutoTokenizer

from App.agents.agent_model.llm_message import LLMMessage
from App.agents.agent_model.llm_response import LLMResponse
from App.agents.agent_model.tool_call import ToolCall
from App.agents.client.llm_client import LLMClient
from App.config import config
from App.services.llm_service.deepseek_extension.encoding_dsv4 import encode_messages

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
            timeout: Optional[float] = None,
    ):
        self.api_key = api_key or config.llm.API_KEY
        self.timeout = timeout or 60
        self.url = url
        self.model = model
        self.max_tokens = max_tokens
        self.temperature = temperature
        self.thinking = thinking
        self.tokenizer = AutoTokenizer.from_pretrained(self.get_tokenizer_model(), trust_remote_code=True)

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

        for i, message in enumerate(messages):
            print(f"[RESUME MESSAGE {i}] {message}")

        if tools:
            payload["tools"] = self.format_tools(tools=tools)

        # 没有指定tools，就指定json_object让llm输出json格式
        if not tools:
            payload["response_format"] = {
                "type": "json_object"
            }
        try:
            response = await self.client.post(self.url, headers=self.headers, json=payload)
            if response.status_code != 200:
                error_detail = response.text
                logger.error("LLM 服务返回错误: status=%s, detail=%s", response.status_code, error_detail)
                raise RuntimeError(f"LLM API 请求失败: " f"{response.status_code} - {error_detail}")

            result = response.json()
            message = result["choices"][0]["message"]
            content = message.get("content")
            reasoning_content = message.get("reasoning_content")
            provider_tool_calls = message.get("tool_calls", [])

            tool_calls = []

            for item in provider_tool_calls:
                function = item.get("function", {})
                tool_name = function.get("name", "")
                tool_call_id = item.get("id", "")
                arguments_raw = function.get("arguments", "{}")
                # arguments_raw = item["function"].get("arguments", "{}")
                if isinstance(arguments_raw, str):
                    try:
                        arguments = json.loads(arguments_raw)
                    except json.JSONDecodeError as e:
                        logger.error(
                            "Tool 参数 JSON 解析失败: "
                            "tool=%s, toolCallId=%s, error=%s, "
                            "argumentsLength=%s, argumentsRaw=%r",
                            tool_name,
                            tool_call_id,
                            e,
                            len(arguments_raw),
                            arguments_raw,
                        )

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
            return LLMResponse(content=content, reasoning_content=reasoning_content, tool_calls=tool_calls, raw=result, usage=result.get("usage"),)
        except httpx.TimeoutException as e:
            logger.error("LLM 请求超时", exc_info=True)
            raise RuntimeError("AI 服务响应超时，请稍后重试") from e
        except httpx.HTTPError as e:
            logger.error("LLM HTTP 请求异常", exc_info=True)
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

    def count_messages(self, messages: list[LLMMessage], tools: list[dict] | None = None) -> int:
        """
        使用 DeepSeek V4 官方 encoding + tokenizer
        估算当前 messages 的输入 Token 数。

        注意：
        - 这是调用前的 Token 估算。
        - 最终真实 Token 以 API 返回的 usage.prompt_tokens 为准。
        """

        if not messages and not tools:
            return 0
        # =========================================================
        # 1. LLMMessage -> DeepSeek OpenAI-compatible message
        # =========================================================
        payload_messages = [
            self.to_deepseek_message(message)
            for message in messages
        ]
        # =========================================================
        # 2. Tool schema
        #
        # get_tool_definitions() 返回的是 function schema：
        #
        # {
        #   "name": "...",
        #   "description": "...",
        #   "parameters": {...}
        # }
        #
        # DeepSeek V4 encoder 需要：
        #
        # {
        #   "type": "function",
        #   "function": {
        #       "name": "...",
        #       ...
        #   }
        # }
        # =========================================================
        if tools:
            formatted_tools = self.format_tools(tools=tools)

            if (payload_messages and payload_messages[0].get("role") == "system"):
                payload_messages[0]["agent_boost"] = formatted_tools
            else:
                payload_messages.insert(
                    0,
                    {
                        "role": "system",
                        "content": "",
                        "agent_boost": formatted_tools,
                    },
                )

        # =========================================================
        # 3. DeepSeek V4 thinking mode
        # =========================================================
        thinking_mode = "thinking" if self.thinking in ("enabled", "adaptive", "thinking") else "chat"

        # =========================================================
        # 4. DeepSeek V4 official encoding
        # =========================================================
        prompt = encode_messages(
            payload_messages,
            thinking_mode=thinking_mode,
            drop_thinking=True,
            reasoning_effort=None,
        )

        # =========================================================
        # 5. Tokenizer
        # =========================================================
        return len(self.tokenizer.encode(prompt, add_special_tokens=False))


    def count_text(self, text: str) -> int:
        if not text:
            return 0
        return len(self.tokenizer.encode(text, add_special_tokens=False))


    @property
    def context_window(self) -> int:
        return 1_000_000


    def get_tokenizer_model(self) -> str:
        mapping = {
            "deepseek-v4-flash": "deepseek-ai/DeepSeek-V4-Flash",
            "deepseek-v4-pro": "deepseek-ai/DeepSeek-V4-Pro",
        }
        tokenizer_model = mapping.get(self.model)
        if tokenizer_model is None:
            raise ValueError(f"不支持的 DeepSeek tokenizer: model={self.model}")
        return tokenizer_model


    def format_tools(self, tools: list[dict] | None) -> list[dict]:
        if not tools:
            return []
        return [
            {
                "type": "function",
                "function": tool,
            }
            for tool in tools
        ]
