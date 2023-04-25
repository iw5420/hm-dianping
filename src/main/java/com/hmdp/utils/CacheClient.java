package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
       stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        //設置過期時間
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        //寫入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // 緩存穿透
    public <R, ID>R queryWithPassThrough(
            String keyPrefix, ID id, Class<R>type, Function<ID, R>dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        // 1.從redis查詢商鋪緩存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判斷是否存在
        if(StrUtil.isNotBlank(json)){
            // 3.存在, 直接返回
            return JSONUtil.toBean(json, type);
        }
        if(json !=null){
            // 返回一個錯誤信息
            return null;
        }
        // 4.不存在, 根據id查詢資料庫
        R r = dbFallback.apply(id);
        // 5.不存在, 返回錯誤
        if(r == null){
            // 將空值寫入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 返回錯誤信息
            return null;
        }
        // 6.存在, 寫入redis
        this.set(key, r, time, unit);
        // 7.返回
        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R, ID>R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R>type, Function<ID, R>dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        // 1.從redis查詢商鋪緩存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判斷是否存在
        if(StrUtil.isBlank(json)){
            // 3.不存在, 直接返回null
            return null;
        }
        // 4.命中, 需要先把json反序列化為對象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5.判斷是否過期
        if(expireTime.isAfter(LocalDateTime.now())){
            // 5.1.未過期, 直接返回店鋪信息
            return r;
        }
        // 5.2.已過期, 需要緩存重建
        // 6.緩存重建
        // 6.1.獲取互斥鎖
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2.判斷是否獲取成功
        if(isLock){
            // 6.3.成功, 開始獨立線程, 實現緩存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                // 重建緩存
                try {
                    // 查詢資料庫
                    R newR = dbFallback.apply(id);
                    // 寫入redis
                    this.setWithLogicalExpire(key, newR, time, unit);
                } catch (Exception e) {
                    throw  new RuntimeException(e);
                } finally {
                    //釋放鎖
                    unlock(lockKey);
                }
            });
        }
        // 6.4. 返回過期的商城信息
        return r;
    }

    public <R, ID>R queryWithMutex(
            String keyPrefix, ID id, Class<R>type,  Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        // 1.從redis查詢商鋪緩存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判斷是否存在
        if(StrUtil.isNotBlank(shopJson)){
            // 3.存在, 直接返回
            return JSONUtil.toBean(shopJson, type);
        }
        if(shopJson !=null){
            // 返回一個錯誤信息
            return null;
        }
        // 4.實現緩存重建
        // 4.1.獲取互斥鎖
        String lockKey = LOCK_SHOP_KEY + id;
        R r = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 4.2.判斷是否獲取成功
            if(!isLock){
                // 4.3.失敗, 則休眠並重試
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }

            // 4.4.成功, 根據id查詢資料庫
            r = dbFallback.apply(id);
            // 模擬重建的延遲
            Thread.sleep(200);
            // 5.不存在, 返回錯誤
            if(r == null){
                // 將空值寫入redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                // 返回錯誤信息
                return null;
            }
            // 6.存在，写入redis
            this.set(key, r, time, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 7.釋放互斥鎖
            unlock(lockKey);
        }
        // 8.返回
        return r;
    }

    private boolean tryLock(String key){
        //10秒
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        //避免自動拆箱產生空指針
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

}
