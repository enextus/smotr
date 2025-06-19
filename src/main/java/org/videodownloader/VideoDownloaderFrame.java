package org.videodownloader;

import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * VideoDownloaderFrame - это класс с графическим интерфейсом на основе Swing для загрузки видео с различных веб-сайтов.
 * Он предоставляет удобный интерфейс с текстовым полем для ввода URL видео и кнопками для запуска загрузки,
 * очистки ввода, выбора папки загрузки, открытия папки и отображения логов.
 */
public class VideoDownloaderFrame extends JFrame {
    // Путь по умолчанию для сохранения загруженных видео
    private static final String DEFAULT_OUTPUT_PATH = "C:/Videos_Download/";

    // Константы для сообщений пользователю и обработки ошибок
    public static final String DOWNLOAD_COMPLETE = "Download complete"; // Завершение загрузки
    public static final String INTERRUPTED = "Interrupted: "; // Прерывание процесса
    public static final String INVALID_URL_PLEASE_CHECK_AND_TRY_AGAIN = "Invalid URL. Please check and try again."; // Неверный URL
    public static final String PLEASE_ENTER_A_URL = "Please enter a URL."; // Пустой URL
    public static final String ERROR2 = "Error"; // Заголовок ошибки 2
    public static final String URL = "url: "; // Префикс для вывода URL
    public static final String ERROR3 = "Error"; // Заголовок ошибки 3
    public static final String ERROR_CREATING_DIRECTORY = "Error creating directory: "; // Ошибка создания папки
    public static final String COPY = "Copy"; // Копировать
    public static final String PASTE = "Paste"; // Вставить
    public static final String CUT = "Cut"; // Вырезать
    public static final String YT_DLP = "yt-dlp"; // Инструмент командной строки для загрузки видео
    public static final String O = "-o"; // Опция вывода для yt-dlp
    public static final String TITLE_S_EXT_S = "%(title)s.%(ext)s"; // Шаблон имени файла для yt-dlp
    public static final String ENTER_THE_VIDEO_URL = "Enter the video URL"; // Приглашение ввести URL
    public static final String CLEAR = "Clear"; // Очистить
    public static final String DOWNLOAD = "Download"; // Загрузить
    public static final String SELECT_DOWNLOAD_FOLDER = "Select Download Folder"; // Выбрать папку загрузки
    public static final String SHOW_LOGS = "Show Logs"; // Показать логи (новая константа)
    public static final int WIDTH1 = 800; // Ширина окна в пикселях
    public static final int HEIGHT1 = 200; // Высота окна в пикселях
    public static final String VIDEO_DOWNLOADER = "Video Downloader"; // Заголовок окна

    // Компоненты графического интерфейса
    private final JTextField urlField; // Текстовое поле для ввода URL видео
    private JLabel infoLabel = null; // Метка для отображения статусных сообщений
    private String selectedOutputPath = DEFAULT_OUTPUT_PATH; // Текущая папка загрузки
    private JFrame logFrame; // Окно для отображения логов (новое поле)
    private JTextArea logTextArea; // Текстовая область для логов (новое поле)

