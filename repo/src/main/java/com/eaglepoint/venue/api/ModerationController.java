package com.eaglepoint.venue.api;

import com.eaglepoint.venue.api.dto.ModerationDecisionRequest;
import com.eaglepoint.venue.api.dto.NotificationItemResponse;
import com.eaglepoint.venue.api.dto.PenaltyStatusResponse;
import com.eaglepoint.venue.api.dto.ReportResponse;
import com.eaglepoint.venue.common.SecurityConstants;
import com.eaglepoint.venue.domain.UserAccount;
import com.eaglepoint.venue.service.ModerationService;
import com.eaglepoint.venue.service.RequestAuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/moderation")
public class ModerationController {

    private final ModerationService moderationService;
    private final RequestAuthorizationService requestAuthorizationService;

    public ModerationController(ModerationService moderationService, RequestAuthorizationService requestAuthorizationService) {
        this.moderationService = moderationService;
        this.requestAuthorizationService = requestAuthorizationService;
    }

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse submitReport(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @RequestParam(required = false) String reporterUser,
            @RequestParam String reportedUser,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) String contentRef,
            @RequestParam String reason,
            @RequestParam(value = "evidence", required = false) MultipartFile[] evidence
    ) {
        UserAccount user = requestAuthorizationService.requireAuthenticated(token);
        String reporter = reporterUser == null || reporterUser.trim().isEmpty() ? user.getUsername() : reporterUser.trim();
        if (!reporter.equalsIgnoreCase(user.getUsername())) {
            requestAuthorizationService.requireAnyRole(token, SecurityConstants.ROLE_MODERATOR, SecurityConstants.ROLE_ORG_ADMIN, SecurityConstants.ROLE_PLATFORM_ADMIN);
        }
        return moderationService.submitReport(reporter, reportedUser, contentType, contentRef, reason, evidence);
    }

    @GetMapping("/reports")
    public List<ReportResponse> openReports(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        UserAccount user = requestAuthorizationService.requireAnyRole(token, SecurityConstants.ROLE_MODERATOR, SecurityConstants.ROLE_ORG_ADMIN, SecurityConstants.ROLE_PLATFORM_ADMIN);
        return moderationService.listOpenReports(user.getRole());
    }

    @PostMapping("/reports/{reportId}/decision")
    public ReportResponse decideReport(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @PathVariable Long reportId,
            @Valid @RequestBody ModerationDecisionRequest request
    ) {
        UserAccount user = requestAuthorizationService.requireAnyRole(token, SecurityConstants.ROLE_MODERATOR, SecurityConstants.ROLE_ORG_ADMIN, SecurityConstants.ROLE_PLATFORM_ADMIN);
        return moderationService.resolveReport(user.getRole(), user.getUsername(), reportId, request);
    }

    @GetMapping("/users/{username}/penalties")
    public List<PenaltyStatusResponse> penalties(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @PathVariable String username
    ) {
        requestAuthorizationService.requireSelfOrAnyRole(token, username, SecurityConstants.ROLE_MODERATOR, SecurityConstants.ROLE_ORG_ADMIN, SecurityConstants.ROLE_PLATFORM_ADMIN);
        return moderationService.penaltyStatus(username);
    }

    @GetMapping("/users/{username}/notifications")
    public List<NotificationItemResponse> notifications(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @PathVariable String username
    ) {
        requestAuthorizationService.requireSelfOrAnyRole(token, username, SecurityConstants.ROLE_MODERATOR, SecurityConstants.ROLE_ORG_ADMIN, SecurityConstants.ROLE_PLATFORM_ADMIN);
        return moderationService.notifications(username);
    }

    @PatchMapping("/notifications/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @PathVariable Long notificationId
    ) {
        UserAccount user = requestAuthorizationService.requireAuthenticated(token);
        moderationService.markNotificationRead(user.getUsername(), user.getRole(), notificationId);
    }
}
