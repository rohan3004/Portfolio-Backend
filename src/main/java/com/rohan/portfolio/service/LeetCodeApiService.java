package com.rohan.portfolio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    // The raw response is uploaded verbatim; no parsing is done here.
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

    private final ObjectMapper objectMapper;

    public LeetCodeApiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches the raw GraphQL profile payload for the given LeetCode username
     * and returns it verbatim (no parsing/normalization).
     *
     * @param username LeetCode handle
     * @return the raw JSON response bytes exactly as returned by the GraphQL API
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

            // Step 2: POST the GraphQL query, echoing the CSRF token.
            // userCalendar requires a year argument; use the current calendar year.
            int currentYear = java.time.Year.now().getValue();
            String requestBody = objectMapper.writeValueAsString(
                    new GraphQlRequest(PROFILE_QUERY, new Variables(username, currentYear)));

            HttpPost post = new HttpPost(GRAPHQL_URL);
            post.addHeader("User-Agent", USER_AGENT);
            post.addHeader("Referer", "https://leetcode.com/u/" + username + "/");
            post.addHeader("x-csrftoken", csrfToken);
            post.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

            return client.execute(post, context, response -> {
                int status = response.getCode();
                byte[] bytes = EntityUtils.toByteArray(response.getEntity());

                // Transport-level guard only: don't upload a Cloudflare/error page
                // as if it were valid data. No parsing of the JSON payload is done.
                if (status != 200) {
                    throw new IllegalStateException(
                            "LeetCode GraphQL returned HTTP " + status
                                    + " for user '" + username + "'");
                }

                // Return the raw response payload verbatim for upload.
                return bytes;
            });
        }
    }

    // --- Request payload shapes (serialized by Jackson) ---

    private record GraphQlRequest(String query, Variables variables) {}

    private record Variables(String username, int year) {}
}
