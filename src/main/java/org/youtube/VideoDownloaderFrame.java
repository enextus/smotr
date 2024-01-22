package org.youtube;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.List;


/**
 * VideoDownloaderFrame is a GUI class for downloading videos from YouTube.
 * It provides a simple interface for entering a video URL, and buttons to download the video
 * and clear the entered URL.
 *
 * VideoDownloaderFrame - это класс графического интерфейса пользователя для скачивания видео с YouTube.
 * Он предоставляет простой интерфейс для ввода URL видео и кнопки для скачивания видео и очистки введенного URL.
 */
public class VideoDownloaderFrame extends JFrame {
    private static final String OUTPUT_PATH = "C:/Users/Administrator/Desktop/downloadVideo/";
    public static final String ERROR_CHECK_OUTPUT = "Error: Check output";
    public static final String DOWNLOAD_COMPLETE = "Download complete";
    public static final String I_O_ERROR = "I/O Error: ";
    public static final String INTERRUPTED = "Interrupted: ";
    public static final String GENERAL_ERROR = "General Error: ";
    public static final String ERROR1 = "ERROR";
    public static final String INVALID_URL_PLEASE_CHECK_AND_TRY_AGAIN = "Invalid URL. Please check and try again.";
    public static final String PLEASE_ENTER_A_URL = "Please enter a URL.";
    public static final String ERROR2 = "Error";
    public static final String URL = "url: ";
    public static final String ERROR3 = "Error";
    public static final String ERROR_CREATING_DIRECTORY = "Error creating directory: ";
    public static final String COPY = "Copy";
    public static final String PASTE = "Paste";
    public static final String CUT = "Cut";
    public static final String URL1 = "url: ";
    public static final String YT_DLP = "yt-dlp";
    public static final String O = "-o";
    public static final String TITLE_S_EXT_S = "%(title)s.%(ext)s";
    public static final String ENTER_THE_VIDEO_URL = "Enter the video URL";
    public static final String CLEAR = "Clear";
    public static final String DOWNLOAD = "Download";
    public static final int WIDTH1 = 500;
    public static final int HEIGHT1 = 120;
    public static final String VIDEO_DOWNLOADER = "Video Downloader";
    private final JTextField urlField;
    private final JLabel infoLabel;

