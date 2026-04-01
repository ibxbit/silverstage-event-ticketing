package com.eaglepoint.venue.service;

import com.eaglepoint.venue.common.SecurityConstants;
import com.eaglepoint.venue.api.dto.IdentityVerificationRequest;
import com.eaglepoint.venue.api.dto.LoginRequest;
import com.eaglepoint.venue.api.dto.LoginResponse;
import com.eaglepoint.venue.api.dto.RegisterAccountRequest;
import com.eaglepoint.venue.api.dto.VerificationReviewRequest;
import com.eaglepoint.venue.domain.AuthSession;
import com.eaglepoint.venue.domain.UserAccount;
import com.eaglepoint.venue.domain.UserIdentityVerification;
import com.eaglepoint.venue.mapper.AuthSessionMapper;
import com.eaglepoint.venue.mapper.UserAccountMapper;
import com.eaglepoint.venue.mapper.UserIdentityVerificationMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AccountSecurityService {
    private final UserAccountMapper userAccountMapper;
    private final AuthSessionMapper authSessionMapper;
    private final UserIdentityVerificationMapper userIdentityVerificationMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final byte[] aesKey;

    public AccountSecurityService(
            UserAccountMapper userAccountMapper,
            AuthSessionMapper authSessionMapper,
            UserIdentityVerificationMapper userIdentityVerificationMapper,
            @Value("${app.security.aes-key}") String key
    ) {
        this.userAccountMapper = userAccountMapper;
        this.authSessionMapper = authSessionMapper;
        this.userIdentityVerificationMapper = userIdentityVerificationMapper;
        this.aesKey = Arrays.copyOf(key.getBytes(StandardCharsets.UTF_8), 16);
    }

    @Transactional
    public UserAccount register(RegisterAccountRequest request) {
        String username = clean(request.getUsername());
        if (userAccountMapper.findByUsername(username) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "username already exists");
        }
        validatePassword(request.getPassword());
        UserAccount row = new UserAccount();
        row.setUsername(username);
        row.setPasswordHash(encoder.encode(request.getPassword()));
        row.setRole(resolveSelfRegistrationRole(request.getRole()));
        row.setFailedAttempts(0);
        row.setActive("Y");
        userAccountMapper.insert(row);
        return userAccountMapper.findById(row.getId());
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        UserAccount user = userAccountMapper.findByUsername(clean(request.getUsername()));
        if (user == null || !"Y".equals(user.getActive())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
        }

        if (user.getLockoutUntil() != null && user.getLockoutUntil().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "account locked until " + user.getLockoutUntil());
        }

        if (!encoder.matches(request.getPassword(), user.getPasswordHash())) {
            int failed = user.getFailedAttempts() == null ? 0 : user.getFailedAttempts();
            failed += 1;
            LocalDateTime lockout = failed >= 5 ? LocalDateTime.now().plusMinutes(15) : null;
            if (failed >= 5) {
                failed = 5;
            }
            userAccountMapper.updateLoginFailure(user.getId(), failed, lockout);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
        }

        userAccountMapper.resetLoginFailures(user.getId());

        AuthSession session = new AuthSession();
        session.setUserId(user.getId());
        session.setToken(UUID.randomUUID().toString().replace("-", ""));
        session.setExpiresAt(LocalDateTime.now().plusHours(12));
        authSessionMapper.insert(session);

        LoginResponse response = new LoginResponse();
        response.setToken(session.getToken());
        response.setUsername(user.getUsername());
        response.setRole(user.getRole());
        response.setVisibleMenus(visibleMenus(user.getRole()));
        return response;
    }

    public UserAccount requireUserByToken(String token) {
        AuthSession session = authSessionMapper.findValidByToken(clean(token), LocalDateTime.now());
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid or expired token");
        }
        UserAccount user = userAccountMapper.findById(session.getUserId());
        if (user == null || !"Y".equals(user.getActive())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid user session");
        }
        return user;
    }

    @Transactional
    public UserIdentityVerification submitVerification(String token, IdentityVerificationRequest request) {
        UserAccount user = requireUserByToken(token);
        UserIdentityVerification row = new UserIdentityVerification();
        row.setUserId(user.getId());
        row.setFullNameEncrypted(encrypt(clean(request.getFullName())));
        row.setIdType(clean(request.getIdType()).toUpperCase(Locale.ROOT));
        row.setIdNumberEncrypted(encrypt(clean(request.getIdNumber())));
        row.setIdNumberMasked(maskId(clean(request.getIdNumber())));
        row.setStatus("PENDING");
        userIdentityVerificationMapper.insert(row);
        return userIdentityVerificationMapper.findLatestByUserId(user.getId());
    }

    public List<UserIdentityVerification> pendingVerifications(String token) {
        UserAccount user = requireUserByToken(token);
        requireAnyRole(user.getRole(), SecurityConstants.ROLE_SERVICE_STAFF, SecurityConstants.ROLE_ORG_ADMIN, SecurityConstants.ROLE_PLATFORM_ADMIN);
        return userIdentityVerificationMapper.findPending();
    }

    @Transactional
    public UserIdentityVerification reviewVerification(String token, Long verificationId, VerificationReviewRequest request) {
        UserAccount user = requireUserByToken(token);
        requireAnyRole(user.getRole(), SecurityConstants.ROLE_SERVICE_STAFF, SecurityConstants.ROLE_ORG_ADMIN, SecurityConstants.ROLE_PLATFORM_ADMIN);
        String status = clean(request.getStatus()).toUpperCase(Locale.ROOT);
        if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be APPROVED or REJECTED");
        }
        userIdentityVerificationMapper.review(verificationId, status, user.getUsername(), clean(request.getNotes()));
        return userIdentityVerificationMapper.findById(verificationId);
    }

    public List<String> visibleMenusByToken(String token) {
        UserAccount user = requireUserByToken(token);
        return visibleMenus(user.getRole());
    }

    public void requireAnyRole(String role, String... allowed) {
        String normalized = normalizeRole(role);
        for (String allow : allowed) {
            if (normalizeRole(allow).equals(normalized)) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "insufficient role permissions");
    }

    private void validatePassword(String password) {
        String value = password == null ? "" : password;
        if (value.length() < 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password must be at least 10 characters");
        }
        boolean upper = value.matches(".*[A-Z].*");
        boolean lower = value.matches(".*[a-z].*");
        boolean digit = value.matches(".*\\d.*");
        boolean symbol = value.matches(".*[^A-Za-z0-9].*");
        if (!(upper && lower && digit && symbol)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password must include upper/lowercase letters, digits, and symbols");
        }
    }

    private List<String> visibleMenus(String role) {
        String r = normalizeRole(role);
        List<String> menus = new ArrayList<String>();
        menus.add("events");
        menus.add("tickets");
        menus.add("discovery");
        if (SecurityConstants.ROLE_SENIOR.equals(r) || SecurityConstants.ROLE_FAMILY_MEMBER.equals(r)) {
            menus.add("my-account");
        }
        if (SecurityConstants.ROLE_SERVICE_STAFF.equals(r) || SecurityConstants.ROLE_ORG_ADMIN.equals(r)
                || SecurityConstants.ROLE_PLATFORM_ADMIN.equals(r)) {
            menus.add("file-management");
            menus.add("verification-review");
        }
        if (SecurityConstants.ROLE_ORG_ADMIN.equals(r) || SecurityConstants.ROLE_PLATFORM_ADMIN.equals(r)
                || SecurityConstants.ROLE_MODERATOR.equals(r)) {
            menus.add("moderation");
            menus.add("publishing");
        }
        if (SecurityConstants.ROLE_ORG_ADMIN.equals(r) || SecurityConstants.ROLE_PLATFORM_ADMIN.equals(r)) {
            menus.add("payments");
            menus.add("reconciliation");
        }
        return menus;
    }

    private String encrypt(String plainText) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "encryption error");
        }
    }

    String encryptForTest(String plainText) {
        return encrypt(plainText);
    }

    String decryptForTest(String cipherText) {
        try {
            String[] parts = cipherText.split(":", 2);
            if (parts.length != 2) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid encrypted format");
            }
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] payload = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(payload), StandardCharsets.UTF_8);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unable to decrypt value");
        }
    }

    private String maskId(String raw) {
        String value = clean(raw);
        if (value.length() <= 4) {
            return "****" + value;
        }
        return "****" + value.substring(value.length() - 4);
    }

    private String normalizeRole(String role) {
        return clean(role).toUpperCase(Locale.ROOT);
    }

    private String resolveSelfRegistrationRole(String requestedRole) {
        String normalized = normalizeRole(requestedRole);
        if (normalized.isEmpty() || SecurityConstants.ROLE_SENIOR.equals(normalized)) {
            return SecurityConstants.ROLE_SENIOR;
        }
        if (SecurityConstants.ROLE_FAMILY_MEMBER.equals(normalized)) {
            return SecurityConstants.ROLE_FAMILY_MEMBER;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "self-registration role is not allowed");
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
