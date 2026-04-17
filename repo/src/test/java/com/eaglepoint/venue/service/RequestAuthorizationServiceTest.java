package com.eaglepoint.venue.service;

import com.eaglepoint.venue.domain.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestAuthorizationServiceTest {
    @Mock private AccountSecurityService accountSecurityService;
    private RequestAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new RequestAuthorizationService(accountSecurityService);
    }

    @Test
    void requireAuthenticated_nullToken_throws401() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.requireAuthenticated(null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void requireAuthenticated_emptyToken_throws401() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.requireAuthenticated("   "));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void requireAuthenticated_validToken_returnsUser() {
        UserAccount user = new UserAccount();
        user.setUsername("user1");
        user.setRole("SENIOR");
        when(accountSecurityService.requireUserByToken("tok-1")).thenReturn(user);

        UserAccount result = service.requireAuthenticated("tok-1");
        assertEquals("user1", result.getUsername());
    }

    @Test
    void requireAnyRole_authorizedRole_returnsUser() {
        UserAccount user = new UserAccount();
        user.setUsername("admin1");
        user.setRole("ORG_ADMIN");
        when(accountSecurityService.requireUserByToken("tok-admin")).thenReturn(user);
        // requireAnyRole on accountSecurityService should not throw for ORG_ADMIN

        UserAccount result = service.requireAnyRole("tok-admin", "ORG_ADMIN", "PLATFORM_ADMIN");
        assertEquals("admin1", result.getUsername());
        verify(accountSecurityService).requireAnyRole("ORG_ADMIN", "ORG_ADMIN", "PLATFORM_ADMIN");
    }

    @Test
    void requireAnyRole_unauthorizedRole_throws403() {
        UserAccount user = new UserAccount();
        user.setUsername("senior1");
        user.setRole("SENIOR");
        when(accountSecurityService.requireUserByToken("tok-senior")).thenReturn(user);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "insufficient role"))
            .when(accountSecurityService).requireAnyRole("SENIOR", "ORG_ADMIN", "PLATFORM_ADMIN");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.requireAnyRole("tok-senior", "ORG_ADMIN", "PLATFORM_ADMIN"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void requireSelfOrAnyRole_selfAccess_returnsUser() {
        UserAccount user = new UserAccount();
        user.setUsername("user_a");
        user.setRole("SENIOR");
        when(accountSecurityService.requireUserByToken("tok-a")).thenReturn(user);

        UserAccount result = service.requireSelfOrAnyRole("tok-a", "user_a", "MODERATOR");
        assertEquals("user_a", result.getUsername());
        // Should NOT call requireAnyRole since it's self-access
        verify(accountSecurityService, never()).requireAnyRole(anyString(), any());
    }

    @Test
    void requireSelfOrAnyRole_selfAccess_caseInsensitive() {
        UserAccount user = new UserAccount();
        user.setUsername("User_A");
        user.setRole("SENIOR");
        when(accountSecurityService.requireUserByToken("tok-a")).thenReturn(user);

        UserAccount result = service.requireSelfOrAnyRole("tok-a", "user_a", "MODERATOR");
        assertEquals("User_A", result.getUsername());
    }

    @Test
    void requireSelfOrAnyRole_differentUser_withPrivilegedRole_succeeds() {
        UserAccount user = new UserAccount();
        user.setUsername("admin_1");
        user.setRole("ORG_ADMIN");
        when(accountSecurityService.requireUserByToken("tok-admin")).thenReturn(user);

        UserAccount result = service.requireSelfOrAnyRole("tok-admin", "other_user", "ORG_ADMIN");
        assertEquals("admin_1", result.getUsername());
    }

    @Test
    void requireSelfOrAnyRole_differentUser_withoutPrivilegedRole_throws403() {
        UserAccount user = new UserAccount();
        user.setUsername("user_b");
        user.setRole("SENIOR");
        when(accountSecurityService.requireUserByToken("tok-b")).thenReturn(user);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "insufficient role"))
            .when(accountSecurityService).requireAnyRole("SENIOR", "MODERATOR");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.requireSelfOrAnyRole("tok-b", "other_user", "MODERATOR"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void requireAnyRole_nullToken_throws401() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.requireAnyRole(null, "ORG_ADMIN"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }
}
