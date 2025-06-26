package org.randomfetcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

public class OpenAIAnalyzer {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String apiKey = resolveApiKey();

    public static String getApiKey() {
        return apiKey;
    }

    private static String resolveApiKey() {
        String key = System.getenv("OPENAI_API_KEY");
        if (key != null) return key;

        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            props.load(fis);
            key = props.getProperty("openai.api.key");
        } catch (IOException ignored) {
        }

        if (key == null || key.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY not set (neither env var nor config.properties)");
        }

        return key;
    }

    public static String analyze(List<Integer> bytes) throws Exception {
        ObjectNode root = JSON.createObjectNode();
        root.put("model", "gpt-3.5-turbo");
        root.put("max_tokens", 1050);

        ArrayNode messages = JSON.createArrayNode();
        messages.addObject()
                .put("role", "user")
                .put("content", String.format("""
                        +Bytes (0–255) = %s
                        +Give a ≤520-word summary of their basic randomness metrics (uniformity, chi² / KS, autocorr, runs).
                        +""", bytes));
        root.set("messages", messages);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(root)))
                .build();

        HttpResponse<String> resp =
                HTTP.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() != 200)
            throw new RuntimeException("OpenAI API error (" + resp.statusCode() + "): " + resp.body());

        return JSON.readTree(resp.body())
                .path("choices").get(0)
                .path("message").path("content")
                .asText().trim();
    }

}
