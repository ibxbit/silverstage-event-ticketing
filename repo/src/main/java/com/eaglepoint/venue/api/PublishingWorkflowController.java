package com.eaglepoint.venue.api;

import com.eaglepoint.venue.api.dto.AppealDecisionRequest;
import com.eaglepoint.venue.api.dto.AppealRequest;
import com.eaglepoint.venue.api.dto.AuditLogResponse;
import com.eaglepoint.venue.api.dto.ContentResponse;
import com.eaglepoint.venue.api.dto.ContentVersionResponse;
import com.eaglepoint.venue.api.dto.CreateContentRequest;
import com.eaglepoint.venue.api.dto.DiffResponse;
import com.eaglepoint.venue.api.dto.UpdateContentRequest;
import com.eaglepoint.venue.common.SecurityConstants;
import com.eaglepoint.venue.domain.ContentAppeal;
import com.eaglepoint.venue.domain.UserAccount;
import com.eaglepoint.venue.service.PublishingWorkflowService;
import com.eaglepoint.venue.service.RequestAuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/publishing")
public class PublishingWorkflowController {

    private final PublishingWorkflowService publishingWorkflowService;
    private final RequestAuthorizationService requestAuthorizationService;

    public PublishingWorkflowController(
            PublishingWorkflowService publishingWorkflowService,
            RequestAuthorizationService requestAuthorizationService
    ) {
        this.publishingWorkflowService = publishingWorkflowService;
        this.requestAuthorizationService = requestAuthorizationService;
    }

    @PostMapping("/content")
    @ResponseStatus(HttpStatus.CREATED)
    public ContentResponse createDraft(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @Valid @RequestBody CreateContentRequest request
    ) {
        UserAccount user = requestAuthorizationService.requireAuthenticated(token);
        return publishingWorkflowService.createDraft(user.getUsername(), request);
    }

    @GetMapping("/content")
    public List<ContentResponse> list() {
        return publishingWorkflowService.listAll();
    }

    @PostMapping("/content/{contentId}/update")
    public ContentResponse updateDraft(
            @PathVariable Long contentId,
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @Valid @RequestBody UpdateContentRequest request
    ) {
        UserAccount user = requestAuthorizationService.requireAuthenticated(token);
        String owner = publishingWorkflowService.contentOwner(contentId);
        if (!owner.equalsIgnoreCase(user.getUsername()) && !isPrivilegedPublishingRole(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only the content owner or a privileged role can update the draft");
        }
        return publishingWorkflowService.updateDraft(contentId, user.getUsername(), request);
    }

    @PostMapping("/content/{contentId}/submit")
    public ContentResponse submit(
            @PathVariable Long contentId,
            @RequestHeader(value = "X-Auth-Token", required = false) String token
    ) {
        UserAccount user = requestAuthorizationService.requireAuthenticated(token);
        String owner = publishingWorkflowService.contentOwner(contentId);
        if (!owner.equalsIgnoreCase(user.getUsername()) && !isPrivilegedPublishingRole(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only the content owner or a privileged role can submit the draft");
        }
        return publishingWorkflowService.submit(contentId, user.getUsername());
    }

    @PostMapping("/content/{contentId}/review")
    public ContentResponse review(
            @PathVariable Long contentId,
            @RequestHeader(value = "X-Auth-Token", required = false) String token
    ) {
        UserAccount user = requestAuthorizationService.requireAnyRole(token, SecurityConstants.ROLE_MODERATOR, SecurityConstants.ROLE_ORG_ADMIN, SecurityConstants.ROLE_PLATFORM_ADMIN);
        return publishingWorkflowService.markReview(contentId, user.getUsername());
    }

    @PostMapping("/content/{contentId}/publish")
    public ContentResponse publish(
            @PathVariable Long contentId,
            @RequestHeader(value = "X-Auth-Token", required = false) String token
    ) {
        UserAccount user = requestAuthorizationService.requireAnyRole(token, SecurityConstants.ROLE_MODERATOR, SecurityConstants.ROLE_ORG_ADMIN, SecurityConstants.ROLE_PLATFORM_ADMIN);
        return publishingWorkflowService.publish(contentId, user.getUsername());
    }

    @PostMapping("/content/{contentId}/appeals")
    @ResponseStatus(HttpStatus.CREATED)
    public ContentAppeal requestAppeal(
            @PathVariable Long contentId,
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @Valid @RequestBody AppealRequest request
    ) {
        UserAccount user = requestAuthorizationService.requireAuthenticated(token);
        return publishingWorkflowService.requestAppeal(contentId, user.getUsername(), request);
    }

    @PostMapping("/appeals/{appealId}/decision")
    public ContentAppeal decideAppeal(
            @PathVariable Long appealId,
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @Valid @RequestBody AppealDecisionRequest request
    ) {
        UserAccount user = requestAuthorizationService.requireAnyRole(token, SecurityConstants.ROLE_MODERATOR, SecurityConstants.ROLE_ORG_ADMIN, SecurityConstants.ROLE_PLATFORM_ADMIN);
        return publishingWorkflowService.decideAppeal(appealId, user.getUsername(), request);
    }

    @PostMapping("/content/{contentId}/corrections")
    public ContentResponse applyCorrection(
            @PathVariable Long contentId,
            @RequestParam Long appealId,
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @Valid @RequestBody UpdateContentRequest request
    ) {
        UserAccount user = requestAuthorizationService.requireAuthenticated(token);
        String owner = publishingWorkflowService.contentOwner(contentId);
        if (!owner.equalsIgnoreCase(user.getUsername()) && !isPrivilegedPublishingRole(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "content owner or moderator/admin role required");
        }
        return publishingWorkflowService.applyPublishedCorrection(contentId, appealId, user.getUsername(), request);
    }

    @GetMapping("/content/{contentId}/versions")
    public List<ContentVersionResponse> versions(@PathVariable Long contentId) {
        return publishingWorkflowService.versions(contentId);
    }

    @GetMapping("/content/{contentId}/diff")
    public DiffResponse diff(
            @PathVariable Long contentId,
            @RequestParam Integer leftVersion,
            @RequestParam Integer rightVersion
    ) {
        return publishingWorkflowService.diff(contentId, leftVersion, rightVersion);
    }

    @PostMapping("/content/{contentId}/rollback")
    public ContentResponse rollback(
            @PathVariable Long contentId,
            @RequestParam Integer targetVersion,
            @RequestHeader(value = "X-Auth-Token", required = false) String token
    ) {
        UserAccount user = requestAuthorizationService.requireAnyRole(token, SecurityConstants.ROLE_MODERATOR, SecurityConstants.ROLE_ORG_ADMIN, SecurityConstants.ROLE_PLATFORM_ADMIN);
        return publishingWorkflowService.rollback(contentId, targetVersion, user.getUsername());
    }

    @GetMapping("/content/{contentId}/audit")
    public List<AuditLogResponse> audit(@PathVariable Long contentId) {
        return publishingWorkflowService.auditTrail(contentId);
    }

    private boolean isPrivilegedPublishingRole(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        return SecurityConstants.ROLE_MODERATOR.equals(normalized)
                || SecurityConstants.ROLE_ORG_ADMIN.equals(normalized)
                || SecurityConstants.ROLE_PLATFORM_ADMIN.equals(normalized);
    }

}
