package com.rohan.portfolio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * Fetches LeetCode profile statistics via the public GraphQL API instead of
 * scraping the HTML page.
 * Why: the LeetCode HTML route (/u/{user}/) is hard-blocked by Cloudflare (403),
 * so a headless browser cannot load it reliably. The GraphQL endpoint
 * (/graphql/) is reachable without a browser, requiring only a CSRF handshake:
 *   1. GET /graphql/ to obtain a fresh "csrftoken" cookie.
 *   2. POST the query, echoing that token in the "x-csrftoken" header and cookie.
 * This runs natively on a headless server (e.g. EC2) with no Chrome/Selenium.
 */
@Service
public class LeetCodeApiService {

    private static final String GRAPHQL_URL = "https://leetcode.com/graphql/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    // GraphQL query — mirrors the exact query set the LeetCode site uses
    // (sourced from the community-maintained alfa-leetcode-api, since LeetCode
    // disabled schema introspection). Verified to return HTTP 200 unauthenticated.
    // userCalendar(year:) yields only the requested year, so the calendar in
    // this base response is later merged with the other active years.
    private static final String PROFILE_QUERY =
            "query getFullUserProfile($username: String!, $year: Int!) {"
                    + "  allQuestionsCount { difficulty count }"
                    + "  matchedUser(username: $username) {"
                    + "    username"
                    + "    githubUrl"
                    + "    twitterUrl"
                    + "    linkedinUrl"
                    + "    contributions { points questionCount testcaseCount }"
                    + "    profile {"
                    + "      realName userAvatar birthday ranking reputation websites"
                    + "      countryName company school skillTags aboutMe starRating"
                    + "    }"
                    + "    badges { id displayName icon creationDate }"
                    + "    upcomingBadges { name icon }"
                    + "    activeBadge { id displayName icon creationDate }"
                    + "    submitStatsGlobal {"
                    + "      totalSubmissionNum { difficulty count submissions }"
                    + "      acSubmissionNum { difficulty count submissions }"
                    + "    }"
                    + "    userCalendar(year: $year) {"
                    + "      activeYears streak totalActiveDays submissionCalendar"
                    + "    }"
                    + "  }"
                    + "  userContestRanking(username: $username) {"
                    + "    attendedContestsCount rating globalRanking totalParticipants topPercentage"
                    + "    badge { name }"
                    + "  }"
                    + "  recentSubmissionList(username: $username, limit: 20) {"
                    + "    title titleSlug timestamp statusDisplay lang"
                    + "  }"
                    + "}";

    // Per-year calendar query. userCalendar(year:) returns only that year's
    // submissionCalendar, so we call this once per active year and merge the
    // resulting maps into a single calendar covering the user's whole history.
    private static final String CALENDAR_QUERY =
            "query userCalendar($username: String!, $year: Int!) {"
                    + "  matchedUser(username: $username) {"
                    + "    userCalendar(year: $year) {"
                    + "      activeYears submissionCalendar"
                    + "    }"
                    + "  }"
                    + "}";

    private final ObjectMapper objectMapper;

