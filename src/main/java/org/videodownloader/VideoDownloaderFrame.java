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
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * VideoDownloaderFrame is a Swing-based GUI class for downloading videos from various websites.
 * It provides a user-friendly interface with a text field for entering a video URL and buttons
 * to initiate downloads, clear the input, select a download folder, or open the folder.
 */
public class VideoDownloaderFrame extends JFrame {
    // Default directory where downloaded videos will be saved
    private static final String DEFAULT_OUTPUT_PATH = "C:/Videos_Download/";

    // Constants for user-facing messages and error handling
    public static final String DOWNLOAD_COMPLETE = "Download complete";
    public static final String INTERRUPTED = "Interrupted: ";
    public static final String INVALID_URL_PLEASE_CHECK_AND_TRY_AGAIN = "Invalid URL. Please check and try again.";
    public static final String PLEASE_ENTER_A_URL = "Please enter a URL.";
    public static final String ERROR2 = "Error";
    public static final String URL = "url: ";
    public static final String ERROR3 = "Error";
    public static final String ERROR_CREATING_DIRECTORY = "Error creating directory: ";
    public static final String COPY = "Copy";
    public static final String PASTE = "Paste";
    public static final String CUT = "Cut";
    public static final String YT_DLP = "yt-dlp"; // Command-line tool for downloading videos
    public static final String O = "-o"; // Output option for yt-dlp
    public static final String TITLE_S_EXT_S = "%(title)s.%(ext)s"; // Filename template for yt-dlp
    public static final String ENTER_THE_VIDEO_URL = "Enter the video URL";
    public static final String CLEAR = "Clear";
    public static final String DOWNLOAD = "Download";
    public static final String SELECT_DOWNLOAD_FOLDER = "Select Download Folder";
    public static final int WIDTH1 = 500; // Window width in pixels
    public static final int HEIGHT1 = 120; // Window height in pixels
    public static final String VIDEO_DOWNLOADER = "Video Downloader"; // Window title

    // GUI components
    private final JTextField urlField; // Text field for entering the video URL
    private JLabel infoLabel = null; // Label for displaying status messages
    private String selectedOutputPath = DEFAULT_OUTPUT_PATH; // Current download directory

