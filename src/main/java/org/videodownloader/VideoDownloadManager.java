package org.videodownloader;

import java.awt.Component;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingWorker;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Класс для управления логикой загрузки видео.
 * Поддерживает загрузку через yt-dlp и прямую загрузку с использованием VideoExtractor.
 * Выполняет операции асинхронно для предотвращения блокировки UI.
 */
public class VideoDownloadManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(VideoDownloadManager.class);
    private static final String DEFAULT_OUTPUT_PATH = "C:/Videos_Download/";

    /** Текущая папка для загрузки (единый источник истины) */
    private Path outputDir;

    private Process currentProcess;

    public VideoDownloadManager() {
        this.outputDir = Paths.get(DEFAULT_OUTPUT_PATH);
        LOGGER.info("VideoDownloadManager initialized. Default download folder: {}",
                outputDir.toAbsolutePath().normalize());
    }

    /** Текущий путь для загрузки (как строка, для UI) */
    public String getSelectedOutputPath() {
        return outputDir.toString();
    }

    /** Установить путь для загрузки (из настроек/диалога) */
    public void setSelectedOutputPath(String selectedOutputPath) {
        this.outputDir = Paths.get(selectedOutputPath);
        LOGGER.info("Output path updated to: {}", outputDir.toAbsolutePath().normalize());
    }

    /**
     * Запускает процесс загрузки видео.
     *
     * @param url      URL видео для загрузки
     * @param listener слушатель для обновления статуса
     */
    public void downloadVideo(String url, App.DownloadListener listener) {
        if (!isValidURL(url)) {
            listener.onStatusUpdate("Invalid URL: " + url);
            LOGGER.error("Invalid URL provided: {}", url);
            return;
        }

        // Страховка: папку могли удалить после старта — создадим (без диалога)
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            listener.onStatusUpdate("Error creating directory: " + e.getMessage());
            LOGGER.error("Failed to (re)create directory: {}", outputDir, e);
            return;
        }

        SwingWorker<Boolean, String> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                publish("Trying yt-dlp...");
                boolean success = tryYtDlp(url, outputDir);
                if (!success) {
                    publish("yt-dlp failed, trying direct download...");
                    String videoUrl = VideoExtractor.extractVideoUrl(url);
                    if (videoUrl != null) {
                        publish("Extracted video URL: " + videoUrl);
                        success = tryYtDlp(videoUrl, outputDir);
                        if (!success) {
                            publish("Direct download not implemented");
                            LOGGER.warn("Direct download not implemented for URL: {}", videoUrl);
                        }
                    } else {
                        publish("No video found on page");
                        LOGGER.warn("No video URL extracted for: {}", url);
                    }
                }
                return success;
            }

            @Override
            protected void process(List<String> chunks) {
                chunks.forEach(listener::onStatusUpdate);
            }

            @Override
            protected void done() {
                try {
                    if (get()) {
                        listener.onStatusUpdate("Download complete");
                    } else {
                        listener.onStatusUpdate("Download failed");
                    }
                } catch (Exception e) {
                    listener.onStatusUpdate("Error: " + e.getMessage());
                    LOGGER.error("Download error", e);
                }
            }
        };
        worker.execute();
    }

    /**
     * Пытается загрузить видео с помощью yt-dlp.
     *
     * @param videoUrl  URL видео
     * @param outputDir путь для сохранения
     * @return true, если загрузка успешна, иначе false
     */
    private boolean tryYtDlp(String videoUrl, Path outputDir) {
        ProcessBuilder processBuilder = getProcessBuilder(videoUrl, outputDir);
        try {
            currentProcess = processBuilder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(currentProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    LOGGER.debug("yt-dlp output: {}", line);
                }
            }
            int exitCode = currentProcess.waitFor();
            currentProcess = null;
            if (exitCode == 0) {
                LOGGER.info("yt-dlp download successful for URL: {}", videoUrl);
                return true;
            } else {
                LOGGER.warn("yt-dlp failed with exit code {}: {}", exitCode, output);
                return false;
            }
        } catch (IOException e) {
            LOGGER.error("Error running yt-dlp: {}", e.getMessage(), e);
            return false;
        } catch (InterruptedException e) {
            LOGGER.warn("yt-dlp interrupted: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            return false;
        } finally {
            currentProcess = null;
        }
    }

    /**
     * Создаёт ProcessBuilder для выполнения команды yt-dlp.
     */
    private ProcessBuilder getProcessBuilder(String videoUrl, Path outputDir) {
        ProcessBuilder pb = new ProcessBuilder(
                "yt-dlp",
                "--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
                videoUrl,
                "-o", outputDir.resolve("%(title)s.%(ext)s").toString()
        );
        pb.redirectErrorStream(true);
        LOGGER.debug("ProcessBuilder command: {}", pb.command());
        return pb;
    }

    /** Проверяет валидность URL. */
    private boolean isValidURL(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        try {
            URI uri = new URI(url);
            uri.parseServerAuthority();
            String scheme = uri.getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (URISyntaxException e) {
            LOGGER.debug("Invalid URL syntax: {}", url, e);
            return false;
        }
    }

    /** Отменяет текущую загрузку, если она выполняется. */
    public void cancelDownload() {
        if (currentProcess != null) {
            currentProcess.destroy();
            LOGGER.info("Download cancelled");
        }
    }

    // ====== Стартап-инициализация папки загрузок ======

    /**
     * Проверяет/инициализирует папку загрузки при старте приложения.
     * @param parent компонент-владелец для модальных диалогов (например, JFrame)
     * @return true — если папка готова к работе; false — если пользователь отказался/ошибка.
     */
    public boolean initOutputDirOnStartup(Component parent) {
        if (isUsableDirectory(outputDir)) {
            LOGGER.info("Using existing download folder: {}", outputDir.toAbsolutePath().normalize());
            return true;
        }

        int choice = JOptionPane.showConfirmDialog(
                parent,
                "Папка для загрузок не найдена:\n" + outputDir.toAbsolutePath().normalize() +
                        "\nСоздать её?",
                "Папка загрузок",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (choice == JOptionPane.YES_OPTION) {
            try {
                Files.createDirectories(outputDir);
                if (isUsableDirectory(outputDir)) {
                    LOGGER.info("Created and using download folder: {}", outputDir.toAbsolutePath().normalize());
                    return true;
                } else {
                    LOGGER.warn("Created but not usable: {}", outputDir.toAbsolutePath().normalize());
                }
            } catch (IOException e) {
                LOGGER.error("Failed to create download folder: {}", outputDir, e);
                JOptionPane.showMessageDialog(
                        parent,
                        "Не удалось создать папку:\n" + outputDir.toAbsolutePath().normalize() +
                                "\nОшибка: " + e.getMessage(),
                        "Ошибка",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }

        // Предложим выбрать другую папку
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Выберите папку для загрузок");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        while (true) {
            int res = chooser.showOpenDialog(parent);
            if (res != JFileChooser.APPROVE_OPTION) {
                LOGGER.warn("User canceled download folder selection");
                return false;
            }
            Path selected = chooser.getSelectedFile().toPath();
            try {
                Files.createDirectories(selected);
                if (isUsableDirectory(selected)) {
                    this.outputDir = selected;
                    LOGGER.info("Using user-selected download folder: {}", selected.toAbsolutePath().normalize());
                    return true;
                } else {
                    JOptionPane.showMessageDialog(
                            parent,
                            "Нельзя записывать в выбранную папку:\n" + selected.toAbsolutePath().normalize(),
                            "Недостаточно прав",
                            JOptionPane.WARNING_MESSAGE
                    );
                }
            } catch (IOException e) {
                LOGGER.error("Cannot create selected folder: {}", selected, e);
                JOptionPane.showMessageDialog(
                        parent,
                        "Не удалось создать папку:\n" + selected.toAbsolutePath().normalize() +
                                "\nОшибка: " + e.getMessage(),
                        "Ошибка",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    /** Годна ли папка: существует/директория/доступна для записи. */
    private boolean isUsableDirectory(Path dir) {
        try {
            if (!Files.exists(dir)) return false;
            if (!Files.isDirectory(dir)) return false;
            if (!Files.isWritable(dir)) return false;
            Path probe = dir.resolve(".write_probe.tmp");
            Files.writeString(probe, "ok");
            Files.deleteIfExists(probe);
            return true;
        } catch (IOException e) {
            LOGGER.debug("Directory usability check failed for {}: {}", dir, e.toString());
            return false;
        }
    }
}
