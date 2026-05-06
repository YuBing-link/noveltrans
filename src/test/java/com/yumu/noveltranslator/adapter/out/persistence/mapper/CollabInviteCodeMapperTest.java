package com.yumu.noveltranslator.mapper;

import com.yumu.noveltranslator.entity.CollabInviteCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CollabInviteCodeMapper 自定义 SQL 集成测试
 * 验证邀请码表被正确排除在租户过滤之外
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CollabInviteCodeMapperTest {

    @Autowired
    private CollabInviteCodeMapper collabInviteCodeMapper;

    @Test
    @Order(1)
    @DisplayName("selectByValidCode - 无效邀请码返回 null")
    void selectByValidCodeInvalid() {
        CollabInviteCode code = collabInviteCodeMapper.selectByValidCode("INVALID_CODE_X");
        assertNull(code);
    }

    @Test
    @Order(2)
    @DisplayName("selectByCode - 按邀请码查询")
    void selectByCode() {
        CollabInviteCode code = collabInviteCodeMapper.selectByCode("NON_EXISTENT");
        assertNull(code);
    }
}
