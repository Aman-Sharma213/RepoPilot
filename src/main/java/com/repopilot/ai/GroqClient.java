package com.repopilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Groq chat-completions client used only for ranking existing symbols.
 *
 * <p>The client is optional by construction: when {@code GROQ_API_KEY} is absent, callers fall back
 * to deterministic ranking. Timeouts, HTTP errors and malformed model output all raise
 * {@link AiUnavailableException}, which callers treat as "no AI available" rather than as a crash.
 */
public class GroqClient {

    public static final String API_KEY_ENV = "GROQ_API_KEY";
    public static final String MODEL_ENV = "GROQ_MODEL";
    public static final String DEFAULT_MODEL = "llama-3.3-70b-versatile";
    public static final String DEFAULT_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";

    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String model;
    private final String endpoint;
    private final Duration timeout;

    public GroqClient() {
        this(System.getenv(API_KEY_ENV),
                System.getenv(MODEL_ENV) == null ? DEFAULT_MODEL : System.getenv(MODEL_ENV),
                DEFAULT_ENDPOINT, Duration.ofSeconds(30));
    }

    public static GroqClient disabled() {
        return new GroqClient(null, DEFAULT_MODEL, DEFAULT_ENDPOINT, Duration.ofSeconds(30));
    }

    public static GroqClient forKey(String apiKey, String model) {
        return new GroqClient(apiKey, model, DEFAULT_ENDPOINT, Duration.ofSeconds(30));
    }

    public GroqClient(String apiKey, String model, String endpoint, Duration timeout) {
        this.apiKey = apiKey;
        this.model = model;
        this.endpoint = endpoint;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String model() {
        return model;
    }

    /** Sends a prompt and returns the raw assistant message content. */
    public String complete(String systemPrompt, String userPrompt) {
        if (!isConfigured()) {
            throw new AiUnavailableException(API_KEY_ENV + " is not set");
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0);
        body.putObject("response_format").put("type", "json_object");
        var messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                throw new AiUnavailableException("Groq rate limit reached");
            }
            if (response.statusCode() / 100 != 2) {
                throw new AiUnavailableException("Groq returned HTTP " + response.statusCode());
            }
            JsonNode json = mapper.readTree(response.body());
            JsonNode content = json.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new AiUnavailableException("Groq returned an empty completion");
            }
            return content.asText();
        } catch (java.net.http.HttpTimeoutException e) {
            throw new AiUnavailableException("Groq request timed out after " + timeout.toSeconds() + "s");
        } catch (IOException e) {
            throw new AiUnavailableException("Cannot reach Groq: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("Groq request interrupted");
        }
    }
}
