package org.videodownloader;

import javax.swing.SwingUtilities;

/**
 * Главный класс приложения, отвечающий за запуск программы.
 * Инициализирует основные компоненты (UI, менеджер загрузок, менеджер логов)
 * и связывает их для обеспечения работы приложения.
 */
public class App {

    /**
     * Точка входа в приложение.
     * Запускает графический интерфейс в потоке EDT (Event Dispatch Thread) для
     * корректной работы Swing.
     *
     * @param args аргументы командной строки (не используются)
     */
    public static void main(String[] args) {
        // Запускаем приложение в потоке EDT для безопасной работы с Swing
        SwingUtilities.invokeLater(() -> {
            // Создаём экземпляр менеджера загрузок
            VideoDownloadManager downloadManager = new VideoDownloadManager();

            // Создаём экземпляр менеджера логов
            LogManager logManager = new LogManager();

            // Создаём UI без слушателя, чтобы избежать циклической зависимости
            VideoDownloaderUI ui = new VideoDownloaderUI(downloadManager, logManager, null);

            // Определяем слушатель для обратной связи о статусе загрузки
            DownloadListener listener = status -> {
                // Теперь ui доступна, так как определена выше
                ui.setStatus(status);
                logManager.appendLog(status);
            };

            // Устанавливаем слушатель в UI после его создания
            ui.setDownloadListener(listener);

            // Делаем окно видимым
            ui.setVisible(true);

            // Логируем запуск приложения
            logManager.appendLog("Application started successfully");
        });
    }

    /**
     * Интерфейс для обработки обновлений статуса загрузки.
     * Используется для передачи информации от VideoDownloadManager в UI и LogManager.
     */
    public interface DownloadListener {
        /**
         * Вызывается при обновлении статуса загрузки.
         *
         * @param status сообщение о текущем статусе
         */
        void onStatusUpdate(String status);
    }

}
