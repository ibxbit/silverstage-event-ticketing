package com.eaglepoint.venue.service;

import com.eaglepoint.venue.api.dto.LoginRequest;
import com.eaglepoint.venue.api.dto.RegisterAccountRequest;
import com.eaglepoint.venue.common.SecurityConstants;
import com.eaglepoint.venue.domain.UserAccount;
import com.eaglepoint.venue.mapper.AuthSessionMapper;
import com.eaglepoint.venue.mapper.UserAccountMapper;
import com.eaglepoint.venue.mapper.UserIdentityVerificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class AccountSecurityServiceTest {

    @Mock
    private UserAccountMapper userAccountMapper;
    @Mock
    private AuthSessionMapper authSessionMapper;
    @Mock
    private UserIdentityVerificationMapper userIdentityVerificationMapper;

    private AccountSecurityService accountSecurityService;

    @BeforeEach
    void setUp() {
        accountSecurityService = new AccountSecurityService(
                userAccountMapper,
                authSessionMapper,
                userIdentityVerificationMapper,
                "UnitTestAESKey01"
        );
    }

    @Test
    void aesGcmEncryption_isReversibleAndUsesRandomIv() {
        String raw = "Resident ID 123456789";

        String encryptedA = accountSecurityService.encryptForTest(raw);
        String encryptedB = accountSecurityService.encryptForTest(raw);

        assertNotEquals(encryptedA, encryptedB, "cipher text should differ due to random IV");
        assertTrue(encryptedA.contains(":"));
        assertTrue(encryptedB.contains(":"));

        assertEquals(raw, accountSecurityService.decryptForTest(encryptedA));
        assertEquals(raw, accountSecurityService.decryptForTest(encryptedB));
    }

    @Test
    void register_rejectsWeakPasswords() {
        when(userAccountMapper.findByUsername(anyString())).thenReturn(null);

        assertPasswordRejected("Short1!");
        assertPasswordRejected("alllowercase1!");
        assertPasswordRejected("ALLUPPERCASE1!");
        assertPasswordRejected("NoDigitsHere!");
        assertPasswordRejected("NoSymbols1234");

        verify(userAccountMapper, never()).insert(any(UserAccount.class));
    }

    @Test
    void login_locksAccountAfterFifthFailedAttemptForFifteenMinutes() {
        UserAccount user = new UserAccount();
        user.setId(8L);
        user.setUsername("locked_user");
        user.setRole(SecurityConstants.ROLE_SENIOR);
        user.setActive("Y");
        user.setFailedAttempts(4);
        user.setPasswordHash(new BCryptPasswordEncoder().encode("ValidPass1!"));

        when(userAccountMapper.findByUsername("locked_user")).thenReturn(user);

        LoginRequest request = new LoginRequest();
        request.setUsername("locked_user");
        request.setPassword("WrongPass1!");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> accountSecurityService.login(request));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());

        ArgumentCaptor<Integer> attemptsCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<LocalDateTime> lockoutCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userAccountMapper).updateLoginFailure(eq(8L), attemptsCaptor.capture(), lockoutCaptor.capture());

        assertEquals(Integer.valueOf(5), attemptsCaptor.getValue());
        LocalDateTime lockout = lockoutCaptor.getValue();
        assertTrue(lockout.isAfter(LocalDateTime.now().plusMinutes(14)));
        assertTrue(lockout.isBefore(LocalDateTime.now().plusMinutes(16)));
    }

    @Test
    void requireAnyRole_allowsAuthorizedRoleAndRejectsUnauthorizedRole() {
        accountSecurityService.requireAnyRole(SecurityConstants.ROLE_ORG_ADMIN, SecurityConstants.ROLE_ORG_ADMIN, SecurityConstants.ROLE_PLATFORM_ADMIN);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> accountSecurityService.requireAnyRole(SecurityConstants.ROLE_SENIOR, SecurityConstants.ROLE_ORG_ADMIN, SecurityConstants.ROLE_PLATFORM_ADMIN)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void register_defaultsToSeniorWhenRoleMissing() {
        when(userAccountMapper.findByUsername("resident_a")).thenReturn(null);
        doAnswer(invocation -> {
            UserAccount row = invocation.getArgument(0);
            row.setId(100L);
            return 1;
        }).when(userAccountMapper).insert(any(UserAccount.class));

        RegisterAccountRequest request = new RegisterAccountRequest();
        request.setUsername("resident_a");
        request.setPassword("ValidPass1!");

        accountSecurityService.register(request);

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountMapper).insert(captor.capture());
        assertEquals(SecurityConstants.ROLE_SENIOR, captor.getValue().getRole());
    }

    @Test
    void register_allowsFamilyMemberRole() {
        when(userAccountMapper.findByUsername("family_a")).thenReturn(null);
        doAnswer(invocation -> {
            UserAccount row = invocation.getArgument(0);
            row.setId(101L);
            return 1;
        }).when(userAccountMapper).insert(any(UserAccount.class));

        RegisterAccountRequest request = new RegisterAccountRequest();
        request.setUsername("family_a");
        request.setPassword("ValidPass1!");
        request.setRole(SecurityConstants.ROLE_FAMILY_MEMBER);

        accountSecurityService.register(request);

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountMapper).insert(captor.capture());
        assertEquals(SecurityConstants.ROLE_FAMILY_MEMBER, captor.getValue().getRole());
    }

    @Test
    void register_rejectsPrivilegedSelfRegistrationRole() {
        when(userAccountMapper.findByUsername("attacker")).thenReturn(null);

        RegisterAccountRequest request = new RegisterAccountRequest();
        request.setUsername("attacker");
        request.setPassword("ValidPass1!");
        request.setRole(SecurityConstants.ROLE_PLATFORM_ADMIN);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> accountSecurityService.register(request));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(userAccountMapper, never()).insert(any(UserAccount.class));
    }

    private void assertPasswordRejected(String password) {
        RegisterAccountRequest request = new RegisterAccountRequest();
        request.setUsername("user_" + password.replaceAll("[^A-Za-z0-9]", "x"));
        request.setPassword(password);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> accountSecurityService.register(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }
}
