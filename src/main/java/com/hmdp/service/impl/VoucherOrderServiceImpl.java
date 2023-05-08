package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
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

    private BlockingQueue<VoucherOrder>orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // 當前類初始化後執行
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while (true){
                try{
                    // 1.獲取隊列中的訂單信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 2.創建訂單
                    handleVoucherOrder(voucherOrder);

                }catch (Exception e){
                    log.error("處裡訂單異常", e);
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 1.獲取用戶
        Long userId = voucherOrder.getUserId();
        // 2.創建鎖對象
         RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 3.獲取鎖
        boolean isLock = lock.tryLock();
        // 4.判斷是否獲取鎖成功
        if(!isLock){
            // 獲取鎖失敗, 返回錯誤或重試
            log.error("不允許重複下單");
            return ;
        }
        try{
            //因為為了讓子線程取得代理對象, 所以先放到常數變量中
            proxy.createVoucherOrder(voucherOrder);
        }finally {
            // 釋放鎖
            lock.unlock();
        }
    }

    private IVoucherOrderService proxy;

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
        VoucherOrder voucherOrder = new VoucherOrder();
        // 2.3.訂單id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 2.4.用戶id
        voucherOrder.setUserId(userId);
        // 2.5.代金卷id
        voucherOrder.setVoucherId(voucherId);
        // 2.6.放入阻塞隊列
        orderTasks.add(voucherOrder);

        // 3.獲取代理對象
        proxy = (IVoucherOrderService)AopContext.currentProxy();
        //
        // 4.返回訂單id
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
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 5.一人一單
        Long userId = voucherOrder.getUserId();

        // 5.1.查詢訂單
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        // 5.2.判斷是否存在
        if(count > 0){
            log.error("用戶已經購買過一次! ");
            return;
        }

        // 6.扣減庫存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock-1") //set stock = stock -1
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)
                .update(); // where id = ? and stock > 0
        if(!success){
            // 扣減失敗
            log.error("庫存不足!");
            return;
        }

        // 7.創建訂單
        save(voucherOrder);
    }
}
