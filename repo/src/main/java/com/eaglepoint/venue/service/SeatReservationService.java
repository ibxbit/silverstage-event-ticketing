package com.eaglepoint.venue.service;

import com.eaglepoint.venue.api.dto.CreateSeatOrderRequest;
import com.eaglepoint.venue.api.dto.SeatMapResponse;
import com.eaglepoint.venue.api.dto.SeatOrderResponse;
import com.eaglepoint.venue.common.SecurityConstants;
import com.eaglepoint.venue.domain.Seat;
import com.eaglepoint.venue.domain.SessionEntity;
import com.eaglepoint.venue.domain.TicketInventory;
import com.eaglepoint.venue.domain.TicketOrder;
import com.eaglepoint.venue.domain.TicketOrderSeat;
import com.eaglepoint.venue.domain.TicketType;
import com.eaglepoint.venue.domain.Zone;
import com.eaglepoint.venue.mapper.SeatMapper;
import com.eaglepoint.venue.mapper.SessionMapper;
import com.eaglepoint.venue.mapper.TicketInventoryMapper;
import com.eaglepoint.venue.mapper.TicketOrderMapper;
import com.eaglepoint.venue.mapper.TicketOrderSeatMapper;
import com.eaglepoint.venue.mapper.TicketTypeMapper;
import com.eaglepoint.venue.mapper.ZoneMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

@Service
public class SeatReservationService {
    private static final String CHANNEL_ONLINE = "ONLINE_PORTAL";
    private static final String CHANNEL_BOX_OFFICE = "BOX_OFFICE";

    private final SessionMapper sessionMapper;
    private final ZoneMapper zoneMapper;
    private final SeatMapper seatMapper;
    private final TicketTypeMapper ticketTypeMapper;
    private final TicketInventoryMapper ticketInventoryMapper;
    private final TicketOrderMapper ticketOrderMapper;
    private final TicketOrderSeatMapper ticketOrderSeatMapper;

    public SeatReservationService(
            SessionMapper sessionMapper,
            ZoneMapper zoneMapper,
            SeatMapper seatMapper,
            TicketTypeMapper ticketTypeMapper,
            TicketInventoryMapper ticketInventoryMapper,
            TicketOrderMapper ticketOrderMapper,
            TicketOrderSeatMapper ticketOrderSeatMapper
    ) {
        this.sessionMapper = sessionMapper;
        this.zoneMapper = zoneMapper;
        this.seatMapper = seatMapper;
        this.ticketTypeMapper = ticketTypeMapper;
        this.ticketInventoryMapper = ticketInventoryMapper;
        this.ticketOrderMapper = ticketOrderMapper;
        this.ticketOrderSeatMapper = ticketOrderSeatMapper;
    }

    public SeatMapResponse getSeatMap(Long sessionId, Long ticketTypeId, String channel) {
        SessionEntity session = sessionMapper.findById(sessionId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found");
        }

        List<Zone> zones = zoneMapper.findBySessionId(sessionId);
        List<Seat> seats = seatMapper.findBySessionId(sessionId);

        Map<Long, SeatMapResponse.ZoneSeatMap> zoneMap = new HashMap<Long, SeatMapResponse.ZoneSeatMap>();
        for (Zone zone : zones) {
            SeatMapResponse.ZoneSeatMap zoneSeatMap = new SeatMapResponse.ZoneSeatMap();
            zoneSeatMap.setZoneId(zone.getId());
            zoneSeatMap.setZoneCode(zone.getCode());
            zoneSeatMap.setZoneName(zone.getName());
            zoneMap.put(zone.getId(), zoneSeatMap);
        }

        for (Seat seat : seats) {
            SeatMapResponse.ZoneSeatMap zoneSeatMap = zoneMap.get(seat.getZoneId());
            if (zoneSeatMap == null) {
                continue;
            }
            SeatMapResponse.SeatItem item = new SeatMapResponse.SeatItem();
            item.setSeatId(seat.getId());
            item.setSeatNumber(seat.getSeatNumber());
            item.setStatus(seat.getStatus());
            zoneSeatMap.getSeats().add(item);
        }

        SeatMapResponse response = new SeatMapResponse();
        response.setSessionId(sessionId);
        response.setSessionTitle(session.getTitle());
        response.setZones(new ArrayList<SeatMapResponse.ZoneSeatMap>(zoneMap.values()));

        if (ticketTypeId != null && channel != null && !channel.trim().isEmpty()) {
            String normalizedChannel = channel.trim().toUpperCase();
            validateChannel(normalizedChannel);
            TicketInventory inventory = ticketInventoryMapper.findByTicketTypeIdAndChannel(ticketTypeId, normalizedChannel);
            if (inventory != null) {
                int remaining = inventory.getAllocated() - inventory.getSold();
                if (remaining < 0) {
                    remaining = 0;
                }
                response.setRemainingQuota(remaining);
                response.setLowInventory(remaining <= 10);
                response.setQuotaReached(remaining == 0);
            }
        }

        return response;
    }

