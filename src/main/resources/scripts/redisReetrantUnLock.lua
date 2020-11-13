local lockKey = KEYS[1]
local lockId = ARGV[1]
local lockVal=redis.call("get",lockKey);

if (lockVal==false) then       --锁不存在不进行解锁
    return
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

local oldLockId=arrRst[1]
local lockCount=tonumber(arrRst[2])

if (lockId==oldLockId) then   --必须是同一个业务Id才能解锁
    lockCount=lockCount-1;
    if (lockCount<=0) then    --已经是最后一个锁则删除锁 否则计数器减1
        redis.call("del",lockKey)
    else
        local expireTime=redis.call("ttl",lockKey);
        redis.call("set",lockKey,lockId..":"..lockCount)
        redis.call("expire",lockKey,expireTime);
    end
end



