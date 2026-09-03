import os

from dotenv import load_dotenv

load_dotenv()


PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

class LLMConfig:
    """大模型 Provider 连接配置"""
    API_KEY = os.getenv("LLM_API_KEY")
    BASE_URL = os.getenv("LLM_BASE_URL")
    TIMEOUT = float(os.getenv("LLM_TIMEOUT", "60.0"))



class OllamaConfig:
    """Ollama LLM 连接配置"""
    BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434/v1/chat/completions")


class EmbeddingConfig:
    """Embedding Provider 连接配置"""
    BASE_URL = os.getenv("EMBEDDING_BASE_URL", "http://localhost:11434")
    MODEL = os.getenv("EMBEDDING_MODEL", "nomic-embed-text")


class MQConfig:
    """RocketMQ 配置"""
    NAMESRV_ADDR = os.getenv("ROCKETMQ_NAMESRV_ADDR")
    CONSUMER_GROUP = os.getenv("ROCKETMQ_CONSUMER_GROUP")
    CONSUMER_TOPICS = (
        item.strip()
        for item in os.getenv("ROCKETMQ_CONSUMER_TOPICS", "").split(",")
        if item.strip()
    )
    STATUS_TOPIC = os.getenv("ROCKETMQ_STATUS_TOPIC")
    HEARTBEAT_TOPIC = os.getenv("ROCKETMQ_HEARTBEAT_TOPIC")

    MESSAGE_TYPES = tuple(
        item.strip()
        for item in os.getenv("MESSAGE_TYPE", "").split(",")
        if item.strip()
    )


class RedisConfig:
    """Redis 配置"""
    URL = os.getenv("REDIS_URL")

class WorkspaceConfig:
    """Workspace 配置"""
    ROOT = os.getenv("WORKSPACE_ROOT", "/data/workspaces")

class VectorDBConfig:
    """向量数据库配置"""
    CHROMA_PATH = os.path.normpath(os.path.join(PROJECT_ROOT, os.getenv("CHROMA_PATH", "./data/chroma_db")))
    COLLECTION_NAME = os.getenv("COLLECTION_NAME", "alibaba_manual")
    N_RESULTS = int(os.getenv("VECTOR_N_RESULTS", "2"))

class BackendConfig:
    """Java Backend 配置"""
    BASE_URL = os.getenv("BACKEND_BASE_URL", "http://localhost:8080")

class Config:
    llm = LLMConfig()
    ollama = OllamaConfig()
    embedding = EmbeddingConfig()
    mq = MQConfig()
    redis = RedisConfig()
    workspace = WorkspaceConfig()
    vector_db = VectorDBConfig()
    backend = BackendConfig()


config = Config()