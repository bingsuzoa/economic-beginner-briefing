package com.economicbriefing.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminTokenFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldPassThroughWhenTokenIsBlank() throws Exception {
        AdminTokenFilter filter = new AdminTokenFilter("", objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    }

    @Test
    void shouldPassThroughWhenTokenIsNull() throws Exception {
        AdminTokenFilter filter = new AdminTokenFilter(null, objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldRejectWhenNoAuthorizationHeader() throws Exception {
        AdminTokenFilter filter = new AdminTokenFilter("my-secret", objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(chain, never()).doFilter(request, response);
        assertTrue(response.getContentAsString().contains("UNAUTHORIZED"));
    }

    @Test
    void shouldRejectWhenTokenInvalid() throws Exception {
        AdminTokenFilter filter = new AdminTokenFilter("my-secret", objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/status");
        request.addHeader("Authorization", "Bearer wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void shouldPassWhenTokenValid() throws Exception {
        AdminTokenFilter filter = new AdminTokenFilter("my-secret", objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/status");
        request.addHeader("Authorization", "Bearer my-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    }

    @Test
    void shouldNotFilterNonAdminPaths() throws Exception {
        AdminTokenFilter filter = new AdminTokenFilter("my-secret", objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");

        assertFalse(filter.shouldNotFilter(request) == false);
    }

    @Test
    void shouldFilterAdminPaths() throws Exception {
        AdminTokenFilter filter = new AdminTokenFilter("my-secret", objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/runs");

        assertTrue(filter.shouldNotFilter(request) == false);
    }

    @Test
    void shouldRejectNonBearerAuthScheme() throws Exception {
        AdminTokenFilter filter = new AdminTokenFilter("my-secret", objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/status");
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    }
}
