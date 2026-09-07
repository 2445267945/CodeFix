from fastapi import FastAPI
from App.bootstrap.mq_bootstrap import MQBootstrap
from contextlib import asynccontextmanager
import uvicorn

from App.utils.logger import setup_logging

mq_bootstrap = MQBootstrap()

@asynccontextmanager
async def lifespan(app: FastAPI):
    setup_logging()
    mq_bootstrap.start()
    try:
        yield
    finally:
        mq_bootstrap.stop()

app = FastAPI(
    title="Java代码审计Agent",
    version="1.0",
    lifespan=lifespan
)

# ---------- 启动服务 ----------
if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)