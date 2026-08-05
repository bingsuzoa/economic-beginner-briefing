package com.economicbriefing.auth;

import com.economicbriefing.auth.repository.UserRepository;
import com.economicbriefing.auth.service.UserSessionFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserSessionFilterTest {
    @Test
    void invalidatesSessionAfterAccountDeletion() throws Exception {
        UserRepository users = mock(UserRepository.class);
        @SuppressWarnings("unchecked") ObjectProvider<UserRepository> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(users);
        when(users.existsById("deleted-user")).thenReturn(false);
        UserSessionFilter filter = new UserSessionFilter(provider);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("USER_ID", "deleted-user");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertTrue(session.isInvalid());
        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }
}
