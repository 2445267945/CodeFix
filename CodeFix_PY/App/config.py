# 读.env的配置
import os
from dotenv import load_dotenv

load_dotenv()
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
class LLMConfig:
    """大模型通用配置"""

    # 核心三要素
    API_KEY = os.getenv("LLM_API_KEY")
    BASE_URL = os.getenv("LLM_BASE_URL")
    MODEL = os.getenv("LLM_MODEL")
    ENTHINK = os.getenv("LLM_ENTHINK")

    # 通用请求参数
    MAX_TOKENS = int(os.getenv("LLM_MAX_TOKENS", "4096"))
    TEMPERATURE = float(os.getenv("LLM_TEMPERATURE", "0.3"))
    TIMEOUT = float(os.getenv("LLM_TIMEOUT", "60.0"))

class VectorDBConfig:
    raw_path = os.getenv("CHROMA_PATH", "./data/chroma_db")
    CHROMA_PATH = os.path.normpath(os.path.join(PROJECT_ROOT, raw_path))
    COLLECTION_NAME = os.getenv("COLLECTION_NAME", "alibaba_manual")
    OLLAMA_URL = os.getenv("OLLAMA_URL", "http://localhost:11434")
    EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "nomic-embed-text")
    # 检索返回条数
    N_RESULTS = int(os.getenv("VECTOR_N_RESULTS", "2"))

class Config:
    llm = LLMConfig()
    vector_db = VectorDBConfig()


config = Config()