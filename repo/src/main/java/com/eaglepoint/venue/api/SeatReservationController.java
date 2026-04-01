package com.eaglepoint.venue.api;

import com.eaglepoint.venue.api.dto.CreateSeatOrderRequest;
import com.eaglepoint.venue.api.dto.SeatMapResponse;
import com.eaglepoint.venue.api.dto.SeatOrderResponse;
import com.eaglepoint.venue.common.SecurityConstants;
import com.eaglepoint.venue.domain.UserAccount;
import com.eaglepoint.venue.service.RequestAuthorizationService;
import com.eaglepoint.venue.service.SeatReservationService;
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

import javax.validation.Valid;

@RestController
@RequestMapping("/api")
public class SeatReservationController {

    private final SeatReservationService seatReservationService;
    private final RequestAuthorizationService requestAuthorizationService;

    public SeatReservationController(
            SeatReservationService seatReservationService,
            RequestAuthorizationService requestAuthorizationService
    ) {
        this.seatReservationService = seatReservationService;
        this.requestAuthorizationService = requestAuthorizationService;
    }

    @GetMapping("/sessions/{sessionId}/seat-map")
    public SeatMapResponse getSeatMap(
            @PathVariable Long sessionId,
            @RequestParam(required = false) Long ticketTypeId,
            @RequestParam(required = false) String channel
    ) {
        return seatReservationService.getSeatMap(sessionId, ticketTypeId, channel);
    }

    @PostMapping("/seat-orders")
    @ResponseStatus(HttpStatus.CREATED)
    public SeatOrderResponse createSeatOrder(
            @RequestHeader("X-Auth-Token") String token,
            @Valid @RequestBody CreateSeatOrderRequest request
    ) {
        UserAccount user = requestAuthorizationService.requireAnyRole(
                token,
                SecurityConstants.ROLE_SENIOR,
                SecurityConstants.ROLE_FAMILY_MEMBER,
                SecurityConstants.ROLE_SERVICE_STAFF,
                SecurityConstants.ROLE_ORG_ADMIN,
                SecurityConstants.ROLE_PLATFORM_ADMIN
        );
        request.setBuyerReference(user.getUsername());
        return seatReservationService.createSeatOrder(request);
    }

    @PostMapping("/seat-orders/{orderId}/pay")
    public SeatOrderResponse markSeatOrderPaid(
            @RequestHeader("X-Auth-Token") String token,
            @PathVariable Long orderId
    ) {
        UserAccount user = requestAuthorizationService.requireAnyRole(
                token,
                SecurityConstants.ROLE_SENIOR,
                SecurityConstants.ROLE_FAMILY_MEMBER,
                SecurityConstants.ROLE_SERVICE_STAFF,
                SecurityConstants.ROLE_ORG_ADMIN,
                SecurityConstants.ROLE_PLATFORM_ADMIN
        );
        return seatReservationService.markOrderPaid(orderId, user.getUsername(), user.getRole());
    }
}
