package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.校驗手機號
        if(RegexUtils.isPhoneInvalid(phone)){
            // 2.如果不符合, 返回錯誤信息
            return Result.fail("手機號格式錯誤");
        }
        // 3.符合, 生成驗證碼 6位數字
        String code = RandomUtil.randomNumbers(6);

        // 4.保存驗證碼到session
        session.setAttribute("code", code);

        // 5.發送驗證碼
        log.debug("發送短信驗證碼成功, 驗證碼: {}", code);
        //返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.校驗手機號

        // 2.校驗驗證碼

        // 3.不一致, 報錯

        // 4.一致, 根據手機號查詢用戶

        // 5.判斷用戶是否存在

        // 6.不存在, 創建新用戶並保存

        // 7.保存用戶信息到session中
        
        return null;
    }
}
