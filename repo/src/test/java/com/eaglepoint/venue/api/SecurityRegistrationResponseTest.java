package com.eaglepoint.venue.api;

import com.eaglepoint.venue.domain.UserAccount;
import com.eaglepoint.venue.service.AccountSecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SecurityRegistrationResponseTest {

    @Mock
    private AccountSecurityService accountSecurityService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityController controller = new SecurityController(accountSecurityService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void registerResponse_excludesPasswordHashAndLockoutInternals() throws Exception {
        UserAccount account = new UserAccount();
        account.setId(5L);
        account.setUsername("safe_user");
        account.setRole("SENIOR");
        account.setActive("Y");
        account.setPasswordHash("$2a$10$shouldNotAppear");
        account.setFailedAttempts(4);
        when(accountSecurityService.register(any())).thenReturn(account);

        mockMvc.perform(post("/api/security/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"safe_user\",\"password\":\"StrongPass1!\",\"role\":\"SENIOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.username").value("safe_user"))
                .andExpect(jsonPath("$.role").value("SENIOR"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.failedAttempts").doesNotExist())
                .andExpect(jsonPath("$.lockoutUntil").doesNotExist());
    }
}
