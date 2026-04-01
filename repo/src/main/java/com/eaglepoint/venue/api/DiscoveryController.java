package com.eaglepoint.venue.api;

import com.eaglepoint.venue.api.dto.PagedDiscoveryResponse;
import com.eaglepoint.venue.api.dto.SuggestionResponse;
import com.eaglepoint.venue.service.DiscoveryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/discovery")
public class DiscoveryController {

    private final DiscoveryService discoveryService;

    public DiscoveryController(DiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @GetMapping("/suggestions")
    public SuggestionResponse suggestions(@RequestParam String q) {
        return discoveryService.typeAheadSuggestions(q);
    }

    @GetMapping("/search")
    public PagedDiscoveryResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "ALL") String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer minWords,
            @RequestParam(required = false) Integer maxWords,
            @RequestParam(required = false, defaultValue = "relevance") String sort,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size
    ) {
        return discoveryService.search(q, type, from, to, author, category, minWords, maxWords, sort, page, size);
    }

    @GetMapping("/browse/seasons")
    public PagedDiscoveryResponse browseSeasons(
            @RequestParam(required = false, defaultValue = "newest") String sort,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size
    ) {
        return discoveryService.browseSeasons(page, size, sort);
    }

    @GetMapping("/browse/sessions")
    public PagedDiscoveryResponse browseSessions(
            @RequestParam(required = false, defaultValue = "newest") String sort,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size
    ) {
        return discoveryService.browseSessions(page, size, sort);
    }

    @GetMapping("/browse/announcements")
    public PagedDiscoveryResponse browseAnnouncements(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer minWords,
            @RequestParam(required = false) Integer maxWords,
            @RequestParam(required = false, defaultValue = "relevance") String sort,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size
    ) {
        return discoveryService.browseAnnouncements(from, to, author, category, minWords, maxWords, page, size, sort);
    }
}
