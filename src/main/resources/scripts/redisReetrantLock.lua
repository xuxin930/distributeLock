local lockKey = KEYS[1]
local lockId = ARGV[1]
local maxLockCount = tonumber(ARGV[2])
local lockTime = tonumber(ARGV[3])

local lockVal = redis.call("get",lockKey);
if (lockVal==false) then       --锁不存在则添加锁
    redis.call("set",lockKey,lockId..":1")
    redis.call("expire",lockKey,lockTime)
    return 1
end

--字符串分割start
local sepStr=lockVal --待切分的字符串
local sep=":"    --分隔符
local pos, arrRst = 0, {}  --arrRst分割后的数组
for st, sp in function() return string.find( sepStr, sep, pos, true ) end do
    table.insert(arrRst, string.sub(sepStr, pos, st-1 ))
    pos = sp + 1
end
table.insert(arrRst, string.sub( sepStr, pos))
--字符串分割end

--已经有锁，判断是不是同一个业务，是同一个业务加lockCount，不是同一个业务加锁失败
local oldLockId=arrRst[1]
local lockCount=tonumber(arrRst[2])
if (lockId==oldLockId) then
    if (maxLockCount>0 and lockCount>=maxLockCount) then
        return 0
    end
    lockCount=lockCount+1
    redis.call("set",lockKey,lockId..":"..lockCount)
    redis.call("expire",lockKey,lockTime)
    return 1
end

-- lockId不一致 加锁失败
return 0
