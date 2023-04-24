package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        // 緩存穿透
        // Shop shop = queryWithPassThrough(id);
        Shop shop = queryWithMutex(id);
        return Result.ok(shop);
    }

    public Shop queryWithMutex(Long id){
        String key = CACHE_SHOP_KEY + id;
        // 1.從redis查詢商鋪緩存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判斷是否存在
        if(StrUtil.isNotBlank(shopJson)){
            // 3.存在, 直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if(shopJson !=null){
            // 返回一個錯誤信息
            return null;
        }
        // 4.實現緩存重建
        // 4.1.獲取互斥鎖
        // 4.2.判斷是否獲取成功
        // 4.3.失敗, 則休眠並重試

        // 4.4.成功, 根據id查詢資料庫
        Shop shop = getById(id);
        // 5.不存在, 返回錯誤
        if(shop == null){
            // 將空值寫入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 返回錯誤信息
            return null;
        }
        // 6.存在, 寫入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 7.釋放互斥鎖
        // 8.返回
        return shop;
    }


    // 緩存穿透
    public Shop queryWithPassThrough(Long id){
        String key = CACHE_SHOP_KEY + id;
        // 1.從redis查詢商鋪緩存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判斷是否存在
        if(StrUtil.isNotBlank(shopJson)){
            // 3.存在, 直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if(shopJson !=null){
            // 返回一個錯誤信息
            return null;
        }
        // 4.不存在, 根據id查詢資料庫
        Shop shop = getById(id);
        // 5.不存在, 返回錯誤
        if(shop == null){
            // 將空值寫入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 返回錯誤信息
            return null;
        }
        // 6.存在, 寫入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 7.返回
        return shop;
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

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店鋪id不能為空");
        }
        // 1.更新資料庫
        updateById(shop);
        // 2.刪除緩存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
