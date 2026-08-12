from redis.asyncio import Redis
from App.config import config

class RedisService:
    def __init__(self, url: str):
        self.client = Redis.from_url(url, decode_responses=True)

    async def ping(self):
        return await self.client.ping()

    async def close(self):
        await self.client.aclose()

redis_service = RedisService(config.redis.REDIS_URL)
