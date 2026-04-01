package com.eaglepoint.venue.api;

import com.eaglepoint.venue.domain.UserAccount;
import com.eaglepoint.venue.service.AccountSecurityService;
import com.eaglepoint.venue.service.ModerationService;
import com.eaglepoint.venue.service.PaymentReconciliationService;
import com.eaglepoint.venue.service.RequestAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SecurityControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ModerationService moderationService;

    @Mock
    private PaymentReconciliationService paymentReconciliationService;

    @Mock
    private AccountSecurityService accountSecurityService;

    private RequestAuthorizationService requestAuthorizationService;

    private ModerationController moderationController;

    @InjectMocks
    private PaymentController paymentController;

    @BeforeEach
    void setUp() {
        requestAuthorizationService = new RequestAuthorizationService(accountSecurityService);
        moderationController = new ModerationController(moderationService, requestAuthorizationService);
        mockMvc = MockMvcBuilders.standaloneSetup(moderationController, paymentController).build();
    }

    @Test
    void moderationEndpoint_requiresToken() throws Exception {
        mockMvc.perform(get("/api/moderation/reports"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void moderationEndpoint_rejectsForgedRoleHeaderWithoutPrivilegedToken() throws Exception {
        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setRole("SENIOR");
        user.setUsername("senior_user");

        when(accountSecurityService.requireUserByToken("token-1")).thenReturn(user);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "insufficient role permissions"))
                .when(accountSecurityService)
                .requireAnyRole("SENIOR", "MODERATOR", "ORG_ADMIN", "PLATFORM_ADMIN");

        mockMvc.perform(get("/api/moderation/reports")
                        .header("X-Auth-Token", "token-1")
                        .header("X-User-Role", "ORG_ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPaymentEndpoint_rejectsUnauthorizedRole() throws Exception {
        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setRole("SENIOR");
        user.setUsername("senior_user");

        when(accountSecurityService.requireUserByToken("token-1")).thenReturn(user);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "insufficient role permissions"))
                .when(accountSecurityService)
                .requireAnyRole("SENIOR", "ORG_ADMIN", "PLATFORM_ADMIN");

        mockMvc.perform(get("/api/payments/reconciliation/report")
                        .header("X-Auth-Token", "token-1"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(paymentReconciliationService);
    }
}
