package com.eaglepoint.venue.service;

import com.eaglepoint.venue.api.dto.CreateReservationRequest;
import com.eaglepoint.venue.api.dto.CreateTicketTypeRequest;
import com.eaglepoint.venue.api.dto.ReservationResponse;
import com.eaglepoint.venue.api.dto.TicketTypeResponse;
import com.eaglepoint.venue.domain.Event;
import com.eaglepoint.venue.domain.OperationTrace;
import com.eaglepoint.venue.domain.TicketInventory;
import com.eaglepoint.venue.domain.TicketPriceTier;
import com.eaglepoint.venue.domain.TicketReservation;
import com.eaglepoint.venue.domain.TicketType;
import com.eaglepoint.venue.mapper.EventMapper;
import com.eaglepoint.venue.mapper.OperationTraceMapper;
import com.eaglepoint.venue.mapper.TicketInventoryMapper;
import com.eaglepoint.venue.mapper.TicketPriceTierMapper;
import com.eaglepoint.venue.mapper.TicketReservationMapper;
import com.eaglepoint.venue.mapper.TicketTypeMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TicketingService {
    private static final String CHANNEL_ONLINE = "ONLINE_PORTAL";
    private static final String CHANNEL_BOX_OFFICE = "BOX_OFFICE";
    private static final DateTimeFormatter WINDOW_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy h:mm a");

    private final EventMapper eventMapper;
    private final TicketTypeMapper ticketTypeMapper;
    private final TicketPriceTierMapper ticketPriceTierMapper;
    private final TicketInventoryMapper ticketInventoryMapper;
    private final TicketReservationMapper ticketReservationMapper;
    private final OperationTraceMapper operationTraceMapper;
    private final int defaultOnlineQuotaPercent;
    private final int defaultBoxOfficeQuotaPercent;

    public TicketingService(
            EventMapper eventMapper,
            TicketTypeMapper ticketTypeMapper,
            TicketPriceTierMapper ticketPriceTierMapper,
            TicketInventoryMapper ticketInventoryMapper,
            TicketReservationMapper ticketReservationMapper,
            OperationTraceMapper operationTraceMapper,
            @Value("${app.quota.online-percent:60}") int defaultOnlineQuotaPercent,
            @Value("${app.quota.box-office-percent:40}") int defaultBoxOfficeQuotaPercent
    ) {
        this.eventMapper = eventMapper;
        this.ticketTypeMapper = ticketTypeMapper;
        this.ticketPriceTierMapper = ticketPriceTierMapper;
        this.ticketInventoryMapper = ticketInventoryMapper;
        this.ticketReservationMapper = ticketReservationMapper;
        this.operationTraceMapper = operationTraceMapper;
        this.defaultOnlineQuotaPercent = defaultOnlineQuotaPercent;
        this.defaultBoxOfficeQuotaPercent = defaultBoxOfficeQuotaPercent;
    }

    @Transactional
    public TicketTypeResponse createTicketType(String actor, Long eventId, CreateTicketTypeRequest request) {
        Event event = eventMapper.findById(eventId);
        if (event == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "event not found");
        }
        validateTicketTypeRequest(request);

        TicketType ticketType = new TicketType();
        ticketType.setEventId(eventId);
        ticketType.setCode(request.getCode().trim().toUpperCase());
        ticketType.setName(request.getName().trim());
        ticketType.setBasePrice(request.getBasePrice());
        ticketType.setVisibilityScope(request.getVisibilityScope().trim().toUpperCase());
        ticketType.setSaleStart(request.getSaleStart());
        ticketType.setSaleEnd(request.getSaleEnd());
        ticketType.setTotalInventory(request.getTotalInventory());

        try {
            ticketTypeMapper.insert(ticketType);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ticket type code already exists for event");
        }

        int order = 1;
        for (CreateTicketTypeRequest.TierRuleRequest tierRule : request.getTierRules()) {
            TicketPriceTier priceTier = new TicketPriceTier();
            priceTier.setTicketTypeId(ticketType.getId());
            priceTier.setTierOrder(order);
            priceTier.setMinQuantity(tierRule.getMinQuantity());
            priceTier.setPrice(tierRule.getPrice());
            ticketPriceTierMapper.insert(priceTier);
            order++;
        }

        int onlineQuotaPercent = request.getOnlineQuotaPercent() == null ? defaultOnlineQuotaPercent : request.getOnlineQuotaPercent();
        int boxOfficeQuotaPercent = request.getBoxOfficeQuotaPercent() == null ? defaultBoxOfficeQuotaPercent : request.getBoxOfficeQuotaPercent();
        int onlineAllocated = (ticketType.getTotalInventory() * onlineQuotaPercent) / 100;
        int boxOfficeAllocated = ticketType.getTotalInventory() - onlineAllocated;

        TicketInventory onlineInventory = new TicketInventory();
        onlineInventory.setTicketTypeId(ticketType.getId());
        onlineInventory.setChannel(CHANNEL_ONLINE);
        onlineInventory.setAllocated(onlineAllocated);
        onlineInventory.setSold(0);
        ticketInventoryMapper.insert(onlineInventory);

        TicketInventory boxOfficeInventory = new TicketInventory();
        boxOfficeInventory.setTicketTypeId(ticketType.getId());
        boxOfficeInventory.setChannel(CHANNEL_BOX_OFFICE);
        boxOfficeInventory.setAllocated(boxOfficeAllocated);
        boxOfficeInventory.setSold(0);
        ticketInventoryMapper.insert(boxOfficeInventory);

        trace(clean(actor), "TICKET_TYPE_CREATED", "ticket_type", ticketType.getCode(), "eventId=" + eventId + ",inventory=" + ticketType.getTotalInventory());
        return toTicketTypeResponse(ticketType, ticketPriceTierMapper.findByTicketTypeId(ticketType.getId()), ticketInventoryMapper.findByTicketTypeId(ticketType.getId()));
    }

    public List<TicketTypeResponse> listTicketTypes(Long eventId) {
        List<TicketType> ticketTypes = ticketTypeMapper.findByEventId(eventId);
        List<TicketTypeResponse> responses = new ArrayList<TicketTypeResponse>();
        for (TicketType ticketType : ticketTypes) {
            List<TicketPriceTier> tiers = ticketPriceTierMapper.findByTicketTypeId(ticketType.getId());
            List<TicketInventory> inventory = ticketInventoryMapper.findByTicketTypeId(ticketType.getId());
            responses.add(toTicketTypeResponse(ticketType, tiers, inventory));
        }
        return responses;
    }

    @Transactional
    public ReservationResponse reserveTickets(String actor, CreateReservationRequest request) {
        String normalizedChannel = request.getChannel().trim().toUpperCase();
        validateChannel(normalizedChannel);

        TicketType ticketType = ticketTypeMapper.findById(request.getTicketTypeId());
        if (ticketType == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ticket type not found");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(ticketType.getSaleStart()) || now.isAfter(ticketType.getSaleEnd())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "sale window is closed for this ticket type");
        }

        BigDecimal unitPrice = ticketPriceTierMapper.findApplicablePrice(ticketType.getId(), request.getQuantity());
        if (unitPrice == null) {
            unitPrice = ticketType.getBasePrice();
        }

        TicketReservation reservation = new TicketReservation();
        reservation.setTicketTypeId(ticketType.getId());
        reservation.setReservationCode(request.getReservationCode().trim().toUpperCase());
        reservation.setBuyerReference(request.getBuyerReference().trim());
        reservation.setChannel(normalizedChannel);
        reservation.setQuantity(request.getQuantity());
        reservation.setUnitPrice(unitPrice);
        reservation.setTotalAmount(unitPrice.multiply(new BigDecimal(request.getQuantity())));
        reservation.setStatus("PENDING");

        try {
            ticketReservationMapper.insert(reservation);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "duplicate reservation code");
        }

        int updated = ticketInventoryMapper.incrementSoldWithinQuota(ticketType.getId(), normalizedChannel, request.getQuantity());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "requested quantity exceeds channel quota");
        }

        ticketReservationMapper.updateStatus(reservation.getId(), "CONFIRMED");
        reservation.setStatus("CONFIRMED");
        trace(clean(actor), "TICKET_RESERVED", "ticket_reservation", reservation.getReservationCode(), "ticketTypeId=" + reservation.getTicketTypeId() + ",quantity=" + reservation.getQuantity());

        ReservationResponse response = new ReservationResponse();
        response.setReservationId(reservation.getId());
        response.setTicketTypeId(reservation.getTicketTypeId());
        response.setReservationCode(reservation.getReservationCode());
        response.setChannel(reservation.getChannel());
        response.setQuantity(reservation.getQuantity());
        response.setUnitPrice(reservation.getUnitPrice());
        response.setTotalAmount(reservation.getTotalAmount());
        response.setStatus(reservation.getStatus());
        return response;
    }

    private void validateTicketTypeRequest(CreateTicketTypeRequest request) {
        if (request.getSaleEnd().isBefore(request.getSaleStart())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "saleEnd must not be earlier than saleStart");
        }
        int onlineQuotaPercent = request.getOnlineQuotaPercent() == null ? defaultOnlineQuotaPercent : request.getOnlineQuotaPercent();
        int boxOfficeQuotaPercent = request.getBoxOfficeQuotaPercent() == null ? defaultBoxOfficeQuotaPercent : request.getBoxOfficeQuotaPercent();
        if (onlineQuotaPercent + boxOfficeQuotaPercent != 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "channel quota percentages must total 100");
        }
    }

    private void validateChannel(String channel) {
        if (!CHANNEL_ONLINE.equals(channel) && !CHANNEL_BOX_OFFICE.equals(channel)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "channel must be ONLINE_PORTAL or BOX_OFFICE");
        }
    }

    private TicketTypeResponse toTicketTypeResponse(TicketType ticketType, List<TicketPriceTier> tiers, List<TicketInventory> inventoryRows) {
        TicketTypeResponse response = new TicketTypeResponse();
        response.setId(ticketType.getId());
        response.setCode(ticketType.getCode());
        response.setName(ticketType.getName());
        response.setBasePrice(ticketType.getBasePrice());
        response.setVisibilityScope(ticketType.getVisibilityScope());
        response.setSaleStart(ticketType.getSaleStart());
        response.setSaleEnd(ticketType.getSaleEnd());
        response.setSaleWindowLabel(ticketType.getSaleStart().format(WINDOW_FORMAT) + "-" + ticketType.getSaleEnd().format(WINDOW_FORMAT));
        response.setTotalInventory(ticketType.getTotalInventory());

        for (TicketPriceTier tier : tiers) {
            TicketTypeResponse.TierRule tierRule = new TicketTypeResponse.TierRule();
            tierRule.setMinQuantity(tier.getMinQuantity());
            tierRule.setPrice(tier.getPrice());
            response.getTierRules().add(tierRule);
        }

        for (TicketInventory inventory : inventoryRows) {
            TicketTypeResponse.ChannelQuota quota = new TicketTypeResponse.ChannelQuota();
            quota.setChannel(inventory.getChannel());
            quota.setAllocated(inventory.getAllocated());
            quota.setSold(inventory.getSold());
            response.getChannelQuotas().add(quota);
        }

        return response;
    }

    private void trace(String actor, String action, String entityType, String entityRef, String payload) {
        OperationTrace row = new OperationTrace();
        row.setTraceId(UUID.randomUUID().toString());
        row.setActor(actor);
        row.setAction(action);
        row.setEntityType(entityType);
        row.setEntityRef(entityRef);
        row.setPayload(payload);
        operationTraceMapper.insert(row);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
