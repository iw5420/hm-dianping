package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    // 業務的名稱; 鎖的名稱
    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";

    @Override
    public boolean tryLock(long timeoutSec) {
        // 獲取所標示
        long threadId = Thread.currentThread().getId();
        // 獲取鎖
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                KEY_PREFIX + name, threadId + "", timeoutSec, TimeUnit.SECONDS);
        // 避免拆箱可能產生空指針
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        //釋放鎖
        stringRedisTemplate.delete(KEY_PREFIX + name);
    }
}