    public LeetCodeApiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches the GraphQL profile payload for the given LeetCode username.
     * The base response's single-year submissionCalendar is replaced with a
     * calendar merged across every year in activeYears; all other fields are
     * left exactly as returned by the API.
     *
     * @param username LeetCode handle
     * @return the profile JSON bytes with an all-years merged submissionCalendar
     * @throws Exception on network failure or a non-200 HTTP response
     */
    public byte[] fetchProfileStats(String username) throws Exception {
        BasicCookieStore cookieStore = new BasicCookieStore();
        HttpClientContext context = HttpClientContext.create();
        context.setCookieStore(cookieStore);

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .build()) {

            // Step 1: warm-up GET to obtain the csrftoken cookie.
            HttpGet warmUp = new HttpGet(GRAPHQL_URL);
            warmUp.addHeader("User-Agent", USER_AGENT);
            client.execute(warmUp, context, response -> {
                EntityUtils.consumeQuietly(response.getEntity());
                return null;
            });

            String csrfToken = cookieStore.getCookies().stream()
                    .filter(c -> "csrftoken".equals(c.getName()))
                    .map(org.apache.hc.client5.http.cookie.Cookie::getValue)
                    .findFirst()
                    .orElse("");

            // Step 2: POST the full profile query for the current year. This
            // response carries all profile/stats data plus the activeYears list.
            int currentYear = java.time.Year.now().getValue();
            byte[] baseBytes = executeQuery(client, context, csrfToken, username,
                    PROFILE_QUERY, currentYear);

            // Step 3: enrich the base response with a merged, all-years
            // submissionCalendar. Any failure here degrades gracefully to the
            // single-year payload rather than aborting the upload.
            try {
                return mergeAllYears(client, context, csrfToken, username, baseBytes);
            } catch (Exception e) {
                return baseBytes;
            }
        }
    }

    /**
     * Executes a single GraphQL POST and returns the raw response bytes.
     * Throws on a non-200 HTTP status (transport-level guard only).
     */
    private byte[] executeQuery(CloseableHttpClient client, HttpClientContext context,
                                String csrfToken, String username,
                                String query, int year) throws Exception {
        String requestBody = objectMapper.writeValueAsString(
                new GraphQlRequest(query, new Variables(username, year)));

        HttpPost post = new HttpPost(GRAPHQL_URL);
        post.addHeader("User-Agent", USER_AGENT);
        post.addHeader("Referer", "https://leetcode.com/u/" + username + "/");
        post.addHeader("x-csrftoken", csrfToken);
        post.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

        return client.execute(post, context, response -> {
            int status = response.getCode();
            byte[] bytes = EntityUtils.toByteArray(response.getEntity());
            if (status != 200) {
                throw new IllegalStateException(
                        "LeetCode GraphQL returned HTTP " + status
                                + " for user '" + username + "'");
            }
            return bytes;
        });
    }

    /**
     * Reads activeYears from the base response, fetches the submissionCalendar
     * for every active year, merges them into one map (summing counts on the
     * rare timestamp collision), and splices the merged calendar back into the
     * base response at matchedUser.userCalendar.submissionCalendar.
     *
     * @return the base response bytes with the merged calendar substituted, or
     *         the original bytes unchanged if the structure is unexpected
     */
    private byte[] mergeAllYears(CloseableHttpClient client, HttpClientContext context,
                                 String csrfToken, String username,
                                 byte[] baseBytes) throws Exception {
        JsonNode root = objectMapper.readTree(baseBytes);
        JsonNode userCalendar = root.path("data").path("matchedUser").path("userCalendar");
        if (userCalendar.isMissingNode() || !userCalendar.isObject()) {
            return baseBytes;
        }

        // Collect the distinct set of active years (sorted for determinism).
        int currentYear = java.time.Year.now().getValue();
        TreeSet<Integer> years = new TreeSet<>();
        for (JsonNode yearNode : userCalendar.path("activeYears")) {
            years.add(yearNode.asInt());
        }
        // Ensure the current year is included even if activeYears is empty.
        years.add(currentYear);

        // Merge each year's submissionCalendar (a JSON-encoded string of
        // { "unixTimestamp": count }) into one ordered map.
        Map<String, Long> merged = new LinkedHashMap<>();
        for (Integer year : years) {
            JsonNode yearCalendar;
            if (year == currentYear) {
                // Reuse the calendar already present in the base response.
                yearCalendar = userCalendar.path("submissionCalendar");
            } else {
                byte[] yearBytes = executeQuery(client, context, csrfToken, username,
                        CALENDAR_QUERY, year);
                yearCalendar = objectMapper.readTree(yearBytes)
                        .path("data").path("matchedUser")
                        .path("userCalendar").path("submissionCalendar");
            }
            mergeCalendarInto(merged, yearCalendar);
        }

        // Re-encode the merged map as a JSON string (matching LeetCode's shape:
        // submissionCalendar is itself a stringified JSON object) and splice it
        // back into the base response.
        String mergedCalendarString = objectMapper.writeValueAsString(merged);
        ((ObjectNode) userCalendar).put("submissionCalendar", mergedCalendarString);

        return objectMapper.writeValueAsBytes(root);
    }

    /**
     * Parses a submissionCalendar node (a stringified JSON object) and adds its
     * entries into the accumulator, summing counts if a timestamp repeats.
     */
    private void mergeCalendarInto(Map<String, Long> accumulator, JsonNode calendarNode) throws Exception {
        if (calendarNode == null || calendarNode.isMissingNode() || calendarNode.isNull()) {
            return;
        }
        // The value is a JSON string that itself encodes an object.
        String raw = calendarNode.isTextual() ? calendarNode.asText() : calendarNode.toString();
        if (raw == null || raw.isBlank() || "null".equals(raw)) {
            return;
        }
        JsonNode entries = objectMapper.readTree(raw);
        entries.fields().forEachRemaining(e ->
                accumulator.merge(e.getKey(), e.getValue().asLong(), Long::sum));
    }

    // --- Request payload shapes (serialized by Jackson) ---

    private record GraphQlRequest(String query, Variables variables) {}

    private record Variables(String username, int year) {}
}
