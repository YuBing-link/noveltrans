package com.yumu.noveltranslator.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    void okCreatesSuccessResult() {
        Result<String> result = Result.ok("data");
        assertTrue(result.isSuccess());
        assertEquals("data", result.getData());
        assertEquals("00000", result.getCode());
        assertNull(result.getMessage());
        assertNull(result.getToken());
    }

    @Test
    void okWithTokenCreatesSuccessResultWithToken() {
        Result<String> result = Result.okWithToken("data", "token123");
        assertTrue(result.isSuccess());
        assertEquals("data", result.getData());
        assertEquals("token123", result.getToken());
        assertEquals("00000", result.getCode());
    }

    @Test
    void errorCreatesFailureResult() {
        Result<Void> result = Result.error("Something went wrong");
        assertFalse(result.isSuccess());
        assertNull(result.getData());
        assertNull(result.getToken());
        assertEquals("B0001", result.getCode());
        assertEquals("Something went wrong", result.getMessage());
    }

    @Test
    void settersWork() {
        Result<String> result = new Result<>();
        result.setSuccess(true);
        result.setData("test");
        result.setCode("200");
        result.setMessage("OK");
        result.setToken("jwt-token");

        assertTrue(result.isSuccess());
        assertEquals("test", result.getData());
        assertEquals("200", result.getCode());
        assertEquals("OK", result.getMessage());
        assertEquals("jwt-token", result.getToken());
    }
}
