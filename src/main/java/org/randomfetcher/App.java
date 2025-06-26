package org.randomfetcher;

import javax.swing.SwingUtilities;

/**
 * Точка входа в приложение.
 * Запускает минимальный GUI (QRNG + логи) в EDT-потоке.
 */
public class App {

    @SuppressWarnings("unused")
    public static void main(String[] args) throws ClassNotFoundException {

        try {
            // Регистрируем SQLite JDBC-драйвер вручную
            Class.forName("org.sqlite.JDBC");

            LogManager logManager = new LogManager();
            RandomFetcherUI ui = new RandomFetcherUI(logManager);


        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            // Менеджер логов (окно + SLF4J)
            LogManager logManager = new LogManager();

            // GUI с тремя кнопками
            RandomFetcherUI ui = new RandomFetcherUI(logManager);
            ui.setVisible(true);

            // Первая запись в лог
            logManager.appendLog("Application started (QRNG-only GUI)");
        });
    }

}