    /**
     * Constructor for VideoDownloaderFrame. Initializes the GUI components and sets up the layout.
     */
    public VideoDownloaderFrame() {
        // Declare buttons for download, clear, open folder, and select folder actions
        JButton downloadButton;
        JButton clearButton;
        JButton openFolderButton;
        JButton selectFolderButton;

        // Set up the main window properties
        setTitle(VIDEO_DOWNLOADER);
        setSize(WIDTH1, HEIGHT1);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // Close application on window close
        setLocationRelativeTo(null); // Center the window on the screen

        // Create the main panel with a BorderLayout
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        // Initialize the URL text field and add a popup menu for copy/paste/cut
        urlField = new JTextField();
        createAndSetPopupMenu();

        // Initialize the Download button and define its action
        downloadButton = new JButton(DOWNLOAD);
        downloadButton.addActionListener(e -> downloadVideo()); // Trigger video download on click

        // Initialize the Clear button and define its action
        clearButton = new JButton(CLEAR);
        clearButton.addActionListener(e -> {
            urlField.setText(""); // Clear the URL text field
            infoLabel.setText(ENTER_THE_VIDEO_URL); // Reset the status message
        });

        // Initialize the Open Folder button and define its action
        openFolderButton = new JButton("Открыть папку"); // Note: This label is in Russian
        openFolderButton.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(new File(selectedOutputPath)); // Open the download folder
            } catch (IOException ioException) {
                ioException.printStackTrace();
                infoLabel.setText("Ошибка открытия папки: " + ioException.getMessage()); // Show error
            }
        });

        // Initialize the Select Folder button and define its action
        selectFolderButton = new JButton(SELECT_DOWNLOAD_FOLDER);
        selectFolderButton.addActionListener(e -> selectDownloadFolder()); // Open folder chooser

        // Initialize the status label
        infoLabel = new JLabel(ENTER_THE_VIDEO_URL);

        // Create a panel for buttons with a 4x1 grid layout
        JPanel eastPanel = new JPanel();
        eastPanel.setLayout(new GridLayout(4, 1));
        eastPanel.add(downloadButton);
        eastPanel.add(clearButton);
        eastPanel.add(openFolderButton);
        eastPanel.add(selectFolderButton);

        // Add components to the main panel
        panel.add(urlField, BorderLayout.CENTER); // URL field in the center
        panel.add(eastPanel, BorderLayout.EAST); // Buttons on the right
        panel.add(infoLabel, BorderLayout.SOUTH); // Status label at the bottom

        // Add the panel to the frame
        add(panel);

        // Add a window listener to focus the URL field when the window opens
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent evt) {
                urlField.requestFocusInWindow(); // Set focus to URL field on startup
            }
        });
    }

    /**
     * Opens a file chooser dialog to let the user select a download folder.
     */
    private void selectDownloadFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); // Restrict to directories only
        int result = chooser.showOpenDialog(this); // Show the dialog
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedOutputPath = chooser.getSelectedFile().getAbsolutePath() + "/"; // Update path
            infoLabel.setText("Выбрана папка загрузки: " + selectedOutputPath); // Update status
        }
    }

    /**
     * Creates and attaches a popup menu to the URL field with Copy, Paste, and Cut options.
     */
    private void createAndSetPopupMenu() {
        JPopupMenu popupMenu = new JPopupMenu(); // Create the popup menu
        JMenuItem copy = new JMenuItem(COPY); // Copy option
        JMenuItem paste = new JMenuItem(PASTE); // Paste option
        JMenuItem cut = new JMenuItem(CUT); // Cut option

        // Define actions for each menu item
        copy.addActionListener(e -> urlField.copy());
        paste.addActionListener(e -> urlField.paste());
        cut.addActionListener(e -> urlField.cut());

        // Add items to the popup menu
        popupMenu.add(copy);
        popupMenu.add(paste);
        popupMenu.add(cut);

        // Attach the popup menu to the URL field
        urlField.setComponentPopupMenu(popupMenu);
    }

    /**
     * Initiates the video download process using yt-dlp or direct download if necessary.
     */
    private void downloadVideo() {
        Path outputPath = Paths.get(selectedOutputPath); // Get the output directory as a Path
        if (!Files.exists(outputPath)) {
            try {
                Files.createDirectories(outputPath); // Create the directory if it doesn't exist
            } catch (IOException e) {
                infoLabel.setText(ERROR_CREATING_DIRECTORY + e.getMessage()); // Show error
                return;
            }
        }

        String url = urlField.getText().trim(); // Get and trim the entered URL
        System.out.println(URL + url); // Log the URL for debugging

        // Check if the URL is empty
        if (url.isEmpty()) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(VideoDownloaderFrame.this,
                    PLEASE_ENTER_A_URL, ERROR2, JOptionPane.ERROR_MESSAGE)); // Show error dialog
            return;
        }

        // Validate the URL
        if (!isValidURL(url)) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(VideoDownloaderFrame.this,
                    INVALID_URL_PLEASE_CHECK_AND_TRY_AGAIN, ERROR3, JOptionPane.ERROR_MESSAGE)); // Show error
            return;
        }

        // Try downloading with yt-dlp first
        boolean success = tryYtDlp(url, outputPath);
        if (!success) {
            String extractedUrl = extractVideoUrl(url); // Extract video URL if yt-dlp fails
            if (extractedUrl != null) {
                success = tryYtDlp(extractedUrl, outputPath); // Retry with extracted URL
                if (!success) {
                    downloadDirectly(extractedUrl, outputPath); // Fall back to direct download
                }
            } else {
                infoLabel.setText("Видео не найдено на странице"); // Video not found message
            }
        } else {
            infoLabel.setText(DOWNLOAD_COMPLETE); // Success message
            System.exit(0); // Exit the program after successful download
        }
    }

    /**
     * Attempts to download a video using the yt-dlp command-line tool.
     * @param videoUrl The URL of the video to download.
     * @param outputPath The directory where the video will be saved.
     * @return True if the download succeeds, false otherwise.
     */
    private boolean tryYtDlp(String videoUrl, Path outputPath) {
        ProcessBuilder processBuilder = getProcessBuilder(videoUrl, outputPath);

        try {
            Process process = processBuilder.start(); // Start the process
            StringBuilder output = new StringBuilder(); // Store process output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n"); // Read output line by line
                }
            }
            int exitCode = process.waitFor(); // Wait for the process to complete
            if (exitCode == 0) {
                return true; // Success
            } else {
                infoLabel.setText("yt-dlp завершился с ошибкой " + exitCode + ": " + output); // Show error
                return false;
            }
        } catch (IOException ex) {
            // Handle specific yt-dlp not found errors
            if (ex.getMessage().contains("No such file or directory") || ex.getMessage().contains("cannot find the file")) {
                infoLabel.setText("yt-dlp не найден. Пожалуйста, установите yt-dlp.");
            } else {
                infoLabel.setText("Ошибка запуска yt-dlp: " + ex.getMessage());
            }
            return false;
        } catch (InterruptedException ex) {
            infoLabel.setText(INTERRUPTED + ex.getMessage()); // Handle interruption
            return false;
        }
    }

    private static @NotNull ProcessBuilder getProcessBuilder(String videoUrl, Path outputPath) {
        List<String> command = new ArrayList<>(); // Build the yt-dlp command
        command.add(YT_DLP); // Command executable
        command.add("--user-agent"); // Set a user-agent to mimic a browser
        command.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        command.add(videoUrl); // Add the video URL
        command.add(O); // Output option
        command.add(outputPath.toString() + "/" + TITLE_S_EXT_S); // Output path and filename template

        ProcessBuilder processBuilder = new ProcessBuilder(command); // Create process builder
        processBuilder.redirectErrorStream(true); // Merge error and output streams
        return processBuilder;
    }

    /**
     * Converts a relative URL to an absolute URL using the base URL.
     * @param baseUrl The base URL for resolution.
     * @param relativeUrl The relative URL to convert.
     * @return The absolute URL as a string.
     */
    private String makeAbsoluteUrl(String baseUrl, String relativeUrl) {
        try {
            URI baseUri = new URI(baseUrl);
            URI absoluteUri = baseUri.resolve(relativeUrl);
            return absoluteUri.toString();
        } catch (URISyntaxException e) {
            infoLabel.setText("Ошибка при преобразовании URL: " + e.getMessage());
            return relativeUrl;
        }
    }

    private String extractVideoUrl(String pageUrl) {
        return VideoExtractor.extractVideoUrl(pageUrl);
    }

    /**
     * Recursively searches for a video URL in the document, including iframes.
     * @param baseUrl The base URL for resolving relative URLs.
     * @param doc The HTML document to search.
     * @param depth The current recursion depth (max 2 to prevent infinite loops).
     * @return The video URL if found, or null otherwise.
     */
    private String extractVideoUrlRecursive(String baseUrl, Document doc, int depth) {
        if (depth > 2) { // Limit recursion depth to avoid infinite loops
            return null;
        }

        // Search for <video> tags
        Elements videos = doc.select("video");
        for (Element video : videos) {
            String src = video.attr("src"); // Check the src attribute
            if (src != null && !src.isEmpty()) {
                return makeAbsoluteUrl(baseUrl, src); // Return absolute URL
            }
            Elements sources = video.select("source"); // Check <source> tags inside <video>
            for (Element source : sources) {
                src = source.attr("src");
                if (src != null && !src.isEmpty()) {
                    return makeAbsoluteUrl(baseUrl, src);
                }
            }
        }

        // Search for iframes and recursively process them
        Elements iframes = doc.select("iframe");
        for (Element iframe : iframes) {
            String iframeSrc = iframe.attr("src");
            if (iframeSrc != null && !iframeSrc.isEmpty()) {
                String iframeUrl = makeAbsoluteUrl(baseUrl, iframeSrc); // Resolve iframe URL
                try {
                    Document iframeDoc = Jsoup.connect(iframeUrl)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                            .get();
                    String result = extractVideoUrlRecursive(iframeUrl, iframeDoc, depth + 1); // Recurse
                    if (result != null) {
                        return result;
                    }
                } catch (IOException e) {
                    System.out.println("Ошибка загрузки iframe: " + iframeUrl + " - " + e.getMessage()); // Log error
                    // Continue to next iframe
                }
            }
        }

        // Search for meta tags with video URLs (e.g., og:video)
        Elements metaVideos = doc.select("meta[property=og:video]");
        for (Element meta : metaVideos) {
            String content = meta.attr("content");
            if (content != null && !content.isEmpty()) {
                return makeAbsoluteUrl(baseUrl, content);
            }
        }

        return null; // No video URL found
    }

    /**
     * Downloads a video directly from a URL using HTTP requests.
     * @param videoUrl The URL of the video to download.
     * @param outputPath The directory where the video will be saved.
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

            // Разворачиваем окно на весь экран (опционально)
            driver.manage().window().maximize();

            // Браузер остаётся открытым, чтобы ты мог скачать видео вручную

        } catch (Exception e) {
            infoLabel.setText("Ошибка при открытии URL: " + e.getMessage());
        }
    }

    /**
     * Validates whether a given string is a valid HTTP or HTTPS URL.
     * @param url The URL to validate.
     * @return True if the URL is valid, false otherwise.
     */
    public static boolean isValidURL(String url) {
        if (url == null) return false; // Null URLs are invalid
        try {
            URI uri = new URI(url); // Parse the URL
            uri.parseServerAuthority(); // Check server authority
            String scheme = uri.getScheme(); // Get the protocol (http/https)
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme); // Validate scheme
        } catch (URISyntaxException e) {
            return false; // Invalid URL syntax
        }
    }
}