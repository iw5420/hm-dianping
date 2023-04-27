package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    // 業務的名稱; 鎖的名稱
    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private static final DefaultRedisScript<Long>UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    @Override
    public boolean tryLock(long timeoutSec) {
        // 獲取所標示
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 獲取鎖
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        // 避免拆箱可能產生空指針
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        // 調用lua腳本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId()
        );
    }

//    @Override
//    public void unlock() {
//        // 獲取線程標示
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//        // 獲取鎖中的標示
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        if(threadId.equals(id)) {
//            // 釋放鎖
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//    }
}
