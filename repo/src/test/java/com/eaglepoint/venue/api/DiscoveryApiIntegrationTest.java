package com.eaglepoint.venue.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoveryApiIntegrationTest extends ApiTestBase {

    // -----------------------------------------------------------------------
    // Suggestions
    // -----------------------------------------------------------------------

    @Test
    void suggestions_success() {
        ResponseEntity<String> resp = get("/api/discovery/suggestions?q=choir");

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "suggestions"), "Body should contain 'suggestions' key");
    }

    @Test
    void suggestions_shortQuery() {
        ResponseEntity<String> resp = get("/api/discovery/suggestions?q=a");

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
    }

    @Test
    void suggestions_missingParam() {
        ResponseEntity<String> resp = get("/api/discovery/suggestions");

        assertStatus(resp, HttpStatus.BAD_REQUEST);
    }

    // -----------------------------------------------------------------------
    // Search
    // -----------------------------------------------------------------------

    @Test
    void search_success() {
        ResponseEntity<String> resp = get(
                "/api/discovery/search?q=choir&type=ALL&sort=relevance&page=0&size=5");

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "items"), "Body should contain 'items' key");
    }

    @Test
    void search_withFilters() {
        ResponseEntity<String> resp = get(
                "/api/discovery/search?q=&type=ALL&author=Maria+Santos&category=Programs&sort=relevance&page=0&size=10");

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
    }

    @Test
    void search_pagination() {
        ResponseEntity<String> resp = get(
                "/api/discovery/search?q=&type=ALL&page=0&size=1");

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "total"), "Body should contain 'total' key");
    }

    // -----------------------------------------------------------------------
    // Browse — Seasons
    // -----------------------------------------------------------------------

    @Test
    void browseSeasons_success() {
        ResponseEntity<String> resp = get(
                "/api/discovery/browse/seasons?sort=newest&page=0&size=10");

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "items"), "Body should contain 'items' key");
    }

    // -----------------------------------------------------------------------
    // Browse — Sessions
    // -----------------------------------------------------------------------

    @Test
    void browseSessions_success() {
        ResponseEntity<String> resp = get(
                "/api/discovery/browse/sessions?sort=newest&page=0&size=10");

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "items"), "Body should contain 'items' key");
    }

    // -----------------------------------------------------------------------
    // Browse — Announcements
    // -----------------------------------------------------------------------

    @Test
    void browseAnnouncements_success() {
        ResponseEntity<String> resp = get(
                "/api/discovery/browse/announcements?sort=relevance&page=0&size=10");

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "items"), "Body should contain 'items' key");
    }

    @Test
    void browseAnnouncements_withFilters() {
        ResponseEntity<String> resp = get(
                "/api/discovery/browse/announcements?author=Maria+Santos&category=Wellness&sort=relevance&page=0&size=10");

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
    }

    // -----------------------------------------------------------------------
    // Search — additional coverage
    // -----------------------------------------------------------------------

    @Test
    void search_emptyQuery_returnsAll() {
        ResponseEntity<String> resp = get(
                "/api/discovery/search?q=&type=ALL&page=0&size=10");

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "total"), "Body should contain 'total' key");
    }

    @Test
    void search_wordCountFilters() {
        ResponseEntity<String> resp = get(
                "/api/discovery/search?q=&type=ALL&minWords=10&maxWords=50&page=0&size=10");

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
    }

    // -----------------------------------------------------------------------
    // Browse — Seasons additional coverage
    // -----------------------------------------------------------------------

    @Test
    void browseSeasons_pagination() {
        ResponseEntity<String> resp = get(
                "/api/discovery/browse/seasons?page=0&size=1");

        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody(), "Response body must not be null");
        assertTrue(bodyContains(resp.getBody(), "total"), "Body should contain 'total' key");
    }

    @Test
    void search_invalidDateParam() {
        ResponseEntity<String> resp = get("/api/discovery/search?q=test&from=not-a-date");
        assertTrue(resp.getStatusCode().is4xxClientError(),
            "Invalid date parameter should return 4xx but got: " + resp.getStatusCode());
    }

    @Test
    void search_invalidPagination() {
        ResponseEntity<String> resp = get("/api/discovery/search?q=test&page=-1&size=0");
        // Either 400 for invalid params or 200 with empty results (depends on implementation)
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError(),
            "Invalid pagination should be handled gracefully, got: " + resp.getStatusCode());
    }

    @Test
    void search_sortByNewest_returnsResults() {
        ResponseEntity<String> resp = get("/api/discovery/search?q=&type=ALL&sort=newest&page=0&size=10");
        assertStatus(resp, HttpStatus.OK);
        assertNotNull(resp.getBody());
        assertTrue(bodyContains(resp.getBody(), "items"), "Sort by newest should still return items");
        assertTrue(bodyContains(resp.getBody(), "total"), "Response should contain total count");
    }
}
