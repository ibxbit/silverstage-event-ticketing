package com.eaglepoint.venue.service;

import com.eaglepoint.venue.api.dto.CreateReservationRequest;
import com.eaglepoint.venue.domain.TicketReservation;
import com.eaglepoint.venue.domain.TicketType;
import com.eaglepoint.venue.mapper.EventMapper;
import com.eaglepoint.venue.mapper.OperationTraceMapper;
import com.eaglepoint.venue.mapper.TicketInventoryMapper;
import com.eaglepoint.venue.mapper.TicketPriceTierMapper;
import com.eaglepoint.venue.mapper.TicketReservationMapper;
import com.eaglepoint.venue.mapper.TicketTypeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketingServiceTest {

    @Mock
    private EventMapper eventMapper;
    @Mock
    private TicketTypeMapper ticketTypeMapper;
    @Mock
    private TicketPriceTierMapper ticketPriceTierMapper;
    @Mock
    private TicketInventoryMapper ticketInventoryMapper;
    @Mock
    private TicketReservationMapper ticketReservationMapper;
    @Mock
    private OperationTraceMapper operationTraceMapper;

    private TicketingService ticketingService;

    @BeforeEach
    void setUp() {
        ticketingService = new TicketingService(
                eventMapper,
                ticketTypeMapper,
                ticketPriceTierMapper,
                ticketInventoryMapper,
                ticketReservationMapper,
                operationTraceMapper,
                60,
                40
        );
    }

    @Test
    void reserveTickets_overridesForgedBuyerReferenceWithAuthenticatedActor() {
        TicketType ticketType = new TicketType();
        ticketType.setId(10L);
        ticketType.setBasePrice(new BigDecimal("30.00"));
        ticketType.setSaleStart(LocalDateTime.now().minusHours(1));
        ticketType.setSaleEnd(LocalDateTime.now().plusHours(1));
        when(ticketTypeMapper.findById(10L)).thenReturn(ticketType);
        when(ticketPriceTierMapper.findApplicablePrice(10L, 2)).thenReturn(null);
        when(ticketInventoryMapper.incrementSoldWithinQuota(10L, "ONLINE_PORTAL", 2)).thenReturn(1);

        CreateReservationRequest request = new CreateReservationRequest();
        request.setTicketTypeId(10L);
        request.setReservationCode("RES-100");
        request.setBuyerReference("forged_user");
        request.setChannel("ONLINE_PORTAL");
        request.setQuantity(2);

        ticketingService.reserveTickets("token_user", request);

        ArgumentCaptor<TicketReservation> reservationCaptor = ArgumentCaptor.forClass(TicketReservation.class);
        verify(ticketReservationMapper).insert(reservationCaptor.capture());
        assertEquals("token_user", reservationCaptor.getValue().getBuyerReference());
    }

    @Test
    void reserveTickets_withoutAuthenticatedActor_rejectsRequest() {
        CreateReservationRequest request = new CreateReservationRequest();
        request.setTicketTypeId(10L);
        request.setReservationCode("RES-101");
        request.setBuyerReference("forged_user");
        request.setChannel("ONLINE_PORTAL");
        request.setQuantity(1);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> ticketingService.reserveTickets("  ", request)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }
}
