package com.yumu.noveltranslator.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtAuthenticationEntryPoint 测试")
class JwtAuthenticationEntryPointTest {

    private final JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint();

    @Test
    @DisplayName("commence返回401和JSON错误响应")
    void commenceReturns401AndJsonError() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        BadCredentialsException ex = new BadCredentialsException("Invalid credentials");

        entryPoint.commence(request, response, ex);

        assertEquals(401, response.getStatus());
        String content = response.getContentAsString();
        // Verify it's a JSON object with expected keys
        assertTrue(content.contains("\"success\"") || content.contains("success"),
            "Response should contain 'success' field. Got: " + content);
        assertNotNull(content);
        assertFalse(content.isEmpty(), "Response should not be empty");
    }
}
