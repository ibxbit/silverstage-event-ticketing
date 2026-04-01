package com.eaglepoint.venue.api;

import com.eaglepoint.venue.api.dto.CreateReservationRequest;
import com.eaglepoint.venue.api.dto.CreateTicketTypeRequest;
import com.eaglepoint.venue.api.dto.ReservationResponse;
import com.eaglepoint.venue.api.dto.TicketTypeResponse;
import com.eaglepoint.venue.common.SecurityConstants;
import com.eaglepoint.venue.domain.UserAccount;
import com.eaglepoint.venue.service.AccountSecurityService;
import com.eaglepoint.venue.service.TicketingService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api")
public class TicketingController {

    private final TicketingService ticketingService;
    private final AccountSecurityService accountSecurityService;

    public TicketingController(TicketingService ticketingService, AccountSecurityService accountSecurityService) {
        this.ticketingService = ticketingService;
        this.accountSecurityService = accountSecurityService;
    }

    @PostMapping("/events/{eventId}/ticket-types")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketTypeResponse createTicketType(
            @RequestHeader("X-Auth-Token") String token,
            @PathVariable Long eventId,
            @Valid @RequestBody CreateTicketTypeRequest request
    ) {
        UserAccount user = accountSecurityService.requireUserByToken(token);
        accountSecurityService.requireAnyRole(user.getRole(), SecurityConstants.ROLE_ORG_ADMIN, SecurityConstants.ROLE_PLATFORM_ADMIN);
        return ticketingService.createTicketType(user.getUsername(), eventId, request);
    }

    @GetMapping("/events/{eventId}/ticket-types")
    public List<TicketTypeResponse> listTicketTypes(@PathVariable Long eventId) {
        return ticketingService.listTicketTypes(eventId);
    }

    @PostMapping("/tickets/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserveTickets(
            @RequestHeader("X-Auth-Token") String token,
            @Valid @RequestBody CreateReservationRequest request
    ) {
        UserAccount user = accountSecurityService.requireUserByToken(token);
        return ticketingService.reserveTickets(user.getUsername(), request);
    }
}
