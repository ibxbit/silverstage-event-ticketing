package com.eaglepoint.venue.service;

import com.eaglepoint.venue.api.dto.VenueHierarchyResponse;
import com.eaglepoint.venue.domain.Event;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VenueHierarchyServiceTest {

    @Mock
    private EventMapper eventMapper;
    @Mock
    private SeasonMapper seasonMapper;
    @Mock
    private SessionMapper sessionMapper;
    @Mock
    private StandMapper standMapper;
    @Mock
    private ZoneMapper zoneMapper;
    @Mock
    private SeatMapper seatMapper;
    @Mock
    private OperationTraceMapper operationTraceMapper;

    private VenueHierarchyService venueHierarchyService;

    @BeforeEach
    void setUp() {
        venueHierarchyService = new VenueHierarchyService(
                eventMapper,
                seasonMapper,
                sessionMapper,
                standMapper,
                zoneMapper,
                seatMapper,
                operationTraceMapper
        );
    }

    @Test
    void getEventHierarchy_buildsSessionStandZoneSeatTree() {
        Event event = new Event();
        event.setId(1L);
        event.setCode("EV-1");
        event.setName("Community Concert");
        event.setStartDate(LocalDate.of(2026, 1, 1));
        event.setEndDate(LocalDate.of(2026, 1, 3));
        when(eventMapper.findById(1L)).thenReturn(event);

        Season season = new Season();
        season.setId(2L);
        season.setEventId(1L);
        season.setName("Spring Season");
        season.setStartDate(LocalDate.of(2026, 1, 1));
        season.setEndDate(LocalDate.of(2026, 3, 31));
        when(seasonMapper.findByEventId(1L)).thenReturn(Collections.singletonList(season));

        SessionEntity session = new SessionEntity();
        session.setId(3L);
        session.setSeasonId(2L);
        session.setTitle("Matinee");
        session.setStartTime(LocalDateTime.of(2026, 1, 2, 14, 0));
        session.setEndTime(LocalDateTime.of(2026, 1, 2, 16, 0));
        when(sessionMapper.findBySeasonId(2L)).thenReturn(Collections.singletonList(session));

        Stand stand = new Stand();
        stand.setId(4L);
        stand.setSessionId(3L);
        stand.setCode("ST-A");
        stand.setName("North Stand");
        when(standMapper.findBySessionId(3L)).thenReturn(Collections.singletonList(stand));

        Zone zone = new Zone();
        zone.setId(5L);
        zone.setStandId(4L);
        zone.setSessionId(3L);
        zone.setCode("Z-1");
        zone.setName("Accessible Zone");
        zone.setCapacity(120);
        when(zoneMapper.findByStandId(4L)).thenReturn(Collections.singletonList(zone));

        Seat seat = new Seat();
        seat.setId(6L);
        seat.setZoneId(5L);
        seat.setSeatNumber("A-01");
        seat.setStatus("AVAILABLE");
        when(seatMapper.findByZoneId(5L)).thenReturn(Collections.singletonList(seat));

        VenueHierarchyResponse response = venueHierarchyService.getEventHierarchy(1L);

        assertEquals(1, response.getSeasons().size());
        assertEquals(1, response.getSeasons().get(0).getSessions().size());
        assertEquals(1, response.getSeasons().get(0).getSessions().get(0).getStands().size());
        assertEquals("ST-A", response.getSeasons().get(0).getSessions().get(0).getStands().get(0).getCode());
        assertEquals(1, response.getSeasons().get(0).getSessions().get(0).getStands().get(0).getZones().size());
        assertEquals("Z-1", response.getSeasons().get(0).getSessions().get(0).getStands().get(0).getZones().get(0).getCode());
        assertEquals(1, response.getSeasons().get(0).getSessions().get(0).getStands().get(0).getZones().get(0).getSeats().size());
        assertEquals("A-01", response.getSeasons().get(0).getSessions().get(0).getStands().get(0).getZones().get(0).getSeats().get(0).getSeatNumber());
    }
}
