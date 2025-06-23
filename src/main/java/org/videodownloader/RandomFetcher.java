package org.videodownloader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Java-порт скрипта App_with_sqlite_debug.rb.
 *
 * Запуск:  java --enable-preview -jar qrng-fetcher.jar <count>
 * where <count> — обязательное положительное целое.
 */
public final class RandomFetcher {

    private static final Logger log   = LoggerFactory.getLogger(RandomFetcher.class);
    private static final ObjectMapper JSON  = new ObjectMapper();
    private static final HttpClient   HTTP  = HttpClient.newHttpClient();

    /* ------- описание API (record-класс) ------- */
    record Api(String name, String urlTpl,
               String dataKey, String successKey, boolean active) {
        String url(int cnt) { return urlTpl.replace("#{count}", String.valueOf(cnt)); }
    }

    /* ------- список поддерживаемых сервисов ------- */
    private static final List<Api> APIS = List.of(
            new Api("QRandom.io",
                    "https://qrandom.io/api/random/ints?min=0&max=255&n=#{count}",
                    "numbers", "numbers", true),
            new Api("LfD QRNG (OTH Regensburg)",
                    "https://lfdr.de/qrng_api/qrng?length=#{count}&format=HEX",
                    "qrn", "qrn", true)
    );

    /* ========================== MAIN ========================== */
    public static void main(String[] args) {
        /* --- валидация аргумента --- */
        if (args.length != 1) {
            System.err.println("Usage: RandomFetcher <count>");
            System.exit(1);
        }
        int count;
        try {
            count = Integer.parseInt(args[0]);
            if (count <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            System.err.println("<count> must be positive integer");
            System.exit(1);
            return; // для тишины компилятора
        }

        log.info("Will request {} random bytes …", count);

        Path dbFile = Path.of(System.getProperty("user.dir"), "random_runs.sqlite3");
        try (Connection db = connectOrCreate(dbFile)) {
            createSchemaIfNeeded(db);                      // ← гарантированно до INSERT

            int[] numbers = null;
            for (var api : APIS) {
                if (!api.active()) continue;
                log.info("→ API: {}", api.name());
                numbers = fetchRandomNumbers(api, count);
                logRun(db, api.name(), count, numbers, numbers == null ? "fail" : null);
                if (numbers != null) break;               // успех
            }

            if (numbers != null) {
                log.info("SUCCESS: {}", numbers);
            } else {
                log.error("All APIs failed.");
                System.exit(1);
            }
        } catch (SQLException e) {
            log.error("SQLite error: {}", e.getMessage(), e);
            System.exit(1);
        } catch (Exception e) {
            log.error("Fatal error: {}", e.toString(), e);
            System.exit(1);
        }
    }

    /** Запрашивает <count> байтов и возвращает их как int[]. */
    public static int[] fetchBytes(int count) throws Exception {
        Path dbFile = Path.of(System.getProperty("user.dir"), "random_runs.sqlite3");
        try (Connection db = connectOrCreate(dbFile)) {
            createSchemaIfNeeded(db);

            int[] numbers = null;
            for (var api : APIS) {
                if (!api.active()) continue;
                numbers = fetchRandomNumbers(api, count);
                logRun(db, api.name(), count, numbers, numbers == null ? "fail" : null);
                if (numbers != null) break;
            }
            if (numbers == null)
                throw new IllegalStateException("All APIs failed");
            return numbers;
        }
    }


    /* ======================= HTTP/JSON ======================= */
    private static int[] fetchRandomNumbers(Api api, int count) {
        URI uri = URI.create(api.url(count));
        for (int attempt = 1; attempt <= 5; attempt++) {
            log.debug("[FETCH] {} try {}", api.name(), attempt);
            try {
                HttpRequest req = HttpRequest.newBuilder(uri).GET().build();
                HttpResponse<String> res =
                        HTTP.send(req, HttpResponse.BodyHandlers.ofString());

                if (res.statusCode() != 200) {
                    log.debug("Status {}", res.statusCode());
                    continue;
                }
                JsonNode json = JSON.readTree(res.body());
                if (!json.has(api.successKey())) continue;

                JsonNode dataNode = json.get(api.dataKey());
                if (dataNode == null) continue;

                int[] data;
                if (dataNode.isTextual()) { // HEX-строка
                    byte[] bytes = HexFormat.of().parseHex(dataNode.asText());
                    data = new int[bytes.length];
                    for (int i = 0; i < bytes.length; i++)
                        data[i] = Byte.toUnsignedInt(bytes[i]);
                } else if (dataNode.isArray()) { // массив чисел
                    data = new int[dataNode.size()];
                    for (int i = 0; i < dataNode.size(); i++)
                        data[i] = dataNode.get(i).asInt();
                } else {
                    continue;
                }
                return data;                              // ✓ успех
            } catch (Exception e) {
                log.debug("Attempt failed: {}", e.toString());
            }
            if (attempt < 5) sleepSeconds(5);
        }
        return null;
    }

    /* ======================= SQLite ======================= */
    private static Connection connectOrCreate(Path file) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
    }

    private static void createSchemaIfNeeded(Connection db) throws SQLException {
        try (Statement st = db.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS runs(
                  id        INTEGER PRIMARY KEY AUTOINCREMENT,
                  ts        TEXT    NOT NULL,
                  api_name  TEXT,
                  count     INTEGER,
                  numbers   TEXT,
                  success   INTEGER,
                  error_msg TEXT
                )
            """);
        }
    }

    private static void logRun(Connection db, String apiName,
                               int count, int[] numbers,
                               String errorMsg) throws SQLException {

        String sql = """
            INSERT INTO runs(ts, api_name, count, numbers, success, error_msg)
            VALUES(?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps =
                     db.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, OffsetDateTime.now().toString());
            ps.setString(2, apiName);
            ps.setInt   (3, count);
            ps.setString(4, numbers != null ? JSON.valueToTree(numbers).toString() : null);
            ps.setInt   (5, numbers != null ? 1 : 0);
            ps.setString(6, errorMsg);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next())
                    log.debug("[LOG] Inserted row id {}", keys.getLong(1));
            }
        }
    }

    /* ===================== misc helpers ===================== */
    private static void sleepSeconds(int s) {
        try { TimeUnit.SECONDS.sleep(s); }
        catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    private RandomFetcher() { }  // запрет instantiation
}
