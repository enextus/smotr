package org.videodownloader;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
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
    private static final int HEIGHT = 250;

    private JTextField urlField;
    private JLabel statusLabel;
    private JButton downloadButton;
    private JButton clearButton;

    private final VideoDownloadManager downloadManager;
    private final LogManager logManager;
    private final App.DownloadListener listener;

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
        statusLabel = new JLabel("Enter the video URL");

        // Контекстное меню (добавим позже Clear-экшен, чтобы reuse)
        JPopupMenu popupMenu = buildPopupMenu();
        urlField.setComponentPopupMenu(popupMenu);

        inputPanel.add(urlField, BorderLayout.CENTER);
        inputPanel.add(statusLabel, BorderLayout.SOUTH);

        // Панель кнопок (5 кнопок в вертикальной сетке)
        JPanel buttonPanel = new JPanel(new GridLayout(5, 1, 5, 5));
        downloadButton = new JButton("Download");
        clearButton = new JButton("Clear");
        JButton openFolderButton = new JButton("Open Folder");
        JButton selectFolderButton = new JButton("Select Download Folder");
        JButton showLogsButton = new JButton("Show Logs");

        buttonPanel.add(downloadButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(openFolderButton);
        buttonPanel.add(selectFolderButton);
        buttonPanel.add(showLogsButton);

        // Добавление компонентов в окно
        add(inputPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.EAST);

        // --- Экшен очистки, хоткеи, enable/disable логика ---
        Action clearUrlAction = new AbstractAction("Clear") {
            @Override
            public void actionPerformed(ActionEvent e) {
                urlField.setText("");
                setStatus("Enter the video URL");
                logManager.appendLog("URL field cleared");
                urlField.requestFocusInWindow();
            }
        };
        clearButton.setAction(clearUrlAction);
        clearButton.setToolTipText("Очистить поле URL (Esc / Ctrl+L)");

        // Обновляем контекстное меню, добавив пункт Clear (reuse экшена)
        addClearToPopup(urlField.getComponentPopupMenu(), clearUrlAction);

        // Хоткеи: Esc на поле; Ctrl+L глобально
        urlField.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke("ESCAPE"), "clear-url");
        urlField.getActionMap().put("clear-url", clearUrlAction);

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("control L"), "clear-url-global");
        getRootPane().getActionMap().put("clear-url-global", clearUrlAction);

        // Триггеры включения/выключения Clear и Download
        DocumentListener toggleButtons = new DocumentListener() {
            private void toggle() {
                boolean hasText = !urlField.getText().isBlank();
                clearButton.setEnabled(hasText);
                downloadButton.setEnabled(hasText);
            }
            @Override public void insertUpdate(DocumentEvent e) { toggle(); }
            @Override public void removeUpdate(DocumentEvent e) { toggle(); }
            @Override public void changedUpdate(DocumentEvent e) { toggle(); }
        };
        urlField.getDocument().addDocumentListener(toggleButtons);
        // начальное состояние
        clearButton.setEnabled(false);
        downloadButton.setEnabled(false);

        // --- Обработчики событий ---
        downloadButton.addActionListener(e -> {
            String url = urlField.getText().trim();
            if (url.isEmpty()) {
                setStatus("Please enter a URL");
                logManager.appendLog("Error: URL is empty");
                return;
            }
            // Валидация URL (поддерживаем только http/https)
            if (!VideoDownloaderFrame.isValidURL(url)) {
                setStatus("Invalid URL");
                logManager.appendLog("Error: Invalid URL: " + url);
                return;
            }
            setStatus("Starting download...");
            downloadManager.downloadVideo(url, listener);
        });

        // Кнопка уже привязана к clearUrlAction — отдельный listener не нужен.

        openFolderButton.addActionListener(e -> {
            try {
                String path = downloadManager.getSelectedOutputPath();
                if (path == null || path.isBlank()) {
                    path = DEFAULT_OUTPUT_PATH;
                }
                Desktop.getDesktop().open(new File(path));
                setStatus("Folder opened: " + path);
                logManager.appendLog("Folder opened: " + path);
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
     * Базовое контекстное меню без Clear (добавим позже, когда будет Action).
     */
    private JPopupMenu buildPopupMenu() {
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem copy = new JMenuItem("Copy");
        JMenuItem paste = new JMenuItem("Paste");
        JMenuItem cut = new JMenuItem("Cut");

        addActionListenerHelper(popupMenu, copy, paste, cut, urlField);
        popupMenu.addSeparator(); // место для Clear

        return popupMenu;
    }

    static void addActionListenerHelper(JPopupMenu popupMenu, JMenuItem copy, JMenuItem paste, JMenuItem cut, JTextField urlField) {
        copy.addActionListener(e -> urlField.copy());
        paste.addActionListener(e -> urlField.paste());
        cut.addActionListener(e -> urlField.cut());

        popupMenu.add(copy);
        popupMenu.add(paste);
        popupMenu.add(cut);
    }

    /**
     * Добавляет пункт Clear в уже созданное контекстное меню, реиспользуя Action.
     */
    private void addClearToPopup(JPopupMenu popup, Action clearUrlAction) {
        if (popup == null) return;
        popup.add(new JMenuItem(clearUrlAction));
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
     * Обновляет текст статуса в интерфейсе.
     */
    public void setStatus(String status) {
        statusLabel.setText(status);
    }


}
