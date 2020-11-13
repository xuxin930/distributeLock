local lockKey = KEYS[1]
local lockId = ARGV[1]
local lockTime = tonumber(ARGV[2])
local ret=redis.call("setnx",lockKey,lockId);
if (ret==1) then
    redis.call("expire",lockKey,lockTime)
    return 1
else
    return 0
end
