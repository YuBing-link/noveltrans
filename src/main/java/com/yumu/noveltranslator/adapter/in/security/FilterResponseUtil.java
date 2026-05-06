package com.yumu.noveltranslator.adapter.in.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility for writing consistent JSON error responses from security filters.
 */
public final class FilterResponseUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private FilterResponseUtil() {}

    /**
     * Write a JSON error response and flush the buffer.
     */
    public static void writeJsonError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("data", null);
        body.put("code", code);
        body.put("message", message);
        body.put("token", null);

        OBJECT_MAPPER.writeValue(response.getWriter(), body);
        response.flushBuffer();
    }
}
