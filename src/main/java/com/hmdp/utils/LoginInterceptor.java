package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    //因為這物件是由我們自己new出來, 所以不能用自動注入
    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override //進入controller前校驗用戶
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.獲取請求頭中的token
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank(token)){
            // 不存在, 攔截 返回401狀態碼
            response.setStatus(401);
            return false;
        }
        // 2.基於token獲取redis中的用戶
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);

        // 3.判斷用戶是否存在
        if(userMap.isEmpty()){
            // 4.不存在, 攔截 返回401狀態碼
            response.setStatus(401);
            return false;
        }
        // 5.將查詢到的Hash數據轉為UserDTO對象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        // 6.存在, 保存用戶信息到ThreadLocal
        UserHolder.saveUser(userDTO);
        // TODO 7.刷新token有效期
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        // TODO 8.執行
        return HandlerInterceptor.super.preHandle(request, response, handler);
    }

    @Override //銷毀內存中用戶訊息
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
        UserHolder.removeUser();
    }
}
