package org.videodownloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
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
    private String selectedOutputPath;
    private Process currentProcess;

    /**
     * Конструктор класса VideoDownloadManager.
     * Инициализирует путь для загрузки по умолчанию.
     */
    public VideoDownloadManager() {
        this.selectedOutputPath = DEFAULT_OUTPUT_PATH;
        LOGGER.info("VideoDownloadManager initialized with output path: {}", selectedOutputPath);
    }

    /**
     * Запускает процесс загрузки видео.
     *
     * @param url     URL видео для загрузки
     * @param listener слушатель для обновления статуса
     */
    public void downloadVideo(String url, App.DownloadListener listener) {
        if (!isValidURL(url)) {
            listener.onStatusUpdate("Invalid URL: " + url);
            LOGGER.error("Invalid URL provided: {}", url);
            return;
        }

        // Проверяем и создаём папку для загрузки
        Path outputPath = Paths.get(selectedOutputPath);
        if (!Files.exists(outputPath)) {
            try {
                Files.createDirectories(outputPath);
                listener.onStatusUpdate("Created directory: " + outputPath);
                LOGGER.info("Created output directory: {}", outputPath);
            } catch (IOException e) {
                listener.onStatusUpdate("Error creating directory: " + e.getMessage());
                LOGGER.error("Failed to create directory: {}", outputPath, e);
                return;
            }
        }

        // Выполняем загрузку в фоновом потоке
        SwingWorker<Boolean, String> worker = new SwingWorker<Boolean, String>() {
            @Override
            protected Boolean doInBackground() {
                publish("Trying yt-dlp...");
                boolean success = tryYtDlp(url, outputPath);
                if (!success) {
                    publish("yt-dlp failed, trying direct download...");
                    String videoUrl = VideoExtractor.extractVideoUrl(url);
                    if (videoUrl != null) {
                        publish("Extracted video URL: " + videoUrl);
                        success = tryYtDlp(videoUrl, outputPath);
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
     * @param outputPath путь для сохранения
     * @return true, если загрузка успешна, иначе false
     */
    private boolean tryYtDlp(String videoUrl, Path outputPath) {
        ProcessBuilder processBuilder = getProcessBuilder(videoUrl, outputPath);
        try {
            currentProcess = processBuilder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(currentProcess.getInputStream()))) {
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
     *
     * @param videoUrl  URL видео
     * @param outputPath путь для сохранения
     * @return настроенный ProcessBuilder
     */
    private ProcessBuilder getProcessBuilder(String videoUrl, Path outputPath) {
        ProcessBuilder pb = new ProcessBuilder(
                "yt-dlp",
                "--user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
                videoUrl,
                "-o",
                outputPath.toString() + "/%(title)s.%(ext)s"
        );
        pb.redirectErrorStream(true);
        LOGGER.debug("ProcessBuilder command: {}", pb.command());
        return pb;
    }

    /**
     * Проверяет валидность URL.
     *
     * @param url URL для проверки
     * @return true, если URL валиден, иначе false
     */
    private boolean isValidURL(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
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

    /**
     * Отменяет текущую загрузку, если она выполняется.
     */
    public void cancelDownload() {
        if (currentProcess != null) {
            currentProcess.destroy();
            LOGGER.info("Download cancelled");
        }
    }

    /**
     * Возвращает текущий путь для загрузки.
     *
     * @return путь для сохранения файлов
     */
    public String getSelectedOutputPath() {
        return selectedOutputPath;
    }

    /**
     * Устанавливает новый путь для загрузки.
     *
     * @param selectedOutputPath новый путь
     */
    public void setSelectedOutputPath(String selectedOutputPath) {
        this.selectedOutputPath = selectedOutputPath;
        LOGGER.info("Output path updated to: {}", selectedOutputPath);
    }

}
