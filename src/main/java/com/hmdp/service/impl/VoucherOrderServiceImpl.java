package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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

    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        // 1.查詢優惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2.判斷秒殺是否開始
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒殺尚未開始!");
        }
        // 3.判斷秒殺是否已經結束
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒殺已經結束!");
        }
        // 4.判斷庫存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("庫存不足! ");
        }
        // 5.扣減庫存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock-1")
                .eq("voucher_id", voucherId).update();
        if(!success){
            // 扣減失敗
            return Result.fail("庫存不足!");
        }
        // 6.創建訂單
        VoucherOrder voucherOrder = new VoucherOrder();
        // 6.1訂單id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 6.2用戶id
        Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        // 6.3代金卷id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        // 7.返回訂單id
        return Result.ok(orderId);
    }
}
