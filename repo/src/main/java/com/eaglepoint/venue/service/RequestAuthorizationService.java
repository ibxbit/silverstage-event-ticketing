package com.eaglepoint.venue.service;

import com.eaglepoint.venue.domain.UserAccount;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class RequestAuthorizationService {
    private final AccountSecurityService accountSecurityService;

    public RequestAuthorizationService(AccountSecurityService accountSecurityService) {
        this.accountSecurityService = accountSecurityService;
    }

    public UserAccount requireAuthenticated(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-Auth-Token is required");
        }
        return accountSecurityService.requireUserByToken(token);
    }

    public UserAccount requireAnyRole(String token, String... roles) {
        UserAccount user = requireAuthenticated(token);
        accountSecurityService.requireAnyRole(user.getRole(), roles);
        return user;
    }

    public UserAccount requireSelfOrAnyRole(String token, String requestedUsername, String... roles) {
        UserAccount user = requireAuthenticated(token);
        if (clean(user.getUsername()).equals(clean(requestedUsername))) {
            return user;
        }
        accountSecurityService.requireAnyRole(user.getRole(), roles);
        return user;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
