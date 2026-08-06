from typing import Dict, Optional
from .llm_service import LLMService

class LLMFactory:
    """
    大模型工厂：根据 Agent 类型或场景返回不同的 LLM 实例。
    支持多模型策略，为后续多 Agent 协作预留扩展点。
    """
    instances: Dict[str, LLMService] = {}

    @classmethod
    def get_llm(cls, max_token: int, temperature: float, model_key: Optional[str] = None) -> LLMService:
        """
        获取 LLM 实例。

        :param model_key: 模型标识，如 'default', 'flash', 'reasoning'。
                          若不指定，使用默认配置。
        """
        if model_key is None:
            model_key = "default"

        if model_key not in cls.instances:
            cls.instances[model_key] = cls.create_llm(model_key, max_token, temperature)

        return cls.instances[model_key]

    @classmethod
    def create_llm(cls, model_key: str, max_tokens: int, temperature: float) -> LLMService:
        """根据 key 创建对应的 LLM 实例（带不同配置）"""
        # 从配置中读取不同模型的参数
        model_configs = {
            "default": {
                "url": "https://api.deepseek.com/chat/completions",
                "model": "deepseek-v4-flash",
                "max_tokens": 4096,
                "temperature": 0.3,
                "thinking": "adaptive",
            },
            "low": {
                "url": "http://localhost:11434/v1/chat/completions",
                "model": "qwen2.5:3b",
                "max_tokens": max_tokens,
                "temperature": temperature,
                "thinking": "disabled",
            },
            "mid": {
                "url": "https://api.deepseek.com/chat/completions",
                "model": "deepseek-v4-flash",
                "max_tokens": max_tokens,
                "temperature": temperature,
                "thinking": "disabled",
            },
            "high": {
                "url": "https://api.deepseek.com/chat/completions",
                "model": "deepseek-v4-pro",
                "max_tokens": max_tokens,
                "temperature": temperature,
                "thinking": "enabled",
            }
        }

        cfg = model_configs.get(model_key, model_configs["default"])

        # 创建 LLMService 实例
        return LLMService(
            url=cfg["url"],
            model=cfg["model"],
            max_tokens=cfg["max_tokens"],
            temperature=cfg["temperature"],
            thinking=cfg["thinking"],
        )

llm_factory = LLMFactory()