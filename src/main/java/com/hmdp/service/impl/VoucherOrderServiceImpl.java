package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long>SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 獲取用戶
        Long userId = UserHolder.getUser().getId();
        // 1.執行lua腳本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        // 2.判斷結果是否為0
        int r = result.intValue();
        if(r != 0){
            // 2.1.不為0, 代表沒有購買資格
            return Result.fail(r == 1 ? "庫存不足": "不能重複下單");
        }
        // 2.2.為0, 有購買資格, 把下單信息保存到阻塞隊列
        long orderId = redisIdWorker.nextId("order");
        //TODO 保存阻塞隊列
        // 3.返回訂單id
        return Result.ok(orderId);
    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 1.查詢優惠卷
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        // 2.判斷秒殺是否開始
//        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
//            return Result.fail("秒殺尚未開始!");
//        }
//        // 3.判斷秒殺是否已經結束
//        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
//            return Result.fail("秒殺已經結束!");
//        }
//        // 4.判斷庫存是否充足
//        if (voucher.getStock() < 1) {
//            return Result.fail("庫存不足! ");
//        }
//        Long userId = UserHolder.getUser().getId();
//        // 創建鎖對象
//        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        // 獲取鎖
//        boolean isLock = lock.tryLock();
//        if(!isLock){
//            // 獲取鎖失敗, 返回錯誤或重試
//            return Result.fail("不允許重複下單");
//        }
//        try{
//            // 獲取代理對象 (事務)
//            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }finally {
//            // 釋放鎖
//            lock.unlock();
//        }
//    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 5.一人一單
        Long userId = UserHolder.getUser().getId();

        // 5.1.查詢訂單
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        // 5.2.判斷是否存在
        if(count > 0){
            return Result.fail("用戶已經購買過一次! ");
        }

        // 6.扣減庫存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock-1") //set stock = stock -1
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update(); // where id = ? and stock > 0
        if(!success){
            // 扣減失敗
            return Result.fail("庫存不足!");
        }

        // 7.創建訂單
        VoucherOrder voucherOrder = new VoucherOrder();
        // 7.1訂單id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 7.2用戶id
        voucherOrder.setUserId(userId);
        // 7.3代金卷id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        // 8.返回訂單id
        return Result.ok(orderId);

    }
}
