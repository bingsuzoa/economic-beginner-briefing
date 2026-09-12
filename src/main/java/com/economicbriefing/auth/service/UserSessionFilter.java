package com.economicbriefing.auth.service;

import java.io.IOException;

import com.economicbriefing.auth.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class UserSessionFilter extends OncePerRequestFilter {
    private static final String SESSION_USER_ID = "USER_ID";
    private final ObjectProvider<UserRepository> userRepository;

    public UserSessionFilter(ObjectProvider<UserRepository> userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.equals("/api/auth/me") && !path.startsWith("/api/briefing");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        String userId = session == null ? null : (String) session.getAttribute(SESSION_USER_ID);
        UserRepository repository = userRepository.getIfAvailable();
        if (userId != null && repository != null && !repository.existsById(userId)) {
            session.invalidate();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
