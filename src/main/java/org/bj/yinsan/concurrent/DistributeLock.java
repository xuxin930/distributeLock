package org.bj.yinsan.concurrent;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * @Auther: xuxin930
 * @Date: 2020/11/13/11:47 上午
 * @Description:
 */
@Component
public class DistributeLock implements Lock {

    private static final String LOCK_SCRIPT = "scripts/redisLock.lua";

    private static final String UNLOCK_SCRIPT = "scripts/redisUnLock.lua";

    /**
     * redis链接
     */
    private RedisTemplate redisTemplate;

    /**
     * 需要加锁的key
     */
    private String lockKey;

    /**
     * 锁的唯一标识,防止被其他线程解锁
     */
    private String lockId = "";

    /**
     * 加锁自动释放时间(秒)
     * 默认1秒
     */
    private long lockTime = 1;

    /**
     * 创建分布式锁
     *
     * @param redisTemplate
     * @param lockKey
     */
    public DistributeLock(RedisTemplate redisTemplate, String lockKey) {
        this.redisTemplate = redisTemplate;
        this.lockKey = lockKey;
    }

    /**
     * 创建分布式锁
     *
     * @param redisTemplate
     * @param lockKey
     * @param lockId
     */
    public DistributeLock(RedisTemplate redisTemplate, String lockKey, String lockId) {
        this.redisTemplate = redisTemplate;
        this.lockKey = lockKey;
        if (lockId != null) {
            this.lockId = lockId;
        }
    }

    /**
     * 使用lock必须手动解锁,否则会强制锁10s
     * 每隔100毫秒重试尝试加锁,直至加锁成功
     * 非特殊场景不建议使用
     */
    public void lock() {
        try {
            DefaultRedisScript<Long> lockScript = new DefaultRedisScript();
            lockScript.setLocation(new ClassPathResource(LOCK_SCRIPT));
            lockScript.setResultType(Long.class);
            List<String> redisKeyList = new ArrayList<String>();
            redisKeyList.add(lockKey);
            while (true) {
                Long result = (Long) redisTemplate.execute(lockScript, redisKeyList, lockId, String.valueOf(10));
                if (result == 1) {
                    break;
                }
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 解锁
     */
    public void unlock() {
        DefaultRedisScript<Long> lockScript = new DefaultRedisScript();
        lockScript.setLocation(new ClassPathResource(UNLOCK_SCRIPT));
        lockScript.setResultType(Long.class);
        List<String> redisKeyList = new ArrayList<String>();
        redisKeyList.add(lockKey);
        redisTemplate.execute(lockScript, redisKeyList, lockId);
    }

    /**
     * 外部强制解锁,锁没有Id情况下可以解锁
     *
     * @param redisTemplate redis连接
     * @param lockKey       强制解锁的key
     */
    public static void unlock(RedisTemplate redisTemplate, String lockKey) {
        DefaultRedisScript<Long> lockScript = new DefaultRedisScript();
        lockScript.setLocation(new ClassPathResource(UNLOCK_SCRIPT));
        lockScript.setResultType(Long.class);
        List<String> redisKeyList = new ArrayList<String>();
        redisKeyList.add(lockKey);
        redisTemplate.execute(lockScript, redisKeyList, "");
    }

    /**
     * 外部强制解锁,lockId对应不上则不能解锁
     *
     * @param redisTemplate redis连接
     * @param lockKey       强制解锁的key
     * @param lockId        锁的唯一标识
     */
    public static void unlock(RedisTemplate redisTemplate, String lockKey, String lockId) {
        DefaultRedisScript<Long> lockScript = new DefaultRedisScript();
        lockScript.setLocation(new ClassPathResource(UNLOCK_SCRIPT));
        lockScript.setResultType(Long.class);
        List<String> redisKeyList = new ArrayList<String>();
        redisKeyList.add(lockKey);
        redisTemplate.execute(lockScript, redisKeyList, lockId);
    }

    /**
     * 未实现
     *
     * @throws InterruptedException
     */
    public void lockInterruptibly() throws InterruptedException {

    }

    /**
     * 默认加锁1s,如果需要提前释放则手动unlock
     * 加锁成功立即返回true
     * 加锁失败立即返回false
     *
     * @return
     */
    public boolean tryLock() {
        DefaultRedisScript<Long> lockScript = new DefaultRedisScript();
        lockScript.setLocation(new ClassPathResource(LOCK_SCRIPT));
        lockScript.setResultType(Long.class);
        List<String> redisKeyList = new ArrayList<String>();
        redisKeyList.add(lockKey);
        Long result = (Long) redisTemplate.execute(lockScript, redisKeyList, lockId, String.valueOf(lockTime));
        if (result == 1) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 默认重试加锁3次，重试间隔100毫秒
     *
     * @return
     */
    public boolean retryLock() {
        return this.retryLock(3, 100);
    }

    /**
     * 默认重试加锁3次
     *
     * @param retrySleepTime 自定义重试间隔(毫秒)
     * @return
     */
    public boolean retryLock(int retrySleepTime) {
        return this.retryLock(3, retrySleepTime);
    }

    /**
     * 自定义加锁,立即返回加锁成功还是失败,方法最长耗时=重试加锁次数*重试加锁时间间隔
     *
     * @param retryTimes     重试加锁次数
     * @param retrySleepTime 重试加锁时间间隔(毫秒)
     * @return
     */
    public boolean retryLock(int retryTimes, int retrySleepTime) {
        DefaultRedisScript<Long> lockScript = new DefaultRedisScript();
        lockScript.setLocation(new ClassPathResource(LOCK_SCRIPT));
        lockScript.setResultType(Long.class);
        List<String> redisKeyList = new ArrayList<String>();
        redisKeyList.add(lockKey);
        int realRetryTimes = 0;
        Long result = 0l;
        try {
            while (realRetryTimes < retryTimes) {
                result = (Long) redisTemplate.execute(lockScript, redisKeyList, lockId, String.valueOf(lockTime));
                if (result == 1) {
                    break;
                }
                realRetryTimes++;
                Thread.sleep(retrySleepTime);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (result == 1) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 最少加锁1s,小于1s按1s,如果需要提前释放则手动unlock
     * 加锁成功立即返回true
     * 加锁失败立即返回false
     *
     * @param time 加锁时间,下限1s,上限10s,大于10s自动改为10s,小于1,自动改为1
     * @param unit 加锁单位
     * @return
     * @throws InterruptedException
     */
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        long seconds = unit.toSeconds(time);
        if (seconds < 1) {
            seconds = 1;
        }
        if (seconds > 10) {
            seconds = 10;
        }
        DefaultRedisScript<Long> lockScript = new DefaultRedisScript();
        lockScript.setLocation(new ClassPathResource(LOCK_SCRIPT));
        lockScript.setResultType(Long.class);
        List<String> redisKeyList = new ArrayList<String>();
        redisKeyList.add(lockKey);
        Long result = (Long) redisTemplate.execute(lockScript, redisKeyList, lockId, String.valueOf(seconds));
        if (result == 1) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 未实现
     *
     * @return
     */
    public Condition newCondition() {
        return null;
    }

}