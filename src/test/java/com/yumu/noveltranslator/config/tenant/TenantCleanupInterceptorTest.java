package com.yumu.noveltranslator.config.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("TenantCleanupInterceptor 测试")
class TenantCleanupInterceptorTest {

    private TenantCleanupInterceptor interceptor;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        interceptor = new TenantCleanupInterceptor();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        TenantContext.clear();
        if (closeable != null) closeable.close();
    }

    @Test
    void 正常请求后清除租户上下文() throws Exception {
        TenantContext.setTenantId(42L);

        interceptor.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void 即使filterChain抛出异常也清除租户上下文() throws Exception {
        TenantContext.setTenantId(42L);
        doThrow(new RuntimeException("test error")).when(filterChain).doFilter(request, response);

        assertThrows(RuntimeException.class, () -> interceptor.doFilterInternal(request, response, filterChain));

        assertNull(TenantContext.getTenantId());
    }

    @Test
    void 清除绕过标记() throws Exception {
        TenantContext.setBypassTenant(true);

        interceptor.doFilterInternal(request, response, filterChain);

        assertFalse(TenantContext.isBypassTenant());
    }
}