    @Transactional
    public SeatOrderResponse createSeatOrder(CreateSeatOrderRequest request) {
        validateChannel(request.getChannel());
        if (request.getSeatIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "at least one seat is required");
        }

        SessionEntity session = sessionMapper.findById(request.getSessionId());
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found");
        }

        List<Seat> sessionSeats = seatMapper.findBySessionId(request.getSessionId());
        Set<Long> sessionSeatIds = new HashSet<Long>();
        for (Seat seat : sessionSeats) {
            sessionSeatIds.add(seat.getId());
        }
        for (Long seatId : request.getSeatIds()) {
            if (!sessionSeatIds.contains(seatId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "one or more seats are not part of selected session");
            }
        }

        TicketType ticketType = ticketTypeMapper.findById(request.getTicketTypeId());
        if (ticketType == null || !request.getEventId().equals(ticketType.getEventId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ticket type not found for event");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(ticketType.getSaleStart()) || now.isAfter(ticketType.getSaleEnd())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "sale window is closed for this ticket type");
        }

        int inventoryUpdated = ticketInventoryMapper.incrementSoldWithinQuota(
                ticketType.getId(),
                request.getChannel().trim().toUpperCase(),
                request.getSeatIds().size()
        );
        if (inventoryUpdated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "channel quota reached for selected ticket type");
        }

        int seatUpdated = seatMapper.updateSeatStatusForIds(request.getSeatIds(), "AVAILABLE", "HELD");
        if (seatUpdated != request.getSeatIds().size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "one or more selected seats are no longer available");
        }

        TicketOrder order = new TicketOrder();
        order.setEventId(request.getEventId());
        order.setSessionId(request.getSessionId());
        order.setTicketTypeId(request.getTicketTypeId());
        order.setOrderCode(request.getOrderCode().trim().toUpperCase());
        order.setBuyerReference(request.getBuyerReference().trim());
        order.setChannel(request.getChannel().trim().toUpperCase());
        order.setQuantity(request.getSeatIds().size());
        order.setStatus("UNPAID");
        order.setHoldExpiresAt(now.plusMinutes(15));
        order.setCancelExpiresAt(now.plusMinutes(30));
        order.setInventoryReturned(0);

        try {
            ticketOrderMapper.insert(order);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "duplicate order code");
        }

        for (Long seatId : request.getSeatIds()) {
            TicketOrderSeat orderSeat = new TicketOrderSeat();
            orderSeat.setOrderId(order.getId());
            orderSeat.setSeatId(seatId);
            ticketOrderSeatMapper.insert(orderSeat);
        }

        return toOrderResponse(order);
    }

    @Transactional
    public SeatOrderResponse markOrderPaid(Long orderId, String actorUsername, String actorRole) {
        TicketOrder order = ticketOrderMapper.findById(orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found");
        }
        String normalizedRole = actorRole == null ? "" : actorRole.trim().toUpperCase(Locale.ROOT);
        boolean admin = SecurityConstants.ROLE_ORG_ADMIN.equals(normalizedRole)
                || SecurityConstants.ROLE_PLATFORM_ADMIN.equals(normalizedRole);
        String actor = actorUsername == null ? "" : actorUsername.trim();
        String buyer = order.getBuyerReference() == null ? "" : order.getBuyerReference().trim();
        if (!admin && !buyer.equalsIgnoreCase(actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "order ownership required");
        }
        if (!"UNPAID".equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "order is not payable");
        }
        if (LocalDateTime.now().isAfter(order.getHoldExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "seat hold expired before payment");
        }

        List<Long> seatIds = ticketOrderSeatMapper.findSeatIdsByOrderId(orderId);
        int updated = seatMapper.updateSeatStatusForIds(seatIds, "HELD", "RESERVED");
        if (updated != seatIds.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "some held seats were already released");
        }

        int paid = ticketOrderMapper.markPaid(orderId);
        if (paid == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "order could not be marked paid");
        }
        order.setStatus("PAID");
        return toOrderResponse(order);
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void releaseExpiredSeatHolds() {
        LocalDateTime now = LocalDateTime.now();
        List<TicketOrder> expiredHolds = ticketOrderMapper.findExpiredSeatHolds(now);
        for (TicketOrder order : expiredHolds) {
            releaseHoldForOrder(order.getId());
        }
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void cancelOverdueUnpaidOrders() {
        LocalDateTime now = LocalDateTime.now();
        List<TicketOrder> overdueOrders = ticketOrderMapper.findAutoCancellableOrders(now);
        for (TicketOrder order : overdueOrders) {
            cancelOrderAndReturnInventory(order);
        }
    }

    private void releaseHoldForOrder(Long orderId) {
        TicketOrder order = ticketOrderMapper.findById(orderId);
        if (order == null || !"UNPAID".equals(order.getStatus())) {
            return;
        }
        List<Long> seatIds = ticketOrderSeatMapper.findSeatIdsByOrderId(orderId);
        if (!seatIds.isEmpty()) {
            seatMapper.updateSeatStatusForIds(seatIds, "HELD", "AVAILABLE");
        }
        ticketOrderMapper.markHoldReleased(orderId);
    }

    private void cancelOrderAndReturnInventory(TicketOrder order) {
        TicketOrder current = ticketOrderMapper.findById(order.getId());
        if (current == null || "PAID".equals(current.getStatus()) || current.getInventoryReturned() == 1) {
            return;
        }

        List<Long> seatIds = ticketOrderSeatMapper.findSeatIdsByOrderId(current.getId());
        if (!seatIds.isEmpty()) {
            seatMapper.updateSeatStatusForIds(seatIds, "HELD", "AVAILABLE");
        }

        int updated = ticketOrderMapper.markCancelledAndInventoryReturned(current.getId());
        if (updated > 0) {
            ticketInventoryMapper.decrementSold(current.getTicketTypeId(), current.getChannel(), current.getQuantity());
        }
    }

    private void validateChannel(String channelRaw) {
        String channel = channelRaw == null ? "" : channelRaw.trim().toUpperCase();
        if (!CHANNEL_ONLINE.equals(channel) && !CHANNEL_BOX_OFFICE.equals(channel)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "channel must be ONLINE_PORTAL or BOX_OFFICE");
        }
    }

    private SeatOrderResponse toOrderResponse(TicketOrder order) {
        SeatOrderResponse response = new SeatOrderResponse();
        response.setOrderId(order.getId());
        response.setOrderCode(order.getOrderCode());
        response.setStatus(order.getStatus());
        response.setQuantity(order.getQuantity());
        response.setHoldExpiresAt(order.getHoldExpiresAt());
        response.setCancelExpiresAt(order.getCancelExpiresAt());
        return response;
    }
}