    /**
     * Constructor initializes the frame and components within it.
     * Конструктор инициализирует окно и компоненты внутри него.
     */
    public VideoDownloaderFrame() {
        JButton downloadButton;
        JButton clearButton;
        JButton openFolderButton; // Добавленная кнопка

        setTitle(VIDEO_DOWNLOADER);
        setSize(WIDTH1, HEIGHT1);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        urlField = new JTextField();

        createAndSetPopupMenu();

        downloadButton = new JButton(DOWNLOAD);
        // Добавление обработчика событий для кнопки "Скачать"
        downloadButton.addActionListener(e -> downloadVideo());

        clearButton = new JButton(CLEAR);
        clearButton.addActionListener(e -> urlField.setText(""));

        // Инициализируем и добавляем ActionListener к новой кнопке
        openFolderButton = new JButton("Открыть папку");
        // Инициализация и добавление обработчика событий для новой кнопки "Открыть папку"
        openFolderButton.addActionListener(e -> {
            // Попытка открыть папку с использованием класса Desktop
            try {
                Desktop.getDesktop().open(new File(OUTPUT_PATH));
            } catch (IOException ioException) {
                ioException.printStackTrace(); // Или отобразите диалоговое окно с ошибкой
            }
        });

        infoLabel = new JLabel(ENTER_THE_VIDEO_URL);

        JPanel eastPanel = new JPanel();
        eastPanel.setLayout(new GridLayout(3, 1)); // Обновлено количество строк в GridLayout
        eastPanel.add(downloadButton);
        eastPanel.add(clearButton);
        eastPanel.add(openFolderButton); // Добавляем новую кнопку на панель

        panel.add(urlField, BorderLayout.CENTER);
        panel.add(eastPanel, BorderLayout.EAST);
        panel.add(infoLabel, BorderLayout.SOUTH);

        add(panel);

        // Set focus on the input field after display
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent evt) {
                urlField.requestFocusInWindow();
            }
        });
    }

    /**
     * Creates a popup menu specifically for the URL field.
     *
     * @return the popup menu with the "Paste" option
     */
    private JPopupMenu createPopupMenuForUrlField() {
        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem pasteMenuItem = new JMenuItem("Paste");
        pasteMenuItem.addActionListener(e -> {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            DataFlavor flavor = DataFlavor.stringFlavor;
            if (clipboard.isDataFlavorAvailable(flavor)) {
                try {
                    String clipboardText = (String) clipboard.getData(flavor);
                    urlField.setText(clipboardText);
                } catch (UnsupportedFlavorException | IOException ex) {
                    ex.printStackTrace();
                }
            }
        });

        popupMenu.add(pasteMenuItem);
        return popupMenu;
    }

    /**
     * Checks if a given URL is valid.
     *
     * @param url the URL to be checked
     * @return true if the URL is valid, false otherwise
     */
    public static boolean isValidURL(String url) {
        if (url == null) return false;

        try {
            URI uri = new URI(url);
            uri.parseServerAuthority();
            String scheme = uri.getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Creates and sets the popup menu for the URL field.
     */
    private void createAndSetPopupMenu() {
        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem copy = new JMenuItem(COPY);
        JMenuItem paste = new JMenuItem(PASTE);
        JMenuItem cut = new JMenuItem(CUT);

        copy.addActionListener(e -> urlField.copy());
        paste.addActionListener(e -> urlField.paste());
        cut.addActionListener(e -> urlField.cut());

        popupMenu.add(copy);
        popupMenu.add(paste);
        popupMenu.add(cut);

        urlField.setComponentPopupMenu(popupMenu);
    }

    /**
     * Downloads the video from the given URL.
     * Handles all the logic and error cases for downloading a video.
     *
     * Скачивает видео по заданному URL.
     * Управляет всей логикой и обработкой ошибок при скачивании видео.
     */
    private void downloadVideo() {
        // Проверка и создание каталога для скачивания видео
        Path outputPath = Paths.get(OUTPUT_PATH);
        if (!Files.exists(outputPath)) {
            try {
                Files.createDirectories(outputPath);
            } catch (IOException e) {
                infoLabel.setText(ERROR_CREATING_DIRECTORY + e.getMessage());
                // Прерывание выполнения, если каталог не может быть создан
                // Terminate execution if the directory cannot be created
                return;
            }
        }

        String url = urlField.getText();
        System.out.println(URL + url);

        // Check for empty input
        // Проверка на пустой ввод URL
        if (url.isEmpty()) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(VideoDownloaderFrame.this,
                    PLEASE_ENTER_A_URL, ERROR2, JOptionPane.ERROR_MESSAGE));
            return;
        }

        System.out.println(URL1 + url);

        // Проверка на валидность URL
        if (!isValidURL(url)) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(VideoDownloaderFrame.this,
                    INVALID_URL_PLEASE_CHECK_AND_TRY_AGAIN, ERROR3, JOptionPane.ERROR_MESSAGE));
            return;
        }

        // Формирование команды для вызова внешней утилиты yt-dlp
        List<String> command = new ArrayList<>();
        command.add(YT_DLP);
        command.add(url);
        command.add(O);
        command.add(OUTPUT_PATH + TITLE_S_EXT_S);

        // Запуск процесса скачивания видео
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        // Обработка потенциальных исключений во время скачивания
        try {
            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                process.waitFor();
                if (output.toString().contains(ERROR1)) {
                    infoLabel.setText(ERROR_CHECK_OUTPUT);
                    System.out.println(output);
                } else {
                    infoLabel.setText(DOWNLOAD_COMPLETE);
                }
            }
        } catch (IOException ex) {
            infoLabel.setText(I_O_ERROR + ex.getMessage());
            // Additional handling or logging of IOException
        } catch (InterruptedException ex) {
            infoLabel.setText(INTERRUPTED + ex.getMessage());
            // Additional handling or logging of InterruptedException
        } catch (Exception ex) {
            infoLabel.setText(GENERAL_ERROR + ex.getMessage());
            // Additional handling or logging of the general exception
        }

    }

}
