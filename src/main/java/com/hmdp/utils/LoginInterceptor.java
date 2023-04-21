package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.beans.BeanUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {

    @Override //進入controller前校驗用戶
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.獲取session
        HttpSession session = request.getSession();
        // 2.獲取session中的用戶
        Object user = session.getAttribute("user");
        // 3.判斷用戶是否存在
        if(user == null){
            // 4.不存在, 攔截 返回401狀態碼
            response.setStatus(401);
            return false;
        }

        // 5.存在, 保存用戶信息到ThreadLocal
        UserHolder.saveUser((UserDTO) user);
        // 6.執行
        return HandlerInterceptor.super.preHandle(request, response, handler);
    }

    @Override //銷毀內存中用戶訊息
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
        UserHolder.removeUser();
    }
}
