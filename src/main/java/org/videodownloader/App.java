package org.videodownloader;

import javax.swing.SwingUtilities;

/**
 * Точка входа в приложение.
 * Запускает минимальный GUI (QRNG + логи) в EDT-потоке.
 */
public class App {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Менеджер логов (окно + SLF4J)
            LogManager logManager = new LogManager();

            // GUI с тремя кнопками
            VideoDownloaderUI ui = new VideoDownloaderUI(logManager);
            ui.setVisible(true);

            // Первая запись в лог
            logManager.appendLog("Application started (QRNG-only GUI)");
        });
    }
}
