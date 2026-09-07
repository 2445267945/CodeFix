import logging
import os

from transformers import AutoTokenizer

logger = logging.getLogger(__name__)


def load_tokenizer(model_name: str, trust_remote_code: bool = True):
    """
    优先使用本地 Hugging Face Cache。
    本地不存在时，再尝试从远程下载。
    """

    try:
        logger.info("尝试加载本地 tokenizer: %s", model_name)
        return AutoTokenizer.from_pretrained(model_name, trust_remote_code=trust_remote_code, local_files_only=True)

    except OSError:
        logger.info("本地 tokenizer 不存在: %s", model_name)

    # 用户显式指定 HF_ENDPOINT
    endpoint = os.getenv("HF_ENDPOINT")
    if endpoint:
        logger.info("使用 Hugging Face Endpoint: %s", endpoint)
        return AutoTokenizer.from_pretrained(model_name, trust_remote_code=trust_remote_code)

    # 官方地址
    try:
        logger.info("首次加载 tokenizer，尝试 Hugging Face 官方地址: %s", model_name)
        return AutoTokenizer.from_pretrained(model_name, trust_remote_code=trust_remote_code)
    except Exception as e:
        logger.warning("Hugging Face 官方地址加载失败: %s", e)

    # 镜像
    os.environ["HF_ENDPOINT"] = "https://hf-mirror.com"
    logger.info("尝试从 hf-mirror.com 加载 tokenizer: %s", model_name)

    return AutoTokenizer.from_pretrained(model_name, trust_remote_code=trust_remote_code)