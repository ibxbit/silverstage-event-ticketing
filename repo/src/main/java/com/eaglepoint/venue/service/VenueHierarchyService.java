package com.eaglepoint.venue.service;

import com.eaglepoint.venue.api.dto.CreateEventRequest;
import com.eaglepoint.venue.api.dto.VenueHierarchyResponse;
import com.eaglepoint.venue.domain.Event;
import com.eaglepoint.venue.domain.OperationTrace;
import com.eaglepoint.venue.domain.Season;
import com.eaglepoint.venue.domain.Seat;
import com.eaglepoint.venue.domain.SessionEntity;
import com.eaglepoint.venue.domain.Stand;
import com.eaglepoint.venue.domain.Zone;
import com.eaglepoint.venue.mapper.EventMapper;
import com.eaglepoint.venue.mapper.OperationTraceMapper;
import com.eaglepoint.venue.mapper.SeasonMapper;
import com.eaglepoint.venue.mapper.SeatMapper;
import com.eaglepoint.venue.mapper.SessionMapper;
import com.eaglepoint.venue.mapper.StandMapper;
import com.eaglepoint.venue.mapper.ZoneMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class VenueHierarchyService {

    private final EventMapper eventMapper;
    private final SeasonMapper seasonMapper;
    private final SessionMapper sessionMapper;
    private final StandMapper standMapper;
    private final ZoneMapper zoneMapper;
    private final SeatMapper seatMapper;
    private final OperationTraceMapper operationTraceMapper;

    public VenueHierarchyService(
            EventMapper eventMapper,
            SeasonMapper seasonMapper,
            SessionMapper sessionMapper,
            StandMapper standMapper,
            ZoneMapper zoneMapper,
            SeatMapper seatMapper,
            OperationTraceMapper operationTraceMapper
    ) {
        this.eventMapper = eventMapper;
        this.seasonMapper = seasonMapper;
        this.sessionMapper = sessionMapper;
        this.standMapper = standMapper;
        this.zoneMapper = zoneMapper;
        this.seatMapper = seatMapper;
        this.operationTraceMapper = operationTraceMapper;
    }

    public List<Event> listEvents() {
        return eventMapper.findAll();
    }

    public Event createEvent(String actor, CreateEventRequest request) {
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must not be earlier than startDate");
        }

        Event event = new Event();
        event.setCode(request.getCode());
        event.setName(request.getName());
        event.setStartDate(request.getStartDate());
        event.setEndDate(request.getEndDate());

        eventMapper.insert(event);
        trace(clean(actor), "EVENT_CREATED", "event", event.getCode(), "name=" + event.getName());
        return event;
    }

    public VenueHierarchyResponse getEventHierarchy(Long eventId) {
        Event event = eventMapper.findById(eventId);
        if (event == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "event not found");
        }

        VenueHierarchyResponse response = new VenueHierarchyResponse();
        response.setId(event.getId());
        response.setCode(event.getCode());
        response.setName(event.getName());
        response.setStartDate(event.getStartDate());
        response.setEndDate(event.getEndDate());

        List<Season> seasons = seasonMapper.findByEventId(eventId);
        for (Season season : seasons) {
            VenueHierarchyResponse.SeasonNode seasonNode = new VenueHierarchyResponse.SeasonNode();
            seasonNode.setId(season.getId());
            seasonNode.setName(season.getName());
            seasonNode.setStartDate(season.getStartDate());
            seasonNode.setEndDate(season.getEndDate());

            List<SessionEntity> sessions = sessionMapper.findBySeasonId(season.getId());
            for (SessionEntity session : sessions) {
                VenueHierarchyResponse.SessionNode sessionNode = new VenueHierarchyResponse.SessionNode();
                sessionNode.setId(session.getId());
                sessionNode.setTitle(session.getTitle());
                sessionNode.setStartTime(session.getStartTime());
                sessionNode.setEndTime(session.getEndTime());

                List<Stand> stands = standMapper.findBySessionId(session.getId());
                for (Stand stand : stands) {
                    VenueHierarchyResponse.StandNode standNode = new VenueHierarchyResponse.StandNode();
                    standNode.setId(stand.getId());
                    standNode.setCode(stand.getCode());
                    standNode.setName(stand.getName());

                    List<Zone> zones = zoneMapper.findByStandId(stand.getId());
                    for (Zone zone : zones) {
                        VenueHierarchyResponse.ZoneNode zoneNode = new VenueHierarchyResponse.ZoneNode();
                        zoneNode.setId(zone.getId());
                        zoneNode.setCode(zone.getCode());
                        zoneNode.setName(zone.getName());
                        zoneNode.setCapacity(zone.getCapacity());

                        List<Seat> seats = seatMapper.findByZoneId(zone.getId());
                        for (Seat seat : seats) {
                            VenueHierarchyResponse.SeatNode seatNode = new VenueHierarchyResponse.SeatNode();
                            seatNode.setId(seat.getId());
                            seatNode.setSeatNumber(seat.getSeatNumber());
                            seatNode.setStatus(seat.getStatus());
                            zoneNode.getSeats().add(seatNode);
                        }

                        standNode.getZones().add(zoneNode);
                    }

                    sessionNode.getStands().add(standNode);
                }

                seasonNode.getSessions().add(sessionNode);
            }

            response.getSeasons().add(seasonNode);
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
