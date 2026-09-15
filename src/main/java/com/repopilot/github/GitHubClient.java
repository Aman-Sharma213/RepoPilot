package com.repopilot.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repopilot.git.FileDiff;
import com.repopilot.git.UnifiedDiffParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Minimal GitHub REST client for pull-request diffs.
 *
 * <p>Only the deterministic facts are taken from GitHub: base SHA, head SHA and the unified diff.
 * Every failure mode (missing token, 404, rate limit, timeout) is surfaced as a
 * {@link GitHubException} with an actionable message rather than an empty result.
 */
public class GitHubClient {

    public static final String TOKEN_ENV = "GITHUB_TOKEN";

    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String token;
    private final String apiBase;
    private final Duration timeout;

    public GitHubClient() {
        this(System.getenv(TOKEN_ENV), "https://api.github.com", Duration.ofSeconds(20));
    }

    public GitHubClient(String token, String apiBase, Duration timeout) {
        this.token = token;
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }

    public PullRequestInfo fetchPullRequest(PullRequestRef ref) {
        String base = apiBase + "/repos/" + ref.org() + "/" + ref.repo() + "/pulls/" + ref.number();
        JsonNode metadata = getJson(base);
        String diff = getText(base, "application/vnd.github.v3.diff");
        List<FileDiff> files = new UnifiedDiffParser().parse(diff);
        return new PullRequestInfo(ref,
                metadata.path("title").asText(""),
                metadata.path("base").path("sha").asText(""),
                metadata.path("head").path("sha").asText(""),
                metadata.path("state").asText("unknown"),
                files);
    }

    private JsonNode getJson(String url) {
        try {
            return mapper.readTree(getText(url, "application/vnd.github+json"));
        } catch (IOException e) {
            throw new GitHubException("GitHub returned malformed JSON for " + url, e);
        }
    }

    private String getText(String url, String accept) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Accept", accept)
                .header("User-Agent", "repo-pilot")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET();
        if (hasToken()) {
            request.header("Authorization", "Bearer " + token);
        }
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 200) {
                return response.body();
            }
            String remaining = response.headers().firstValue("x-ratelimit-remaining").orElse(null);
            if (status == 403 && "0".equals(remaining)) {
                throw new GitHubException("GitHub rate limit exhausted. Set " + TOKEN_ENV
                        + " to raise the limit, or retry later.", status);
            }
            if (status == 401) {
                throw new GitHubException("GitHub rejected the credentials in " + TOKEN_ENV + ".", status);
            }
            if (status == 404) {
                throw new GitHubException("Not found: " + url
                        + (hasToken() ? "" : " (private repositories need " + TOKEN_ENV + ")"), status);
            }
            throw new GitHubException("GitHub request failed with HTTP " + status + " for " + url, status);
        } catch (java.net.http.HttpTimeoutException e) {
            throw new GitHubException("GitHub request timed out after " + timeout.toSeconds() + "s", e);
        } catch (IOException e) {
            throw new GitHubException("Cannot reach GitHub: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubException("GitHub request interrupted", e);
        }
    }
}
