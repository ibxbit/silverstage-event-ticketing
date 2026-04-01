package com.eaglepoint.venue.api;

import com.eaglepoint.venue.api.dto.IdentityVerificationRequest;
import com.eaglepoint.venue.api.dto.LoginRequest;
import com.eaglepoint.venue.api.dto.LoginResponse;
import com.eaglepoint.venue.api.dto.RegisterAccountRequest;
import com.eaglepoint.venue.api.dto.RegisterAccountResponse;
import com.eaglepoint.venue.api.dto.VerificationReviewRequest;
import com.eaglepoint.venue.domain.UserAccount;
import com.eaglepoint.venue.domain.UserIdentityVerification;
import com.eaglepoint.venue.service.AccountSecurityService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/security")
public class SecurityController {
    private final AccountSecurityService accountSecurityService;

    public SecurityController(AccountSecurityService accountSecurityService) {
        this.accountSecurityService = accountSecurityService;
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterAccountResponse register(@Valid @RequestBody RegisterAccountRequest request) {
        UserAccount account = accountSecurityService.register(request);
        RegisterAccountResponse response = new RegisterAccountResponse();
        response.setId(account.getId());
        response.setUsername(account.getUsername());
        response.setRole(account.getRole());
        response.setActive(account.getActive());
        return response;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return accountSecurityService.login(request);
    }

    @GetMapping("/menu")
    public Map<String, Object> menu(@RequestHeader("X-Auth-Token") String token) {
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("menus", accountSecurityService.visibleMenusByToken(token));
        return response;
    }

    @PostMapping("/verification")
    public UserIdentityVerification submitVerification(
            @RequestHeader("X-Auth-Token") String token,
            @Valid @RequestBody IdentityVerificationRequest request
    ) {
        return accountSecurityService.submitVerification(token, request);
    }

    @GetMapping("/verification/pending")
    public List<UserIdentityVerification> pending(@RequestHeader("X-Auth-Token") String token) {
        return accountSecurityService.pendingVerifications(token);
    }

    @PatchMapping("/verification/{verificationId}")
    public UserIdentityVerification review(
            @RequestHeader("X-Auth-Token") String token,
            @PathVariable Long verificationId,
            @Valid @RequestBody VerificationReviewRequest request
    ) {
        return accountSecurityService.reviewVerification(token, verificationId, request);
    }
}
