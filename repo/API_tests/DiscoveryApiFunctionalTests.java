import java.net.http.HttpResponse;
import java.util.Map;

public final class DiscoveryApiFunctionalTests {

    private DiscoveryApiFunctionalTests() {}

    public static void run(String token, String username) throws Exception {
        testSuggestionsReturnsResults();
        testSearchReturnsItems();
        testBrowseSeasons();
        testBrowseSessions();
        testBrowseAnnouncements();
    }

    private static void testSuggestionsReturnsResults() throws Exception {
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "GET", "/api/discovery/suggestions?q=choir", null, null
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        if (!resp.body().contains("\"suggestions\"")) {
            throw new IllegalStateException("suggestions response missing suggestions key: " + resp.body());
        }
    }

    private static void testSearchReturnsItems() throws Exception {
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "GET",
            "/api/discovery/search?q=choir&type=ALL&author=&category=&sort=relevance&page=0&size=5",
            null,
            null
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        if (!resp.body().contains("\"items\"")) {
            throw new IllegalStateException("search response missing items key: " + resp.body());
        }
        if (!resp.body().contains("\"total\"")) {
            throw new IllegalStateException("search response missing total key: " + resp.body());
        }
    }

    private static void testBrowseSeasons() throws Exception {
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "GET", "/api/discovery/browse/seasons?sort=newest&page=0&size=10", null, null
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        if (!resp.body().contains("\"items\"")) {
            throw new IllegalStateException("browse seasons response missing items: " + resp.body());
        }
    }

    private static void testBrowseSessions() throws Exception {
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "GET", "/api/discovery/browse/sessions?sort=newest&page=0&size=10", null, null
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        if (!resp.body().contains("\"items\"")) {
            throw new IllegalStateException("browse sessions response missing items: " + resp.body());
        }
    }

    private static void testBrowseAnnouncements() throws Exception {
        HttpResponse<String> resp = ApiFunctionalTestHelper.request(
            "GET",
            "/api/discovery/browse/announcements?sort=relevance&page=0&size=10",
            null,
            null
        );
        ApiFunctionalTestHelper.requireStatus(resp, 200);
        if (!resp.body().contains("\"items\"")) {
            throw new IllegalStateException("browse announcements response missing items: " + resp.body());
        }
    }
}
