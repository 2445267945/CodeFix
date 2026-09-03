from typing import Dict

from App.agents.client.llm_client import LLMClient
from App.services.llm_service.deepseek_service import DeepSeekLLM
from App.services.llm_service.ollama_service import OllamaLLM
from App.config import config


class LLMFactory:
    """
    LLM 工厂。

    职责：
    1. 根据 profile 选择模型
    2. 根据 provider 创建对应 LLM Client
    3. 复用已经创建的 LLM 实例
    """
    instances: Dict[str, LLMClient] = {}

    MODEL_PROFILES = {
        "low": {
            "provider": "ollama",
            "model": "qwen2.5:3b",
            "max_tokens": 4096,
            "temperature": 0.1,
            "thinking": "disabled",
        },
        "mid": {
            "provider": "deepseek",
            "model": "deepseek-v4-flash",
            "max_tokens": 4096,
            "temperature": 0.1,
            "thinking": "enabled",
        },
        "high": {
            "provider": "deepseek",
            "model": "deepseek-v4-pro",
            "max_tokens": 4096,
            "temperature": 0.1,
            "thinking": "enabled",
        },
    }

    @classmethod
    def get_llm(cls, model_key: str = "mid") -> LLMClient:
        """
        根据模型 Profile 获取 LLM 实例。

        :param model_key:
            low / mid / high
        """
        if model_key not in cls.MODEL_PROFILES:
            raise ValueError(f"不支持的模型 Profile: {model_key}")
        if model_key not in cls.instances:
            cls.instances[model_key] = cls.create_llm(model_key)

        return cls.instances[model_key]

    @classmethod
    def create_llm(cls, model_key: str) -> LLMClient:
        """根据 Profile 创建 LLM"""
        profile = cls.MODEL_PROFILES[model_key]
        provider = profile["provider"]

        if provider == "deepseek":
            return DeepSeekLLM(
                url=config.llm.BASE_URL,
                model=profile["model"],
                max_tokens=profile["max_tokens"],
                temperature=profile["temperature"],
                thinking=profile["thinking"],
                api_key=config.llm.API_KEY,
                timeout=config.llm.TIMEOUT,
            )

        if provider == "ollama":
            return OllamaLLM(
                url=config.embedding.BASE_URL,
                model=profile["model"],
                max_tokens=profile["max_tokens"],
                temperature=profile["temperature"],
                thinking=profile["thinking"],
                timeout=config.llm.TIMEOUT,
            )

        raise ValueError(f"不支持的 LLM Provider: {provider}")


llm_factory = LLMFactory()