# App/agents/client/ollama_llm.py

import json
import logging
from typing import Optional, Any

import httpx
from transformers import AutoTokenizer

from App.agents.client.llm_client import LLMClient
from App.agents.agent_model.llm_message import LLMMessage
from App.agents.agent_model.llm_response import LLMResponse
from App.agents.agent_model.tool_call import ToolCall
from App.config import config
from App.utils.hf_utils import load_tokenizer

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
            timeout: Optional[float] = None,
    ):
        self.url = url
        self.model = model
        self.max_tokens = max_tokens
        self.temperature = temperature
        self.thinking = thinking
        self.timeout = timeout or 60
        # Ollama 本地 API 不要求鉴权。
        self.api_key = api_key or "ollama"
        self.headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {self.api_key}",
        }
        self.client = httpx.AsyncClient(timeout=self.timeout)
        # Tokenizer
        self.tokenizer = None
        try:
            self.tokenizer = load_tokenizer(self.get_tokenizer_model())
        except Exception:
            logger.warning("Tokenizer 加载失败，Token 统计功能暂时不可用")

    async def chat(self, messages: list[LLMMessage], tools: list[dict] | None = None) -> LLMResponse:
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
        # Ollama OpenAI-compatible API 支持 agent_boost。
        if tools:
            payload["agent_boost"] = [
                {
                    "type": "function",
                    "function": tool,
                }
                for tool in tools
            ]
        # 非 Tool Calling 场景才启用 JSON mode。
        if not tools:
            payload["response_format"] = {
                "type": "json_object"
            }
        try:
            response = await self.client.post(self.url, headers=self.headers, json=payload)
            if response.status_code != 200:
                error_detail = response.text
                logger.error("Ollama 服务返回错误: status=%s, detail=%s", response.status_code, error_detail)
                raise RuntimeError(f"Ollama API 请求失败: {response.status_code} - {error_detail}")
            result = response.json()
            message = result["choices"][0]["message"]
            content = message.get("content")
            reasoning_content = message.get("reasoning_content")
            provider_tool_calls = message.get("tool_calls",[])

            tool_calls = []
            for item in provider_tool_calls:
                arguments_raw = item["function"].get("arguments","{}")
                if isinstance(arguments_raw, str):
                    try:
                        arguments = json.loads(arguments_raw)
                    except json.JSONDecodeError as e:
                        raise RuntimeError("Tool 参数 JSON 解析失败: {item['function'].get('name')}") from e
                else:
                    arguments = arguments_raw

                tool_calls.append(
                    ToolCall(
                        id=item["id"],
                        name=item["function"]["name"],
                        arguments=arguments,
                    )
                )
            tokens = result.get("usage",{})
            logger.info("Ollama 调用成功，消耗 tokens: %s", tokens)
            return LLMResponse(
                content=content,
                reasoning_content=reasoning_content,
                tool_calls=tool_calls,
                raw=result,
                usage=tokens,
            )
        except httpx.TimeoutException as e:
            logger.error("Ollama 请求超时", exc_info=True)
            raise RuntimeError("Ollama 服务响应超时，请稍后重试") from e
        except httpx.HTTPError as e:
            logger.error("Ollama HTTP 请求异常", exc_info=True)
            raise RuntimeError(f"Ollama HTTP 请求异常: {e}") from e
    async def close(self):
        await self.client.aclose()

    # Tokenizer
    def get_tokenizer_model(self) -> str:
        """
        根据 Ollama 模型名称选择对应 Hugging Face tokenizer。
        """
        mapping = {
            "qwen2.5:3b": "Qwen/Qwen2.5-3B-Instruct",
        }
        tokenizer_model = mapping.get(self.model)
        if tokenizer_model is None:
            raise ValueError(f"未找到 Ollama 模型对应 tokenizer: model={self.model}")
        return tokenizer_model

    # Token Estimator
    def count_text(self, text: str) -> int:
        """
        统计纯文本 Token 数。

        注意：
        这是本地 tokenizer 估算值，
        最终真实消耗应以 Ollama 返回的 usage 为准。
        """
        if not text or self.tokenizer is None:
            return 0
        return len(self.tokenizer.encode(text,add_special_tokens=False))

    def count_messages(self, messages: list[LLMMessage], tools: list[dict] | None = None) -> int:
        """
        估算当前消息列表的 Token 数。

        第一版：
        使用 Qwen tokenizer 对 OpenAI-compatible
        message payload 做近似估算。

        注意：
        该值主要用于 Context Budget 判断，
        不是 Provider 最终计费 Token。
        """
        if self.tokenizer is None:
            return 0
        if not messages and not tools:
            return 0
        payload_messages = [
            self.to_ollama_message(message)
            for message in messages
        ]
        if tools:
            payload_messages.append(
                {
                    "role": "system",
                    "content": json.dumps(
                        tools,
                        ensure_ascii=False,
                    ),
                }
            )

        serialized = json.dumps(payload_messages, ensure_ascii=False, separators=(",", ":"))
        return self.count_text(serialized)

    # Context Window
    @property
    def context_window(self) -> int:
        """
        当前 qwen2.5:3b 模型的上下文窗口。

        第一版根据当前使用模型固定。
        后续可以根据 model 动态映射。
        """

        context_windows = {
            "qwen2.5:3b": 32_768,
        }

        window = context_windows.get(self.model)
        if window is None:
            raise ValueError(f"未配置 Ollama 模型 Context Window: model={self.model}")
        return window

    # Message Convert
    @staticmethod
    def to_ollama_message(message: LLMMessage) -> dict:
        result = {
            "role": message.role,
        }
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