    /**
     * Конструктор для VideoDownloaderFrame. Инициализирует компоненты интерфейса и настраивает layout.
     */
    public VideoDownloaderFrame() {
        // Объявляем кнопки для действий: загрузка, очистка, открытие папки, выбор папки и показ логов
        JButton downloadButton;
        JButton clearButton;
        JButton openFolderButton;
        JButton selectFolderButton;
        JButton showLogsButton; // Новая кнопка для показа логов

        // Настройка свойств основного окна
        setTitle(VIDEO_DOWNLOADER); // Устанавливаем заголовок окна
        setSize(WIDTH1, HEIGHT1); // Устанавливаем размер окна
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // Закрытие приложения при закрытии окна
        setLocationRelativeTo(null); // Центрируем окно на экране

        // Создаем основную панель с BorderLayout
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        // Инициализируем текстовое поле URL и добавляем контекстное меню для копирования/вставки/вырезания
        urlField = new JTextField();
        createAndSetPopupMenu();

        // Инициализируем кнопку "Download" и определяем её действие
        downloadButton = new JButton(DOWNLOAD);
        downloadButton.addActionListener(e -> downloadVideo()); // Запускаем загрузку видео при нажатии

        // Инициализируем кнопку "Clear" и определяем её действие
        clearButton = new JButton(CLEAR);
        clearButton.addActionListener(e -> {
            urlField.setText(""); // Очищаем текстовое поле URL
            infoLabel.setText(ENTER_THE_VIDEO_URL); // Сбрасываем статусное сообщение
        });

        // Инициализируем кнопку "Открыть папку" и определяем её действие
        openFolderButton = new JButton("Открыть папку");
        openFolderButton.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(new File(selectedOutputPath)); // Открываем папку загрузки
            } catch (IOException ioException) {
                ioException.printStackTrace();
                infoLabel.setText("Ошибка открытия папки: " + ioException.getMessage()); // Показываем ошибку
            }
        });

        // Инициализируем кнопку "Select Download Folder" и определяем её действие
        selectFolderButton = new JButton(SELECT_DOWNLOAD_FOLDER);
        selectFolderButton.addActionListener(e -> selectDownloadFolder()); // Открываем выбор папки

        // Инициализируем кнопку "Show Logs" и определяем её действие
        showLogsButton = new JButton(SHOW_LOGS);
        showLogsButton.addActionListener(e -> showLogWindow()); // Открываем окно логов при нажатии

        // Инициализируем метку статуса
        infoLabel = new JLabel(ENTER_THE_VIDEO_URL);

        // Создаем панель для кнопок с сеткой 5x1 (добавляем одну строку для новой кнопки)
        JPanel eastPanel = new JPanel();
        eastPanel.setLayout(new GridLayout(5, 1)); // Изменяем с 4 на 5 строк
        eastPanel.add(downloadButton); // Добавляем кнопку "Download"
        eastPanel.add(clearButton); // Добавляем кнопку "Clear"
        eastPanel.add(openFolderButton); // Добавляем кнопку "Открыть папку"
        eastPanel.add(selectFolderButton); // Добавляем кнопку "Select Download Folder"
        eastPanel.add(showLogsButton); // Добавляем новую кнопку "Show Logs"

        // Добавляем компоненты на основную панель
        panel.add(urlField, BorderLayout.CENTER); // Поле URL в центре
        panel.add(eastPanel, BorderLayout.EAST); // Кнопки справа
        panel.add(infoLabel, BorderLayout.SOUTH); // Метка статуса внизу

        // Добавляем панель в окно
        add(panel);

        // Добавляем слушатель окна, чтобы фокус сразу устанавливался на поле URL
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent evt) {
                urlField.requestFocusInWindow(); // Устанавливаем фокус на поле URL при открытии
            }
        });

        // Создаем окно логов, но изначально оставляем его скрытым
        logFrame = new JFrame("Logs"); // Создаем новое окно с заголовком "Logs"
        logTextArea = new JTextArea(50, 50); // Создаем текстовую область размером 20 строк и 50 символов
        logTextArea.setEditable(false); // Делаем текстовую область только для чтения
        logFrame.add(new JScrollPane(logTextArea)); // Добавляем текстовую область с прокруткой
        logFrame.setSize(800, 600); // Устанавливаем размер окна логов
        logFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE); // Скрываем окно при закрытии, а не завершаем приложение

        // Перенаправляем вывод консоли в текстовую область и инициализируем начальное содержимое

        initializeLogContent(); // Заполняем начальные данные логов
    }

    /**
     * Инициализирует содержимое окна логов начальными данными.
     * Здесь можно добавить любую стартовую информацию, например, командную строку запуска.
     */
    private void initializeLogContent() {
        String initialLog = "Программа запущена.\n" +
                "Папка загрузки по умолчанию: " + DEFAULT_OUTPUT_PATH + "\n" +
                "Введите URL видео для начала загрузки.\n";
        logTextArea.append(initialLog); // Добавляем начальное сообщение в лог
    }

    /**
     * Отображает окно логов при нажатии на кнопку "Show Logs".
     */
    private void showLogWindow() {
        logFrame.setVisible(true); // Делаем окно логов видимым
    }

    /**
     * Открывает диалог выбора папки для загрузки видео.
     */
    private void selectDownloadFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); // Ограничиваем выбор только папками
        int result = chooser.showOpenDialog(this); // Показываем диалог
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedOutputPath = chooser.getSelectedFile().getAbsolutePath() + "/"; // Обновляем путь
            infoLabel.setText("Выбрана папка загрузки: " + selectedOutputPath); // Обновляем статус
            logTextArea.append("Выбрана новая папка загрузки: " + selectedOutputPath + "\n"); // Логируем выбор
        }
    }

    /**
     * Создает и прикрепляет контекстное меню к полю URL с опциями "Copy", "Paste" и "Cut".
     */
    private void createAndSetPopupMenu() {
        JPopupMenu popupMenu = new JPopupMenu(); // Создаем контекстное меню
        JMenuItem copy = new JMenuItem(COPY); // Опция "Copy"
        JMenuItem paste = new JMenuItem(PASTE); // Опция "Paste"
        JMenuItem cut = new JMenuItem(CUT); // Опция "Cut"

        // Определяем действия для каждого пункта меню
        copy.addActionListener(e -> urlField.copy());
        paste.addActionListener(e -> urlField.paste());
        cut.addActionListener(e -> urlField.cut());

        // Добавляем пункты в меню
        popupMenu.add(copy);
        popupMenu.add(paste);
        popupMenu.add(cut);

        // Прикрепляем меню к полю URL
        urlField.setComponentPopupMenu(popupMenu);
    }

    /**
     * Запускает процесс загрузки видео с использованием yt-dlp или прямой загрузки при необходимости.
     */
    private void downloadVideo() {
        Path outputPath = Paths.get(selectedOutputPath); // Получаем путь к папке как Path
        if (!Files.exists(outputPath)) {
            try {
                Files.createDirectories(outputPath); // Создаем папку, если её нет
                logTextArea.append("Создана новая папка: " + outputPath + "\n"); // Логируем создание
            } catch (IOException e) {
                infoLabel.setText(ERROR_CREATING_DIRECTORY + e.getMessage()); // Показываем ошибку
                logTextArea.append("Ошибка создания папки: " + e.getMessage() + "\n"); // Логируем ошибку
                return;
            }
        }

        String url = urlField.getText().trim(); // Получаем и обрезаем введённый URL
        System.out.println(URL + url); // Логируем URL для отладки (теперь в logTextArea)

        // Проверяем, пустой ли URL
        if (url.isEmpty()) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(VideoDownloaderFrame.this,
                    PLEASE_ENTER_A_URL, ERROR2, JOptionPane.ERROR_MESSAGE)); // Показываем ошибку
            logTextArea.append("Ошибка: URL не введён.\n"); // Логируем ошибку
            return;
        }

        // Проверяем валидность URL
        if (!isValidURL(url)) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(VideoDownloaderFrame.this,
                    INVALID_URL_PLEASE_CHECK_AND_TRY_AGAIN, ERROR3, JOptionPane.ERROR_MESSAGE)); // Показываем ошибку
            logTextArea.append("Ошибка: Неверный URL - " + url + "\n"); // Логируем ошибку
            return;
        }

        // Пробуем загрузить с помощью yt-dlp
        boolean success = tryYtDlp(url, outputPath);
        if (!success) {
            String extractedUrl = extractVideoUrl(url); // Извлекаем URL видео, если yt-dlp не сработал
            if (extractedUrl != null) {
                logTextArea.append("Извлечён прямой URL: " + extractedUrl + "\n"); // Логируем извлечённый URL
                success = tryYtDlp(extractedUrl, outputPath); // Повторяем попытку с извлечённым URL
                if (!success) {
                    downloadDirectly(extractedUrl, outputPath); // Переходим к прямой загрузке
                }
            } else {
                infoLabel.setText("Видео не найдено на странице"); // Сообщение об ошибке
                logTextArea.append("Видео не найдено на странице: " + url + "\n"); // Логируем ошибку
            }
        } else {
            infoLabel.setText(DOWNLOAD_COMPLETE); // Сообщение об успехе
            logTextArea.append("Загрузка завершена успешно.\n"); // Логируем успех
            System.exit(0); // Завершаем программу после успешной загрузки
        }
    }

    /**
     * Пытается загрузить видео с помощью инструмента yt-dlp.
     *
     * @param videoUrl   URL видео для загрузки.
     * @param outputPath Папка, куда будет сохранено видео.
     * @return True, если загрузка успешна, иначе False.
     */
    private boolean tryYtDlp(String videoUrl, Path outputPath) {
        ProcessBuilder processBuilder = getProcessBuilder(videoUrl, outputPath);

        try {
            Process process = processBuilder.start(); // Запускаем процесс
            StringBuilder output = new StringBuilder(); // Храним вывод процесса
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n"); // Читаем вывод построчно
                    logTextArea.append("yt-dlp: " + line + "\n"); // Логируем каждую строку вывода
                }
            }
            int exitCode = process.waitFor(); // Ждём завершения процесса
            if (exitCode == 0) {
                logTextArea.append("yt-dlp завершил загрузку с кодом 0.\n"); // Логируем успех
                return true; // Успех
            } else {
                infoLabel.setText("yt-dlp завершился с ошибкой " + exitCode + ": " + output); // Показываем ошибку
                logTextArea.append("yt-dlp завершился с ошибкой " + exitCode + ": " + output + "\n"); // Логируем ошибку
                return false;
            }
        } catch (IOException ex) {
            // Обрабатываем ошибки, связанные с отсутствием yt-dlp
            if (ex.getMessage().contains("No such file or directory") || ex.getMessage().contains("cannot find the file")) {
                infoLabel.setText("yt-dlp не найден. Пожалуйста, установите yt-dlp.");
                logTextArea.append("Ошибка: yt-dlp не найден.\n"); // Логируем ошибку
            } else {
                infoLabel.setText("Ошибка запуска yt-dlp: " + ex.getMessage());
                logTextArea.append("Ошибка запуска yt-dlp: " + ex.getMessage() + "\n"); // Логируем ошибку
            }
            return false;
        } catch (InterruptedException ex) {
            infoLabel.setText(INTERRUPTED + ex.getMessage()); // Обрабатываем прерывание
            logTextArea.append("Прерывание yt-dlp: " + ex.getMessage() + "\n"); // Логируем прерывание
            return false;
        }
    }

    /**
     * Создаёт ProcessBuilder для выполнения команды yt-dlp.
     *
     * @param videoUrl   URL видео.
     * @param outputPath Путь для сохранения.
     * @return Настроенный ProcessBuilder.
     */
    private static @NotNull ProcessBuilder getProcessBuilder(String videoUrl, Path outputPath) {
        List<String> command = new ArrayList<>(); // Создаём команду для yt-dlp
        command.add(YT_DLP); // Исполняемый файл
        command.add("--user-agent"); // Устанавливаем user-agent для имитации браузера
        command.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        command.add(videoUrl); // Добавляем URL видео
        command.add(O); // Опция вывода
        command.add(outputPath.toString() + "/" + TITLE_S_EXT_S); // Путь и шаблон имени файла

        ProcessBuilder processBuilder = new ProcessBuilder(command); // Создаём процесс
        processBuilder.redirectErrorStream(true); // Объединяем потоки ошибок и вывода
        return processBuilder;
    }

    /**
     * Преобразует относительный URL в абсолютный, используя базовый URL.
     *
     * @param baseUrl     Базовый URL для разрешения.
     * @param relativeUrl Относительный URL для преобразования.
     * @return Абсолютный URL как строка.
     */
    private String makeAbsoluteUrl(String baseUrl, String relativeUrl) {
        try {
            URI baseUri = new URI(baseUrl);
            URI absoluteUri = baseUri.resolve(relativeUrl);
            logTextArea.append("Преобразован URL: " + relativeUrl + " -> " + absoluteUri.toString() + "\n"); // Логируем
            return absoluteUri.toString();
        } catch (URISyntaxException e) {
            infoLabel.setText("Ошибка при преобразовании URL: " + e.getMessage());
            logTextArea.append("Ошибка преобразования URL: " + e.getMessage() + "\n"); // Логируем ошибку
            return relativeUrl;
        }
    }

    /**
     * Извлекает URL видео из страницы.
     *
     * @param pageUrl URL страницы.
     * @return Извлечённый URL видео или null.
     */
    private String extractVideoUrl(String pageUrl) {
        return VideoExtractor.extractVideoUrl(pageUrl);
    }

    /**
     * Рекурсивно ищет URL видео в документе, включая iframes.
     *
     * @param baseUrl Базовый URL для разрешения относительных ссылок.
     * @param doc     HTML-документ для поиска.
     * @param depth   Текущая глубина рекурсии (максимум 2 для предотвращения бесконечных циклов).
     * @return URL видео, если найден, иначе null.
     */
    private String extractVideoUrlRecursive(String baseUrl, Document doc, int depth) {
        if (depth > 2) { // Ограничиваем глубину рекурсии
            logTextArea.append("Достигнута максимальная глубина рекурсии: " + depth + "\n"); // Логируем
            return null;
        }

        // Ищем теги <video>
        Elements videos = doc.select("video");
        for (Element video : videos) {
            String src = video.attr("src"); // Проверяем атрибут src
            if (src != null && !src.isEmpty()) {
                logTextArea.append("Найден URL в теге <video>: " + src + "\n"); // Логируем
                return makeAbsoluteUrl(baseUrl, src); // Возвращаем абсолютный URL
            }
            Elements sources = video.select("source"); // Проверяем теги <source> внутри <video>
            for (Element source : sources) {
                src = source.attr("src");
                if (src != null && !src.isEmpty()) {
                    logTextArea.append("Найден URL в теге <source>: " + src + "\n"); // Логируем
                    return makeAbsoluteUrl(baseUrl, src);
                }
            }
        }

        // Ищем iframes и рекурсивно обрабатываем их
        Elements iframes = doc.select("iframe");
        for (Element iframe : iframes) {
            String iframeSrc = iframe.attr("src");
            if (iframeSrc != null && !iframeSrc.isEmpty()) {
                String iframeUrl = makeAbsoluteUrl(baseUrl, iframeSrc); // Разрешаем URL iframe
                logTextArea.append("Обработка iframe: " + iframeUrl + "\n"); // Логируем
                try {
                    Document iframeDoc = Jsoup.connect(iframeUrl)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                            .get();
                    String result = extractVideoUrlRecursive(iframeUrl, iframeDoc, depth + 1); // Рекурсия
                    if (result != null) {
                        return result;
                    }
                } catch (IOException e) {
                    System.out.println("Ошибка загрузки iframe: " + iframeUrl + " - " + e.getMessage()); // Логируем ошибку
                    // Продолжаем с следующим iframe
                }
            }
        }

        // Ищем мета-теги с URL видео (например, og:video)
        Elements metaVideos = doc.select("meta[property=og:video]");
        for (Element meta : metaVideos) {
            String content = meta.attr("content");
            if (content != null && !content.isEmpty()) {
                logTextArea.append("Найден URL в meta og:video: " + content + "\n"); // Логируем
                return makeAbsoluteUrl(baseUrl, content);
            }
        }

        logTextArea.append("URL видео не найден на глубине " + depth + "\n"); // Логируем отсутствие результата
        return null; // URL видео не найден
    }

    /**
     * Загружает видео напрямую с помощью HTTP-запросов и Selenium.
     *
     * @param videoUrl   URL видео для загрузки.
     * @param outputPath Папка для сохранения видео.
     */
    private void downloadDirectly(String videoUrl, Path outputPath) {
        try {
            // Указываем путь к ChromeDriver
            System.setProperty("webdriver.chrome.driver", "C:/opt/chromedriver/chromedriver.exe");

            // Настраиваем опции для Chrome
            ChromeOptions options = new ChromeOptions();
            // Браузер будет виден (не в headless-режиме)

            // Создаём экземпляр ChromeDriver
            ChromeDriver driver = new ChromeDriver(options);

            // Открываем URL видео в новом окне
            driver.get(videoUrl);
            logTextArea.append("Открыт браузер с URL: " + videoUrl + "\n"); // Логируем открытие

            // Разворачиваем окно на весь экран (опционально)
            driver.manage().window().maximize();

            // Браузер остаётся открытым для ручной загрузки пользователем
        } catch (Exception e) {
            infoLabel.setText("Ошибка при открытии URL: " + e.getMessage());
            logTextArea.append("Ошибка открытия URL в браузере: " + e.getMessage() + "\n"); // Логируем ошибку
        }
    }

    /**
     * Проверяет, является ли строка валидным URL с протоколом HTTP или HTTPS.
     *
     * @param url URL для проверки.
     * @return True, если URL валиден, иначе False.
     */
    public static boolean isValidURL(String url) {
        if (url == null) return false; // Нулевые URL недопустимы
        try {
            URI uri = new URI(url); // Парсим URL
            uri.parseServerAuthority(); // Проверяем авторитет сервера
            String scheme = uri.getScheme(); // Получаем протокол (http/https)
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme); // Проверяем схему
        } catch (URISyntaxException e) {
            return false; // Неверный синтаксис URL
        }
    }

}
