local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local bucket = redis.call('hgetall', key)
local tokens = tonumber(bucket[2]) or capacity
local last = tonumber(bucket[4]) or now
local delta = math.max(0, now - last)
local filled = math.min(capacity, tokens + (delta * rate))
if filled < 1 then
    return {0, filled, last}
else
    redis.call('hmset', key, 'tokens', filled - 1, 'timestamp', now)
    return {1, filled - 1, now}
end