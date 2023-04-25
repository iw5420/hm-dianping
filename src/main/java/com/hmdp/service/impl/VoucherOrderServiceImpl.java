package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

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

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.查詢優惠卷

        // 2.判斷秒殺是否開始

        // 3.判斷秒殺是否已經結束

        // 4.判斷庫存是否充足

        // 5.扣減庫存

        // 6.創建訂單

        // 7.返回訂單id
        return null;
    }
}
