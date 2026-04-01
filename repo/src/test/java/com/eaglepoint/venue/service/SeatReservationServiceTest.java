package com.eaglepoint.venue.service;

import com.eaglepoint.venue.api.dto.CreateSeatOrderRequest;
import com.eaglepoint.venue.domain.Seat;
import com.eaglepoint.venue.domain.SessionEntity;
import com.eaglepoint.venue.domain.TicketOrder;
import com.eaglepoint.venue.domain.TicketType;
import com.eaglepoint.venue.mapper.SeatMapper;
import com.eaglepoint.venue.mapper.SessionMapper;
import com.eaglepoint.venue.mapper.TicketInventoryMapper;
import com.eaglepoint.venue.mapper.TicketOrderMapper;
import com.eaglepoint.venue.mapper.TicketOrderSeatMapper;
import com.eaglepoint.venue.mapper.TicketTypeMapper;
import com.eaglepoint.venue.mapper.ZoneMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeatReservationServiceTest {

    @Mock
    private SessionMapper sessionMapper;
    @Mock
    private ZoneMapper zoneMapper;
    @Mock
    private SeatMapper seatMapper;
    @Mock
    private TicketTypeMapper ticketTypeMapper;
    @Mock
    private TicketInventoryMapper ticketInventoryMapper;
    @Mock
    private TicketOrderMapper ticketOrderMapper;
    @Mock
    private TicketOrderSeatMapper ticketOrderSeatMapper;

    @InjectMocks
    private SeatReservationService seatReservationService;

    @Test
    void createSeatOrder_preventsDoubleBookingUnderConcurrentLoad() throws Exception {
        SessionEntity session = new SessionEntity();
        session.setId(100L);
        session.setTitle("Concurrent Session");
        when(sessionMapper.findById(100L)).thenReturn(session);

        Seat seat = new Seat();
        seat.setId(500L);
        seat.setZoneId(10L);
        when(seatMapper.findBySessionId(100L)).thenReturn(Collections.singletonList(seat));

        TicketType ticketType = new TicketType();
        ticketType.setId(200L);
        ticketType.setEventId(1L);
        ticketType.setSaleStart(LocalDateTime.now().minusMinutes(5));
        ticketType.setSaleEnd(LocalDateTime.now().plusMinutes(5));
        when(ticketTypeMapper.findById(200L)).thenReturn(ticketType);

        when(ticketInventoryMapper.incrementSoldWithinQuota(eq(200L), eq("ONLINE_PORTAL"), eq(1))).thenReturn(1);

        AtomicBoolean firstSeatLock = new AtomicBoolean(true);
        when(seatMapper.updateSeatStatusForIds(eq(Collections.singletonList(500L)), eq("AVAILABLE"), eq("HELD")))
                .thenAnswer(invocation -> firstSeatLock.compareAndSet(true, false) ? 1 : 0);

        AtomicLong orderId = new AtomicLong(1L);
        doAnswer(invocation -> {
            TicketOrder order = invocation.getArgument(0);
            order.setId(orderId.getAndIncrement());
            return 1;
        }).when(ticketOrderMapper).insert(any(TicketOrder.class));

        CreateSeatOrderRequest request = new CreateSeatOrderRequest();
        request.setEventId(1L);
        request.setSessionId(100L);
        request.setTicketTypeId(200L);
        request.setOrderCode("ORD-CONCURRENT");
        request.setBuyerReference("user");
        request.setChannel("ONLINE_PORTAL");
        request.setSeatIds(Collections.singletonList(500L));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Boolean> result1 = executor.submit(() -> reserveWithLatch(request, ready, start));
        Future<Boolean> result2 = executor.submit(() -> reserveWithLatch(request, ready, start));

        assertTrue(ready.await(2, TimeUnit.SECONDS));
        start.countDown();

        int successCount = 0;
        if (result1.get()) {
            successCount++;
        }
        if (result2.get()) {
            successCount++;
        }

        executor.shutdownNow();
        assertEquals(1, successCount, "exactly one reservation should succeed for the same seat");
    }

    @Test
    void releaseExpiredSeatHolds_releasesHeldSeatsAndMarksHoldReleased() {
        TicketOrder expired = new TicketOrder();
        expired.setId(91L);
        expired.setStatus("UNPAID");

        when(ticketOrderMapper.findExpiredSeatHolds(any(LocalDateTime.class))).thenReturn(Collections.singletonList(expired));
        when(ticketOrderMapper.findById(91L)).thenReturn(expired);
        when(ticketOrderSeatMapper.findSeatIdsByOrderId(91L)).thenReturn(Arrays.asList(10L, 11L));

        seatReservationService.releaseExpiredSeatHolds();

        verify(seatMapper).updateSeatStatusForIds(eq(Arrays.asList(10L, 11L)), eq("HELD"), eq("AVAILABLE"));
        verify(ticketOrderMapper).markHoldReleased(91L);
    }

    @Test
    void cancelOverdueUnpaidOrders_returnsInventoryAndReleasesHeldSeats() {
        TicketOrder overdue = new TicketOrder();
        overdue.setId(200L);

        TicketOrder current = new TicketOrder();
        current.setId(200L);
        current.setStatus("UNPAID");
        current.setInventoryReturned(0);
        current.setTicketTypeId(77L);
        current.setChannel("ONLINE_PORTAL");
        current.setQuantity(2);

        when(ticketOrderMapper.findAutoCancellableOrders(any(LocalDateTime.class))).thenReturn(Collections.singletonList(overdue));
        when(ticketOrderMapper.findById(200L)).thenReturn(current);
        when(ticketOrderSeatMapper.findSeatIdsByOrderId(200L)).thenReturn(Arrays.asList(33L, 34L));
        when(ticketOrderMapper.markCancelledAndInventoryReturned(200L)).thenReturn(1);

        seatReservationService.cancelOverdueUnpaidOrders();

        verify(seatMapper).updateSeatStatusForIds(eq(Arrays.asList(33L, 34L)), eq("HELD"), eq("AVAILABLE"));
        verify(ticketInventoryMapper).decrementSold(77L, "ONLINE_PORTAL", 2);
    }

    @Test
    void cancelOverdueUnpaidOrders_skipsAlreadyReturnedInventory() {
        TicketOrder overdue = new TicketOrder();
        overdue.setId(201L);

        TicketOrder current = new TicketOrder();
        current.setId(201L);
        current.setStatus("UNPAID");
        current.setInventoryReturned(1);

        when(ticketOrderMapper.findAutoCancellableOrders(any(LocalDateTime.class))).thenReturn(Collections.singletonList(overdue));
        when(ticketOrderMapper.findById(201L)).thenReturn(current);

        seatReservationService.cancelOverdueUnpaidOrders();

        verify(ticketOrderSeatMapper, never()).findSeatIdsByOrderId(201L);
        verify(ticketInventoryMapper, never()).decrementSold(anyLong(), any(), any());
    }

    @Test
    void markOrderPaid_forbiddenWhenNonOwnerAndNotAdmin() {
        TicketOrder order = new TicketOrder();
        order.setId(301L);
        order.setBuyerReference("buyer_a");
        order.setStatus("UNPAID");
        order.setHoldExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(ticketOrderMapper.findById(301L)).thenReturn(order);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> seatReservationService.markOrderPaid(301L, "other_user", "SENIOR")
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(ticketOrderSeatMapper, never()).findSeatIdsByOrderId(301L);
        verify(ticketOrderMapper, never()).markPaid(301L);
    }

    @Test
    void markOrderPaid_allowsOrgAdminForAnotherUsersOrder() {
        TicketOrder order = new TicketOrder();
        order.setId(302L);
        order.setBuyerReference("buyer_a");
        order.setStatus("UNPAID");
        order.setHoldExpiresAt(LocalDateTime.now().plusMinutes(5));
        order.setOrderCode("ORD-302");
        order.setQuantity(1);
        when(ticketOrderMapper.findById(302L)).thenReturn(order);
        when(ticketOrderSeatMapper.findSeatIdsByOrderId(302L)).thenReturn(Collections.singletonList(500L));
        when(seatMapper.updateSeatStatusForIds(eq(Collections.singletonList(500L)), eq("HELD"), eq("RESERVED"))).thenReturn(1);
        when(ticketOrderMapper.markPaid(302L)).thenReturn(1);

        seatReservationService.markOrderPaid(302L, "admin_user", "ORG_ADMIN");

        verify(ticketOrderMapper).markPaid(302L);
    }

    private Boolean reserveWithLatch(CreateSeatOrderRequest request, CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            start.await(2, TimeUnit.SECONDS);
            seatReservationService.createSeatOrder(request);
            return true;
        } catch (ResponseStatusException ex) {
            return false;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
