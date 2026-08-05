import httpx
import logging
from typing import Optional
from App.config import config

logger = logging.getLogger(__name__)

class LLMService:
    def __init__(
            self,
            api_key: Optional[str] = None,
            url: Optional[str] = None,
            model: Optional[str] = None,
            max_tokens: Optional[int] = None,
            temperature: Optional[float] = None,
            thinking: Optional[str] = None,
    ):
        # 从统一配置读取
        self.api_key = api_key or config.llm.API_KEY
        self.timeout = config.llm.TIMEOUT or 60
        self.url = url
        self.model = model
        self.max_tokens = max_tokens
        self.temperature = temperature
        self.enthinking = thinking
        if not self.api_key:
            logger.warning("警告：未设置 LLM_API_KEY，AI 调用会失败")

        self.headers = {
            "Content-Type": "application/json",
        }
        if self.api_key:
            self.headers["Authorization"] = f"Bearer {self.api_key}"
        self.client = httpx.AsyncClient(timeout=self.timeout)

    async def chat(self, messages: list) -> str:
        """
        异步调用 LLM 大模型

        Args:
            messages: 消息列表，比如 [{"role": "user", "content": "你好"}]

        Returns:
            AI 返回的文本内容
        """
        payload = {
            "model": self.model,
            "messages": messages,
            "stream": False,
            "max_tokens": self.max_tokens,
            "temperature": self.temperature,
            "thinking": {"type": self.enthinking}
        }

        try:
            # 异步发送 POST 请求（不会阻塞 FastAPI 事件循环）
            response = await self.client.post(self.url, headers=self.headers, json=payload)
            # result = response.json()
            # print(f"[完整返回] {result}")
            if response.status_code == 200:
                result = response.json()
                content = result['choices'][0]['message']['content']
                # 打印消耗的 tokens（方便监控费用）
                tokens = result.get('usage', {})
                logger.info(f"AI 调用成功，消耗 tokens: {tokens}")
                return content
            else:
                # 打印具体错误信息方便调试
                error_detail = response.text
                logger.error(f"LLM API 请求失败: {response.status_code}, {error_detail}")
                raise Exception(f"LLM API 请求失败: {response.status_code} - {error_detail}")

        except httpx.TimeoutException:
            raise Exception("AI 服务响应超时，请稍后重试")
        except Exception as e:
            logger.error(f"调用大模型发生未知错误: {e}")
            raise e

    async def close(self):
        """关闭 HTTP 客户端（优雅退出时调用）"""
        await self.client.aclose()

