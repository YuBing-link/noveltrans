package com.yumu.noveltranslator.config;

import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.mapper.UserMapper;
import com.yumu.noveltranslator.util.PasswordUtil;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动时自动创建测试账号（仅当不存在时）
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;

    public DataInitializer(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public void run(String... args) {
        String testEmail = "test@test.com";
        if (userMapper.findByEmail(testEmail) == null) {
            User testUser = new User();
            testUser.setEmail(testEmail);
            testUser.setUsername("测试用户");
            testUser.setPassword(PasswordUtil.hashPassword("test123456"));
            testUser.setUserLevel("FREE");
            testUser.setStatus("ACTIVE");
            userMapper.insert(testUser);
        }
    }
}
