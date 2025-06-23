package org.randomfetcher;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.swing.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Набор unit‑тестов для проекта RandomFetcher.
 *
 * Используемые зависимости:
 *   – JUnit Jupiter 5.11.0
 *   – Mockito 5.12.0 (artifact **mockito‑inline** – нужен для static/`final`‑mocking)
 *   – Apache Commons Math 3.6.1 для RandomnessTester
 *   – (опционально) AssertJ‑Swing для UI‑проверок
 */
@ExtendWith(MockitoExtension.class)
class RandomFetcherTests {

    /* ---------------- LogManager ---------------- */

    @Test
    void appendLog_updatesTextComponent_afterWindowIsShown() throws Exception {
        LogManager mgr = new LogManager();
        // Создаём окно в EDT
        SwingUtilities.invokeAndWait(mgr::showLogWindow);
        mgr.appendLog("Hello, logs!");

        JTextArea area = getPrivateField(mgr, "logTextArea");
        assertNotNull(area, "Текстовая область должна быть инициализирована");
        assertTrue(area.getText().contains("Hello, logs!"));
    }

    @Test
    void appendLog_beforeWindowShown_isNoOp() {
        LogManager mgr = new LogManager();
        assertDoesNotThrow(() -> mgr.appendLog("Should not throw even with no window yet"));
    }

    /* ---------------- RandomFetcher ---------------- */



    @Test
    void logRun_writesRow_toInMemoryDb() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            // Вызовем приватные методы createSchemaIfNeeded и logRun через reflection
            Method createSchema = RandomFetcher.class.getDeclaredMethod("createSchemaIfNeeded", Connection.class);
            createSchema.setAccessible(true);
            createSchema.invoke(null, db);

            Method logRun = RandomFetcher.class.getDeclaredMethod(
                    "logRun", Connection.class, String.class, int.class, int[].class, String.class);
            logRun.setAccessible(true);

            int[] numbers = {42, 43};
            logRun.invoke(null, db, "TestAPI", numbers.length, numbers, null);

            // Проверяем, что запись в таблице появилась
            try (Statement st = db.createStatement();
                 ResultSet rs = st.executeQuery("SELECT api_name, count, success FROM runs")) {
                assertTrue(rs.next(), "Ожидалась хотя бы одна строка в runs");
                assertEquals("TestAPI", rs.getString("api_name"));
                assertEquals(numbers.length, rs.getInt("count"));
                assertEquals(1, rs.getInt("success"));
                assertFalse(rs.next(), "Должна быть ровно одна строка");
            }
        }
    }

    /* ---------------- RandomnessTester ---------------- */

    @Test
    void randomnessTests_basicStatistics() {
        java.util.Random rnd = new java.util.Random(42);
        List<Integer> seq = rnd.ints(2_048, 0, 256)
                .boxed()
                .collect(Collectors.toList());
        RandomnessTester tester = new RandomnessTester(seq, 0, 255);

        // Проверяем лишь, что методы отрабатывают и показатели разумны
        assertTrue(tester.chiSquareTest(16, 0.05));
        double ac = tester.autocorrelation(1);
        assertTrue(Math.abs(ac) < 0.2, "Autocorrelation too high: " + ac);
        assertTrue(tester.countConsecutiveRepeats() < 10);
    }

    /* ---------------- helpers ---------------- */

    @SuppressWarnings("unchecked")
    private static <T> T getPrivateField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    private static void setStaticFinal(Class<?> clazz, String fieldName, Object newValue) throws Exception {
        // Используем VarHandle, который умеет писать в final-поля на JDK 12+
        java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.privateLookupIn(clazz, java.lang.invoke.MethodHandles.lookup());
        java.lang.invoke.VarHandle vh = lookup.findStaticVarHandle(clazz, fieldName, field.getType());
        vh.set(newValue);
    }
}
