local lockKey = KEYS[1]
local lockId = ARGV[1]
local retLockId=redis.call("get",lockKey);
if (retLockId==lockId) then
    redis.call("del",lockKey)
end



