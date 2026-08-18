local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refillTokens = tonumber(ARGV[2])
local refillPeriodSeconds = tonumber(ARGV[3])
local now = tonumber(ARGV[4])
local requested = 1

local bucket = redis.call("HMGET", key, "tokens", "last_refill")
local tokens = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

if not tokens then
    tokens = capacity
    last_refill = now
else
    local elapsedTime = now - last_refill
    local intervals = math.floor(elapsedTime / refillPeriodSeconds)
    if intervals > 0 then
        tokens = math.min(capacity, tokens + (intervals * refillTokens))
        last_refill = last_refill + (intervals * refillPeriodSeconds)
    end
end

if tokens >= requested then
    tokens = tokens - requested
    redis.call("HMSET", key, "tokens", tokens, "last_refill", last_refill)
    redis.call("EXPIRE", key, math.ceil(capacity / refillTokens) * refillPeriodSeconds * 2)
    return 1
else
    redis.call("HMSET", key, "tokens", tokens, "last_refill", last_refill)
    return 0
end
