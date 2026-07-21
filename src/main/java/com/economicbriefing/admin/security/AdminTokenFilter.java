package com.economicbriefing.admin.security;

import java.io.IOException;

import com.economicbriefing.config.AdminProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class AdminTokenFilter extends OncePerRequestFilter {

    private final String adminToken;
    private final ObjectMapper objectMapper;

    public AdminTokenFilter(String adminToken, ObjectMapper objectMapper) {
        this.adminToken = adminToken;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        if (adminToken == null || adminToken.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorized(response, "인증 토큰이 필요합니다.");
            return;
        }

        String token = authHeader.substring(7);
        if (!token.equals(adminToken)) {
            sendUnauthorized(response, "유효하지 않은 인증 토큰입니다.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/admin");
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("success", false);
        body.put("code", "UNAUTHORIZED");
        body.put("message", message);

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
