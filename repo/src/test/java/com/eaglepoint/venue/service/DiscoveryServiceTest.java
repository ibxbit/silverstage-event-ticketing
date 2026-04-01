package com.eaglepoint.venue.service;

import com.eaglepoint.venue.api.dto.PagedDiscoveryResponse;
import com.eaglepoint.venue.api.dto.SuggestionResponse;
import com.eaglepoint.venue.domain.CommunityAnnouncement;
import com.eaglepoint.venue.domain.Event;
import com.eaglepoint.venue.domain.Season;
import com.eaglepoint.venue.domain.SessionEntity;
import com.eaglepoint.venue.mapper.CommunityAnnouncementMapper;
import com.eaglepoint.venue.mapper.EventMapper;
import com.eaglepoint.venue.mapper.SeasonMapper;
import com.eaglepoint.venue.mapper.SessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscoveryServiceTest {

    @Mock
    private EventMapper eventMapper;
    @Mock
    private SeasonMapper seasonMapper;
    @Mock
    private SessionMapper sessionMapper;
    @Mock
    private CommunityAnnouncementMapper communityAnnouncementMapper;

    private DiscoveryService discoveryService;

    @BeforeEach
    void setUp() {
        discoveryService = new DiscoveryService(eventMapper, seasonMapper, sessionMapper, communityAnnouncementMapper);
    }

    @Test
    void search_appliesRelevanceHighlightingAndDeduplication() {
        Event duplicateA = new Event();
        duplicateA.setId(1L);
        duplicateA.setName("Jazz Night");
        duplicateA.setCode("JAZZ-001");
        duplicateA.setStartDate(LocalDate.of(2026, 4, 10));

        Event duplicateB = new Event();
        duplicateB.setId(1L);
        duplicateB.setName("Jazz Night");
        duplicateB.setCode("JAZZ-001");
        duplicateB.setStartDate(LocalDate.of(2026, 4, 10));

        Season season = new Season();
        season.setId(2L);
        season.setEventId(1L);
        season.setName("Spring Classics");
        season.setStartDate(LocalDate.of(2026, 4, 1));

        SessionEntity session = new SessionEntity();
        session.setId(3L);
        session.setTitle("Morning mobility workshop");
        session.setStartTime(LocalDateTime.of(2026, 4, 12, 10, 0));

        CommunityAnnouncement announcement = new CommunityAnnouncement();
        announcement.setId(4L);
        announcement.setTitle("Jazz update for families");
        announcement.setBody("Jazz schedule change and new seats available.");
        announcement.setAuthor("editor_a");
        announcement.setCategory("Culture");
        announcement.setWordCount(8);
        announcement.setPopularity(10);
        announcement.setPublishedAt(LocalDateTime.of(2026, 4, 11, 8, 0));

        when(eventMapper.findAll()).thenReturn(Arrays.asList(duplicateA, duplicateB));
        when(seasonMapper.findAll()).thenReturn(Collections.singletonList(season));
        when(sessionMapper.findAll()).thenReturn(Collections.singletonList(session));
        when(communityAnnouncementMapper.findAll()).thenReturn(Collections.singletonList(announcement));

        PagedDiscoveryResponse response = discoveryService.search(
                "jazz",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "relevance",
                0,
                20
        );

        assertEquals(Long.valueOf(2L), response.getTotal());
        assertTrue(response.getItems().get(0).getHighlightedTitle().contains("<mark>Jazz</mark>"));
        assertTrue(response.getItems().get(0).getHighlightedSnippet().toLowerCase().contains("<mark>jazz</mark>"));
        assertTrue(response.getItems().stream().anyMatch(item -> "EVENT".equals(item.getType())));
        assertTrue(response.getItems().stream().anyMatch(item -> "ANNOUNCEMENT".equals(item.getType())));
    }

    @Test
    void typeAheadSuggestions_returnsRelevantUniqueSuggestions() {
        Event event = new Event();
        event.setId(10L);
        event.setName("Summer Gala");
        event.setCode("GAL-01");
        event.setStartDate(LocalDate.of(2026, 6, 1));

        CommunityAnnouncement announcement = new CommunityAnnouncement();
        announcement.setId(11L);
        announcement.setTitle("Summer updates");
        announcement.setBody("Program highlights");
        announcement.setAuthor("summer_editor");
        announcement.setCategory("Seasonal");
        announcement.setWordCount(2);
        announcement.setPopularity(1);
        announcement.setPublishedAt(LocalDateTime.of(2026, 6, 2, 9, 0));

        when(eventMapper.findAll()).thenReturn(Collections.singletonList(event));
        when(seasonMapper.findAll()).thenReturn(Collections.<Season>emptyList());
        when(sessionMapper.findAll()).thenReturn(Collections.<SessionEntity>emptyList());
        when(communityAnnouncementMapper.findAll()).thenReturn(Collections.singletonList(announcement));

        SuggestionResponse response = discoveryService.typeAheadSuggestions("sum");

        assertFalse(response.getSuggestions().isEmpty());
        assertTrue(response.getSuggestions().contains("Summer Gala"));
        assertTrue(response.getSuggestions().contains("Summer updates"));
    }

    @Test
    void search_appliesDateAuthorCategoryWordAndPaginationBoundaries() {
        when(eventMapper.findAll()).thenReturn(Collections.<Event>emptyList());
        when(seasonMapper.findAll()).thenReturn(Collections.<Season>emptyList());
        when(sessionMapper.findAll()).thenReturn(Collections.<SessionEntity>emptyList());

        CommunityAnnouncement a1 = new CommunityAnnouncement();
        a1.setId(1L);
        a1.setTitle("City Event Bulletin");
        a1.setBody("General event bulletin");
        a1.setAuthor("alice");
        a1.setCategory("news");
        a1.setWordCount(3);
        a1.setPopularity(1);
        a1.setPublishedAt(LocalDateTime.of(2026, 5, 10, 9, 0));

        CommunityAnnouncement a2 = new CommunityAnnouncement();
        a2.setId(2L);
        a2.setTitle("City Event Deep Dive");
        a2.setBody("Detailed category analysis");
        a2.setAuthor("alice");
        a2.setCategory("news");
        a2.setWordCount(10);
        a2.setPopularity(5);
        a2.setPublishedAt(LocalDateTime.of(2026, 5, 11, 10, 0));

        CommunityAnnouncement a3 = new CommunityAnnouncement();
        a3.setId(3L);
        a3.setTitle("Other category item");
        a3.setBody("Different author and category");
        a3.setAuthor("bob");
        a3.setCategory("alerts");
        a3.setWordCount(8);
        a3.setPopularity(9);
        a3.setPublishedAt(LocalDateTime.of(2026, 5, 12, 8, 0));

        when(communityAnnouncementMapper.findAll()).thenReturn(Arrays.asList(a1, a2, a3));

        PagedDiscoveryResponse filtered = discoveryService.search(
                "event",
                null,
                LocalDateTime.of(2026, 5, 10, 0, 0),
                LocalDateTime.of(2026, 5, 11, 0, 0),
                "alice",
                "news",
                2,
                10,
                "newest",
                0,
                1
        );

        assertEquals(Long.valueOf(2L), filtered.getTotal());
        assertEquals(1, filtered.getItems().size());
        assertEquals(Long.valueOf(2L), filtered.getItems().get(0).getId());

        PagedDiscoveryResponse nextPage = discoveryService.search(
                "event",
                null,
                LocalDateTime.of(2026, 5, 10, 0, 0),
                LocalDateTime.of(2026, 5, 11, 0, 0),
                "alice",
                "news",
                2,
                10,
                "newest",
                1,
                1
        );
        assertEquals(1, nextPage.getItems().size());
        assertEquals(Long.valueOf(1L), nextPage.getItems().get(0).getId());
    }

    @Test
    void search_handlesPaginationExtremesWithSafeDefaults() {
        Event event = new Event();
        event.setId(88L);
        event.setName("Edge Case Event");
        event.setCode("EDGE-1");
        event.setStartDate(LocalDate.of(2026, 7, 1));

        when(eventMapper.findAll()).thenReturn(Collections.singletonList(event));
        when(seasonMapper.findAll()).thenReturn(Collections.<Season>emptyList());
        when(sessionMapper.findAll()).thenReturn(Collections.<SessionEntity>emptyList());
        when(communityAnnouncementMapper.findAll()).thenReturn(Collections.<CommunityAnnouncement>emptyList());

        PagedDiscoveryResponse response = discoveryService.search(
                "edge",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "relevance",
                -5,
                1000
        );

        assertEquals(Integer.valueOf(0), response.getPage());
        assertEquals(Integer.valueOf(100), response.getSize());
        assertEquals(Long.valueOf(1L), response.getTotal());
    }
}
