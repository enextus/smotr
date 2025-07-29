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

/**
 * Мини‑клиент к OpenAI Chat‑API.
 *
 * <p><b>Новое:</b> метод {@link #analyzeWithPrompt(String)} — принимает готовый
 * prompt; старый analyze(List) оставлен для совместимости.</p>
 */
public class OpenAIAnalyzer {

    /* ----------------------- конфигурация ----------------------- */
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_2)
            .build();

    private static final String MODEL = System.getProperty("openai.model", "gpt-4o-mini");
    private static final String API_KEY = resolveApiKey();

    /* ------------------------------------------------------------- */
    private static String resolveApiKey() {
        String key = System.getenv("OPENAI_API_KEY");
        if (key != null && !key.isBlank()) return key;

        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            props.load(fis);
            key = props.getProperty("openai.api.key");
        } catch (IOException ignored) { }

        if (key == null || key.isBlank())
            throw new IllegalStateException("OPENAI_API_KEY not set (env or config.properties)");
        return key;
    }

    /* ======================= ПУБЛИЧНЫЕ МЕТОДЫ ======================= */

    /** Старый метод: получает bytes и строит короткий prompt. */
    public static String analyze(List<Integer> bytes) throws Exception {
        String prompt = """
Bytes (0‑255): %s
Кратко (≤150 слов) оцени равномерность, χ²/KS, автокорреляцию, runs.
На русском и английском.
""".formatted(bytes);
        return analyzeWithPrompt(prompt);
    }

    /** Новый метод: принимает готовый prompt, ничего не добавляет. */
    public static String analyzeWithPrompt(String prompt) throws Exception {
        ObjectNode root = JSON.createObjectNode();
        root.put("model", MODEL);
        root.put("max_tokens", 400);

        ArrayNode messages = JSON.createArrayNode();
        messages.addObject()
                .put("role", "user")
                .put("content", prompt);
        root.set("messages", messages);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(root)))
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new RuntimeException("OpenAI error " + resp.statusCode() + ": " + truncate(resp.body(), 300));

        return JSON.readTree(resp.body())
                .path("choices").get(0)
                .path("message").path("content")
                .asText().trim();
    }

    /* ----------------------- утилитарное -------------------------- */
    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
