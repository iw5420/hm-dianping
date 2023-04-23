package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    //由spring構建的物件就能依賴注入
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //攔截部分請求需登入
       registry.addInterceptor(new LoginInterceptor())
               .excludePathPatterns(
                 "/shop/**",
                 "/voucher/**",
                 "/shop-type/**",
                 "/blog/hot",
                 "/user/code",
                 "/user/login"
               ).order(1);
       //攔截所有請求刷新token, order為0先執行
        registry.addInterceptor( new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }
}
