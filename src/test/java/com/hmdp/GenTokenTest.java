package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

@Slf4j
@SpringBootTest
public class GenTokenTest extends ServiceImpl<UserMapper, User> {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void test(){
        List<User> userList = query().list();
        StringBuilder content = new StringBuilder();
        for(int i = 0 ; i<userList.size(); i++){

            String token = UUID.randomUUID().toString(true);
            // 7.2.從User對象轉為Hash存儲
            UserDTO userDTO = BeanUtil.copyProperties(userList.get(i), UserDTO.class);
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)
                            .setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));
            String tokenKey = LOGIN_USER_KEY + token;
            stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
            content.append(token);
            content.append("\r\n");
        }
        try {
            FileWriter fw= new FileWriter("D:/course data/hm-dianping course/tokens.txt");
            fw.write(content.toString());
            fw.flush();
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
