package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

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

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.校驗手機號
        if(RegexUtils.isPhoneInvalid(phone)){
            // 2.如果不符合, 返回錯誤信息
            return Result.fail("手機號格式錯誤");
        }
        // 3.符合, 生成驗證碼 6位數字
        String code = RandomUtil.randomNumbers(6);

        // 4.保存驗證碼到redis // set key value ex 120
        // key加入業務前綴以免衝突
        // 設置有效期, 避免被惡意攻擊塞滿
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 5.發送驗證碼
        log.debug("發送短信驗證碼成功, 驗證碼: {}", code);
        //返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.校驗手機號
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            // 2.如果不符合, 返回錯誤信息
            return Result.fail("手機號格式錯誤");
        }
        // 3.從redis獲取校驗驗證碼
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if(cacheCode == null || !cacheCode.equals(code)){
            // 不一致, 報錯-反向校驗, 避免嵌套
            return Result.fail("驗證碼錯誤");
        }
        // 4.一致, 根據手機號查詢用戶
        User user = query().eq("phone", phone).one();

        // 5.判斷用戶是否存在
        if(user == null){
            // 6.不存在, 創建新用戶並保存
            user = createUserWithPhone(phone);
        }

        // 7.保存用戶信息到redis中
        // 7.1.隨機生成token, 作為登陸令牌
        String token = UUID.randomUUID().toString(true);
        // 7.2.從User對象轉為Hash存儲
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));
        // 7.3.存儲
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 7.4.設置token有效期
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 8.返回token
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        // 1.獲取當前登入用戶
        Long userId = UserHolder.getUser().getId();
        // 2.獲取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.獲取今天是本月的第幾天
        int dayOfMonth = now.getDayOfMonth();
        // 5.寫入Redis SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 1.獲取當前登入用戶
        Long userId = UserHolder.getUser().getId();
        // 2.獲取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.獲取今天是本月的第幾天
        int dayOfMonth = now.getDayOfMonth();
        // 5.獲取本月截止今天為止的所有簽到記錄, 返回是一個10進制的數字 BITFIELD sign:5:202203 GET u14 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if(result == null || result.isEmpty()){
            //沒有任何簽到結果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if(num == null || num == 0){
            return Result.ok(0);
        }
        // 6.循環遍歷
        int count = 0;
        while(true) {
            // 6.1.讓這個數字與1作與運算, 得到數字的最後一個bit位
            // 6.2.判斷這個bit位是否為0
            if((num & 1) == 0){
                // 如果為0, 說明未簽到, 結束
                break;
            }else{
                // 如果不為0, 說明已簽到, 計數器+1
                count++;
            }
            // 把數字右移一位, 拋棄最後一個bit位,　繼續下一個bit位
            num >>>=1;
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        // 1.創建用戶
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 2.保存用戶
        save(user);
        return user;
    }
}
