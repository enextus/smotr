package org.randomfetcher;

import javax.swing.SwingUtilities;
import java.awt.*;

/**
 * Точка входа в приложение.
 * Запускает минимальный GUI (QRNG + логи) в EDT-потоке.
 */
public class App {

    @SuppressWarnings("unused")
    public static void main(String[] args) {
        // Регистрация драйвера SQLite не обязательна (DriverManager сам загрузит через SPI),
        // но оставим на случай теневых ClassLoader'ов
        try { Class.forName("org.sqlite.JDBC"); } catch (ClassNotFoundException ignored) { }

        EventQueue.invokeLater(() -> {
            LogManager logManager = new LogManager();
            RandomFetcherUI ui = new RandomFetcherUI(logManager);
            ui.initUI();                       // <-- ВАЖНО!
            ui.setVisible(true);
            logManager.appendLog("Application started");
        });
    }

}