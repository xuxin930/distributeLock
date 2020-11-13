package org.bj.yinsan.concurrent;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * @Auther: xuxin930
 * @Date: 2020/11/13/11:52 上午
 * @Description:
 */
public class DistributeReetrantLock implements Lock {

    private static final String LOCK_SCRIPT = "scripts/redisReetrantLock.lua";

    private static final String UNLOCK_SCRIPT = "scripts/redisReetrantUnLock.lua";

    /**
     * redis链接
     */
    private RedisTemplate redisTemplate;

    /**
     * 需要加锁的key
     */
    private String lockKey;

    /**
     * 锁的唯一标识,防止被其他lockId 加锁/解锁
     */
    private String lockId = "";
    /**
     * lockId最大并发加锁次数数,超过maxLockCount认为加锁失败,-1表示无限制
     */
    private int maxLockCount = -1;
    /**
     * 加锁自动释放时间(秒)
     * 默认3秒 防止重入锁被其他线程释放
     */
    private long lockTime = 3;


    /**
     * 按lockId可重入
     *
     * @param redisTemplate
     * @param lockKey       需要加锁的lockKey
     * @param lockId        需要加锁的业务唯一标识
     */
    public DistributeReetrantLock(RedisTemplate redisTemplate, String lockKey, String lockId) {
        this.redisTemplate = redisTemplate;
        this.lockKey = lockKey;
        this.lockId = lockId;
    }

    /**
     * 按lockId可重入,多个业务加同一个锁个别业务需要不支持可重入时使用
     *
     * @param redisTemplate
     * @param lockKey       需要加锁的lockKey
     * @param lockId        需要加锁的业务唯一标识,1个lockId只能出现一种maxLockCount
     * @param maxLockCount  最大加锁次数,1不可重入,-1可重入,一般设置-1,如果传0则自动改为1
     */
    public DistributeReetrantLock(RedisTemplate redisTemplate, String lockKey, String lockId, int maxLockCount) {
        this.redisTemplate = redisTemplate;
        this.lockKey = lockKey;
        this.lockId = lockId;
        if (maxLockCount < -1)
            maxLockCount = -1;
        if (maxLockCount == 0) {
            maxLockCount = 1;
        }
        this.maxLockCount = maxLockCount;
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
                Long result = (Long) redisTemplate.execute(lockScript, redisKeyList, lockId, String.valueOf(maxLockCount), String.valueOf(10));
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
     * 未实现
     *
     * @throws InterruptedException
     */
    public void lockInterruptibly() throws InterruptedException {

    }

    /**
     * 默认加锁3s,如果需要提前释放则手动unlock
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
        Long result = (Long) redisTemplate.execute(lockScript, redisKeyList, lockId, String.valueOf(maxLockCount), String.valueOf(lockTime));
        if (result == 1) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 最少加锁3s,小于3s按3s,如果需要提前释放则手动unlock
     * 加锁成功立即返回true
     * 加锁失败立即返回false
     *
     * @param time 加锁时间,下限3s,上限10s,大于10s自动改为10s,小于3,自动改为3
     * @param unit 加锁单位
     * @return
     * @throws InterruptedException
     */
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        long seconds = unit.toSeconds(time);
        if (seconds < 3) {
            seconds = 3;
        }
        if (seconds > 10) {
            seconds = 10;
        }
        DefaultRedisScript<Long> lockScript = new DefaultRedisScript();
        lockScript.setLocation(new ClassPathResource(LOCK_SCRIPT));
        lockScript.setResultType(Long.class);
        List<String> redisKeyList = new ArrayList<String>();
        redisKeyList.add(lockKey);
        Long result = (Long) redisTemplate.execute(lockScript, redisKeyList, lockId, String.valueOf(maxLockCount), String.valueOf(seconds));
        if (result == 1) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 解锁
     * 可重入锁计数减1,计数为0则释放锁，如果是互斥锁则删除锁
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
     * 外部强制解锁,lockId对应不上则不能解锁
     * 可重入锁计数器减1,计数为0则释放锁，如果是互斥锁则删除锁)
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
                result = (Long) redisTemplate.execute(lockScript, redisKeyList, lockId, String.valueOf(maxLockCount), String.valueOf(lockTime));
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


    public Condition newCondition() {
        return null;
    }
}
