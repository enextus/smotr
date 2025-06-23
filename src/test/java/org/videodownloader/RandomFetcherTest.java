package org.videodownloader;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

class RandomFetcherTest {

    static WireMockServer wm;

    @BeforeAll
    static void startStub() {
        wm = new WireMockServer(0);   // random free port
        wm.start();
        configureFor("localhost", wm.port());

        // stub JSON ответ
        stubFor(get(urlPathEqualTo("/api/random/ints"))
                .willReturn(okJson("{\"numbers\":[1,2,3]}")));

        // Поменяем URL первого API на локальный
        RandomFetcher.Api localApi = new RandomFetcher.Api(
                "LOCAL", "http://localhost:" + wm.port() + "/api/random/ints?n=#{count}&min=0&max=255",
                "numbers", "numbers", true);
        // reflection-хак: подменяем константное поле APIS
        TestUtil.replacePrivateStaticList(RandomFetcher.class, "APIS",
                java.util.List.of(localApi));
    }

    @AfterAll static void stop() { wm.stop(); }

    @Test
    void fetchBytes_returns_expected_array_and_logs_to_db() throws Exception {
        int[] result = RandomFetcher.fetchBytes(3);
        assertThat(result).containsExactly(1, 2, 3);

        // проверяем, что запись попала в SQLite
        Path db = Path.of("random_runs.sqlite3");
        assertThat(db).exists();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement  s = c.createStatement();
             ResultSet  rs = s.executeQuery("SELECT count FROM runs ORDER BY id DESC LIMIT 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("count")).isEqualTo(3);
        }
        Files.deleteIfExists(db); // чистим за собой
    }
}
