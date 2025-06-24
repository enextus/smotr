package org.randomfetcher;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cdimascio.dotenv.Dotenv;     // +- dotenv
import java.net.http.*;
import java.net.URI;
import java.time.Duration;
import java.util.List;

public final class OpenAIAnalyzer {

    /* 1) читаем переменную среды ИЛИ .env */
    private static final String API_KEY = resolveApiKey();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private OpenAIAnalyzer() {}

    private static String resolveApiKey() {
        String k = System.getenv("OPENAI_API_KEY");
        if (k == null || k.isBlank()) {
            // .env в корне проекта (java-dotenv ищет автоматически)
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            k = dotenv.get("OPENAI_API_KEY");
        }
        if (k == null || k.isBlank())
            throw new IllegalStateException(
                    "OPENAI_API_KEY not set (env var or .env)");
        return k;
    }

    /** Отправляет байтовую последовательность в GPT-3.5-Turbo. */
    public static String analyze(List<Integer> bytes) throws Exception {

        ObjectNode root = JSON.createObjectNode();
        root.put("model", "gpt-3.5-turbo");
        root.put("max_tokens", 250);

        ArrayNode messages = JSON.createArrayNode();
        messages.addObject()
                .put("role", "system")
                .put("content", "You are a terse, precise statistician who explains randomness-test results in plain language.");
        messages.addObject()
                .put("role", "user")
                .put("content", STR."""
Bytes (0-255) = \{bytes}
Give a ≤120-word summary of their basic randomness metrics (uniformity, chi² / KS, autocorr, runs).
""");
        root.set("messages", messages);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + API_KEY)
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
