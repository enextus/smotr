package org.videodownloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VideoDownloadManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(VideoDownloadManager.class);
    private static final String DEFAULT_OUTPUT_PATH = "C:/Videos_Download";

    // активная папка загрузки как Path
    private Path outputDir = Paths.get(DEFAULT_OUTPUT_PATH);

    // текущий процесс yt-dlp
    private volatile Process currentProcess;

    // куда в итоге сохранили (парсим из вывода yt-dlp)
    private final AtomicReference<Path> lastSavedFile = new AtomicReference<>(null);

    // паттерны под разные строки yt-dlp
    private static final Pattern YTDLP_DESTINATION =
            Pattern.compile("^\\[download\\] Destination: (.+)$");
    private static final Pattern YTDLP_ALREADY =
            Pattern.compile("^\\[download\\] (.+) has already been downloaded$");
    private static final Pattern YTDLP_MERGE =
            Pattern.compile("^\\[(?:Merger|ffmpeg)\\] Merging .*? into \"(.+)\"$");

    public VideoDownloadManager() {
        LOGGER.info("VideoDownloadManager initialized. Default download folder: {}",
                outputDir.toAbsolutePath().normalize());
    }

    /** Для UI — вернуть путь как строку. */
    public String getSelectedOutputPath() {
        return outputDir.toAbsolutePath().normalize().toString();
    }

    /** Установить новую папку (создаём при необходимости). */
    public void setSelectedOutputPath(String newPath) {
        Path p = Paths.get(newPath);
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create directory: " + p, e);
        }
        this.outputDir = p;
        LOGGER.info("Output path updated to: {}", outputDir.toAbsolutePath().normalize());
    }

    /** Последний сохранённый файл (может быть null). */
    public Path getLastSavedFile() {
        return lastSavedFile.get();
    }

    /** Инициализация папки при старте: спросить/создать/выбрать. */
    public boolean initOutputDirOnStartup(java.awt.Component parent) {
        if (isUsableDirectory(outputDir)) {
            LOGGER.info("Using existing download folder: {}", getSelectedOutputPath());
            return true;
        }

        int choice = JOptionPane.showConfirmDialog(
                parent,
                "Папка для загрузок не найдена:\n" + getSelectedOutputPath() + "\nСоздать её?",
                "Папка загрузок",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        if (choice == JOptionPane.YES_OPTION) {
            try {
                Files.createDirectories(outputDir);
                if (isUsableDirectory(outputDir)) {
                    LOGGER.info("Created and using download folder: {}", getSelectedOutputPath());
                    return true;
                }
            } catch (IOException e) {
                LOGGER.error("Failed to create download folder: {}", outputDir, e);
                JOptionPane.showMessageDialog(parent,
                        "Не удалось создать папку:\n" + getSelectedOutputPath() + "\nОшибка: " + e.getMessage(),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        }

        // выбрать вручную
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
                    LOGGER.info("Using user-selected download folder: {}", getSelectedOutputPath());
                    return true;
                } else {
                    JOptionPane.showMessageDialog(parent,
                            "Нельзя записывать в выбранную папку:\n" + selected.toAbsolutePath().normalize(),
                            "Недостаточно прав", JOptionPane.WARNING_MESSAGE);
                }
            } catch (IOException e) {
                LOGGER.error("Cannot create selected folder: {}", selected, e);
                JOptionPane.showMessageDialog(parent,
                        "Не удалось создать папку:\n" + selected.toAbsolutePath().normalize() +
                                "\nОшибка: " + e.getMessage(),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /** Запустить загрузку. */
    public void downloadVideo(String url, App.DownloadListener listener) {
        if (!isValidURL(url)) {
            listener.onStatusUpdate("Invalid URL: " + url);
            LOGGER.error("Invalid URL provided: {}", url);
            return;
        }

        // убедимся, что папка есть
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            listener.onStatusUpdate("Error creating directory: " + e.getMessage());
            LOGGER.error("Failed to create directory: {}", outputDir, e);
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
                        Path saved = lastSavedFile.get();
                        if (saved != null) {
                            String full = saved.toAbsolutePath().normalize().toString();
                            listener.onStatusUpdate("Saved to: " + full);
                            LOGGER.info("Saved file: {}", full);
                        }
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

    /** Попытка запустить yt-dlp. */
    private boolean tryYtDlp(String videoUrl, Path dir) {
        lastSavedFile.set(null);
        ProcessBuilder processBuilder = getProcessBuilder(videoUrl, dir);
        try {
            currentProcess = processBuilder.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(currentProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    LOGGER.debug("yt-dlp output: {}", line);

                    Matcher m1 = YTDLP_DESTINATION.matcher(line);
                    if (m1.find()) { lastSavedFile.set(Paths.get(m1.group(1)).toAbsolutePath().normalize()); continue; }

                    Matcher m2 = YTDLP_ALREADY.matcher(line);
                    if (m2.find()) { lastSavedFile.set(Paths.get(m2.group(1)).toAbsolutePath().normalize()); continue; }

                    Matcher m3 = YTDLP_MERGE.matcher(line);
                    if (m3.find()) { lastSavedFile.set(Paths.get(m3.group(1)).toAbsolutePath().normalize()); }
                }
            }

            int exitCode = currentProcess.waitFor();
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
            if (currentProcess != null && currentProcess.isAlive()) {
                currentProcess.destroy();
            }
            currentProcess = null;
        }
    }

    /** Конструируем команду. */
    private ProcessBuilder getProcessBuilder(String videoUrl, Path dir) {
        // более устойчивый шаблон имени файла, без двойных .mp4
        String outTpl = dir.resolve("%(id)s-%(title).128s.%(ext)s").toString();

        ProcessBuilder pb = new ProcessBuilder(
                "yt-dlp",
                "--user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
                videoUrl,
                "-o", outTpl
        );
        pb.redirectErrorStream(true);
        LOGGER.debug("ProcessBuilder command: {}", pb.command());
        return pb;
    }

    /** Отмена загрузки. */
    public void cancelDownload() {
        Process p = currentProcess;
        if (p != null) {
            p.destroy();
            LOGGER.info("Download cancelled");
        }
    }

    private boolean isValidURL(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (URISyntaxException e) {
            LOGGER.debug("Invalid URL syntax: {}", url, e);
            return false;
        }
    }

    /** Проверка пригодности папки. */
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
