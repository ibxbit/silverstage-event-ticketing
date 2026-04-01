package com.eaglepoint.venue.api;

import com.eaglepoint.venue.api.dto.CreateEventRequest;
import com.eaglepoint.venue.api.dto.VenueHierarchyResponse;
import com.eaglepoint.venue.common.SecurityConstants;
import com.eaglepoint.venue.domain.Event;
import com.eaglepoint.venue.domain.UserAccount;
import com.eaglepoint.venue.service.AccountSecurityService;
import com.eaglepoint.venue.service.VenueHierarchyService;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class VenueController {

    private final VenueHierarchyService venueHierarchyService;
    private final AccountSecurityService accountSecurityService;

    public VenueController(VenueHierarchyService venueHierarchyService, AccountSecurityService accountSecurityService) {
        this.venueHierarchyService = venueHierarchyService;
        this.accountSecurityService = accountSecurityService;
    }

    @GetMapping
    public List<Event> getEvents() {
        return venueHierarchyService.listEvents();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Event createEvent(
            @RequestHeader("X-Auth-Token") String token,
            @Valid @RequestBody CreateEventRequest request
    ) {
        UserAccount user = accountSecurityService.requireUserByToken(token);
        accountSecurityService.requireAnyRole(user.getRole(), SecurityConstants.ROLE_ORG_ADMIN, SecurityConstants.ROLE_PLATFORM_ADMIN);
        return venueHierarchyService.createEvent(user.getUsername(), request);
    }

    @GetMapping("/{eventId}/hierarchy")
    public VenueHierarchyResponse getEventHierarchy(@PathVariable Long eventId) {
        return venueHierarchyService.getEventHierarchy(eventId);
    }
}
