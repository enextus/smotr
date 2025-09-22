package org.videodownloader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VideoDownloadManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(VideoDownloadManager.class);
    private static final String DEFAULT_OUTPUT_PATH = "C:/Videos_Download";

    private Path outputDir = Paths.get(DEFAULT_OUTPUT_PATH);
    private volatile Process currentProcess;

    private final AtomicReference<Path> lastSavedFile = new AtomicReference<>(null);

    // yt-dlp stdout patterns
    private static final Pattern YTDLP_DESTINATION =
            Pattern.compile("^\\[download\\] Destination: (.+)$");
    private static final Pattern YTDLP_ALREADY =
            Pattern.compile("^\\[download\\] (.+) has already been downloaded$");
    private static final Pattern YTDLP_MERGE =
            Pattern.compile("^\\[(?:Merger|ffmpeg)\\] Merging .*? into \"(.+)\"$");

    // формат времени для имени файла
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

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

                // сформируем «умное» базовое имя один раз
                String smartBase = buildSmartBaseName(url);
                boolean success = tryYtDlp(url, outputDir, smartBase);

                if (!success) {
                    publish("yt-dlp failed, trying direct download...");
                    String videoUrl = VideoExtractor.extractVideoUrl(url);
                    if (videoUrl != null) {
                        publish("Extracted video URL: " + videoUrl);
                        // на прямой URL попробуем тем же базовым именем
                        success = tryYtDlp(videoUrl, outputDir, smartBase);
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
    private boolean tryYtDlp(String videoUrl, Path dir, String smartBase) {
        lastSavedFile.set(null);
        ProcessBuilder processBuilder = getProcessBuilder(videoUrl, dir, smartBase);
        try {
            currentProcess = processBuilder.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(currentProcess.getInputStream(), StandardCharsets.UTF_8))) {
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

    /** Конструируем команду yt-dlp с нашим «умным» именем. */
    private ProcessBuilder getProcessBuilder(String videoUrl, Path dir, String smartBase) {
        // Мы задаём уже готовую «базу», расширение подставит yt-dlp.
        // В Windows избегаем обратных слешей в шаблоне — используем resolve и toString().
        String outTpl = dir.resolve(smartBase + ".%(ext)s").toString();

        ProcessBuilder pb = new ProcessBuilder(
                "yt-dlp",
                "--user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit(KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
                videoUrl,
                "-o", outTpl
        );
        pb.redirectErrorStream(true);
        LOGGER.debug("ProcessBuilder command: {}", pb.command());
        return pb;
    }

    /** Собрать «умную» базу имени файла. */
    private String buildSmartBaseName(String pageUrl) {
        String title = null;
        try {
            title = fetchPreferredTitle(pageUrl);
        } catch (Exception e) {
            LOGGER.debug("Title fetch failed: {}", e.toString());
        }

        if (title == null || title.isBlank()) {
            title = fallbackFromUrl(pageUrl);
        }

        String cleaned = sanitizeForFilename(title);
        if (cleaned.isBlank()) cleaned = "video";

        // ограничим разумную длину (оставляя место под время и uuid)
        if (cleaned.length() > 80) cleaned = cleaned.substring(0, 80).trim();

        String ts = LocalDateTime.now().format(TS_FMT);
        String uuid8 = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        // Итог: Title_YYYYMMDD_HHMMSS_UUID8
        String base = cleaned + "_" + ts + "_" + uuid8;

        // финальная страховка на предел длины имени (Windows ~255)
        if (base.length() > 180) {
            base = base.substring(0, 180);
        }
        return base;
    }

    /** Получаем «лучший» заголовок страницы: og:title → twitter:title → <title>. */
    private String fetchPreferredTitle(String url) throws IOException {
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118 Safari/537.36")
                .timeout(15000)
                .get();

        // 1) og:title
        Element og = doc.selectFirst("meta[property=og:title], meta[name=og:title]");
        if (og != null) {
            String content = og.attr("content");
            if (content != null && !content.isBlank()) return content;
        }

        // 2) twitter:title
        Element tw = doc.selectFirst("meta[name=twitter:title]");
        if (tw != null) {
            String content = tw.attr("content");
            if (content != null && !content.isBlank()) return content;
        }

        // 3) обычный title
        String title = doc.title();
        if (title != null && !title.isBlank()) {
            return title;
        }
        return null;
    }

    /** Фолбэк имя из URL (последний сегмент пути, без query/frag). */
    private String fallbackFromUrl(String url) {
        try {
            URI u = new URI(url);
            String path = u.getPath();
            if (path != null && !path.isBlank()) {
                String seg = path.substring(path.lastIndexOf('/') + 1);
                // иногда сегмент пуст — попробуем хост
                if (seg == null || seg.isBlank()) seg = u.getHost();
                // уберём расширение если есть
                int dot = seg.lastIndexOf('.');
                if (dot > 0) seg = seg.substring(0, dot);
                return URLDecoder.decode(seg, StandardCharsets.UTF_8);
            }
            return u.getHost() != null ? u.getHost() : "video";
        } catch (Exception e) {
            return "video";
        }
    }

    /** Санитизация строки под безопасное имя файла. */
    private String sanitizeForFilename(String s) {
        if (s == null) return "";
        String normalized = Normalizer.normalize(s, Normalizer.Form.NFKC);

        // заменим запрещённые/опасные символы
        String cleaned = normalized
                .replaceAll("[\\\\/:*?\"<>|]", " ")   // win-forbidden
                .replaceAll("[\\p{Cntrl}]", " ")      // control chars
                .replaceAll("\\s+", " ")              // collapse spaces
                .trim();

        // заменим пробелы на подчёркивания для стабильности
        cleaned = cleaned.replace(' ', '_');

        // ещё немного почистим «мусорные» последовательности
        cleaned = cleaned.replaceAll("_+", "_");
        cleaned = cleaned.replaceAll("^_+|_+$", "");

        return cleaned;
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
