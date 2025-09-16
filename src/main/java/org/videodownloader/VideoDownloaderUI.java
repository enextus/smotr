package org.videodownloader;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;


/**
 * Класс для создания и управления графическим интерфейсом приложения.
 * Предоставляет поле для ввода URL, кнопки управления и отображение статуса загрузки.
 * Поддерживает динамическую установку слушателя для обновления статуса.
 */
public class VideoDownloaderUI extends JFrame {
    private static final String DEFAULT_OUTPUT_PATH = "C:/Videos_Download/";
    private static final int WIDTH = 700;
    private static final int HEIGHT = 600;
    private JTextField urlField;
    private JLabel statusLabel;
    private JButton downloadButton;
    private JButton clearButton;
    private JButton openFolderButton;
    private JButton selectFolderButton;
    private JButton showLogsButton;
    private final VideoDownloadManager downloadManager;
    private final LogManager logManager;
    private App.DownloadListener listener;

    /**
     * Конструктор класса VideoDownloaderUI.
     *
     * @param downloadManager менеджер загрузок
     * @param logManager      менеджер логов
     * @param listener        слушатель для обновления статуса (может быть null)
     */
    public VideoDownloaderUI(VideoDownloadManager downloadManager, LogManager logManager, App.DownloadListener listener) {
        this.downloadManager = downloadManager;
        this.logManager = logManager;
        this.listener = listener;
        initializeUI();
    }

    /**
     * Инициализирует компоненты графического интерфейса.
     * Настраивает окно, панели, кнопки, контекстное меню и обработчики событий.
     */
    private void initializeUI() {
        // Настройка основного окна
        setTitle("Video Downloader");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(WIDTH, HEIGHT);

        // Центр по монитору под курсором (а не по main-монитору):
        setLocationByPlatform(false);
        MonitorUtils.moveToMouseScreen(this);

        setLayout(new BorderLayout());

        // Панель ввода URL и статуса
        JPanel inputPanel = new JPanel(new BorderLayout());
        urlField = new JTextField(30);
        createAndSetPopupMenu();
        statusLabel = new JLabel("Enter the video URL");
        inputPanel.add(urlField, BorderLayout.CENTER);
        inputPanel.add(statusLabel, BorderLayout.SOUTH);

        // Панель кнопок (5 кнопок в вертикальной сетке)
        JPanel buttonPanel = new JPanel(new GridLayout(5, 1, 5, 5));
        downloadButton = new JButton("Download");
        clearButton = new JButton("Clear");
        openFolderButton = new JButton("Open Folder");
        selectFolderButton = new JButton("Select Download Folder");
        showLogsButton = new JButton("Show Logs");

        buttonPanel.add(downloadButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(openFolderButton);
        buttonPanel.add(selectFolderButton);
        buttonPanel.add(showLogsButton);

        // Добавление компонентов в окно
        add(inputPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.EAST);

        // Обработчики событий
        downloadButton.addActionListener(e -> {
            String url = urlField.getText().trim();
            if (url.isEmpty()) {
                setStatus("Please enter a URL");
                logManager.appendLog("Error: URL is empty");
                return;
            }
            downloadManager.downloadVideo(url, listener);
            setStatus("Starting download...");
        });

        clearButton.addActionListener(e -> {
            urlField.setText("");
            setStatus("Enter the video URL");
            logManager.appendLog("URL field cleared");
        });

        openFolderButton.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(new File(downloadManager.getSelectedOutputPath()));
                setStatus("Folder opened: " + downloadManager.getSelectedOutputPath());
                logManager.appendLog("Folder opened: " + downloadManager.getSelectedOutputPath());
            } catch (IOException ex) {
                setStatus("Error opening folder: " + ex.getMessage());
                logManager.appendLog("Error opening folder: " + ex.getMessage());
            }
        });

        selectFolderButton.addActionListener(e -> selectDownloadFolder());

        showLogsButton.addActionListener(e -> {
            logManager.showLogWindow();
            logManager.appendLog("Log window opened");
        });

        // Установка фокуса на поле URL при открытии окна
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent evt) {
                urlField.requestFocusInWindow();
            }
        });

        // Не центрируем по default screen — позиция уже выставлена по мыши.
    }

    /**
     * Создаёт и прикрепляет контекстное меню к полю URL.
     * Поддерживает операции Copy, Paste, Cut.
     */
    private void createAndSetPopupMenu() {
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem copy = new JMenuItem("Copy");
        JMenuItem paste = new JMenuItem("Paste");
        JMenuItem cut = new JMenuItem("Cut");

        copy.addActionListener(e -> urlField.copy());
        paste.addActionListener(e -> urlField.paste());
        cut.addActionListener(e -> urlField.cut());

        popupMenu.add(copy);
        popupMenu.add(paste);
        popupMenu.add(cut);

        urlField.setComponentPopupMenu(popupMenu);
    }

    /**
     * Открывает диалог выбора папки для загрузки.
     * Обновляет путь в downloadManager и отображает статус.
     */
    private void selectDownloadFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            String newPath = chooser.getSelectedFile().getAbsolutePath() + "/";
            downloadManager.setSelectedOutputPath(newPath);
            setStatus("Selected folder: " + newPath);
            logManager.appendLog("Selected download folder: " + newPath);
        }
    }

    /**
     * Обновляет текст статуса в интерфейсе и уведомляет слушателя.
     *
     * @param status новое сообщение статуса
     */
    public void setStatus(String status) {
        statusLabel.setText(status);
    }

    /**
     * Устанавливает слушатель для обновления статуса.
     * Используется для динамической привязки слушателя после создания UI.
     *
     * @param listener новый слушатель
     */
    public void setDownloadListener(App.DownloadListener listener) {
        this.listener = listener;
    }

}
