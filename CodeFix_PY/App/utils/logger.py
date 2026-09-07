# 暂无统一处理+# App/utils/logger.py
import logging
import os
import sys
_DEFAULT_FORMAT = "%(asctime)s | %(levelname)-8s | %(name)s | %(message)s"
_DEFAULT_DATE_FORMAT = "%Y-%m-%d %H:%M:%S"
def setup_logging(level: str | None = None) -> None:
    """
    初始化全局日志配置。
    由进程入口（main.py lifespan）调用一次，
    使各模块通过 ``logging.getLogger(__name__)`` 创建的 logger 正常输出。
    可通过环境变量 LOG_LEVEL 覆盖级别，默认 INFO。
    """
    log_level = (level or os.getenv("LOG_LEVEL", "INFO")).upper()
    logging.basicConfig(
        level=getattr(logging, log_level, logging.INFO),
        format=_DEFAULT_FORMAT,
        datefmt=_DEFAULT_DATE_FORMAT,
        stream=sys.stdout,
        force=True,
    )