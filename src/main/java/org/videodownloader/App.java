// src/main/java/org/videodownloader/App.java
package org.videodownloader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class App {

    /** Колбэк, который ожидает VideoDownloadManager */
    public interface DownloadListener {
        void onStatusUpdate(String status);
    }

    private JFrame frame;
    private JTextField urlField;
    private JTextField folderField;
    private JButton downloadBtn;
    private JButton cancelBtn;
    private JButton chooseFolderBtn;
    private JButton openFolderBtn;
    private JTextArea statusArea;
    private JProgressBar progressBar;

    private volatile boolean downloading = false;

    private VideoDownloadManager manager;

    // анти-дубликат для "Saved to:"
    private volatile String lastShownSavedPath = null;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new App().start());
    }

    private void start() {
        // UI
        frame = new JFrame("Video Downloader");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                if (downloading && manager != null) {
                    int ans = JOptionPane.showConfirmDialog(
                            frame,
                            "Загрузка ещё идёт. Действительно выйти?",
                            "Подтверждение",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE
                    );
                    if (ans != JOptionPane.YES_OPTION) return;
                    manager.cancelDownload();
                }
                frame.dispose();
                System.exit(0);
            }
        });
        frame.setMinimumSize(new Dimension(900, 540));

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        frame.setContentPane(root);

        // Верхняя панель
        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: URL + Download/Cancel
        JLabel urlLbl = new JLabel("Video URL:");
        gc.gridx = 0; gc.gridy = 0; gc.weightx = 0;
        top.add(urlLbl, gc);

        urlField = new JTextField();
        urlField.setToolTipText("Вставьте ссылку на страницу с видео (Ctrl+L — фокус сюда)");
        gc.gridx = 1; gc.gridy = 0; gc.weightx = 1;
        top.add(urlField, gc);

        downloadBtn = new JButton("Download");
        downloadBtn.setToolTipText("Начать загрузку (Enter)");
        gc.gridx = 2; gc.gridy = 0; gc.weightx = 0;
        top.add(downloadBtn, gc);

        cancelBtn = new JButton("Cancel");
        cancelBtn.setEnabled(false);
        cancelBtn.setToolTipText("Остановить текущую загрузку (Esc)");
        gc.gridx = 3; gc.gridy = 0;
        top.add(cancelBtn, gc);

        // Row 1: Folder + Choose/Open
        JLabel folderLbl = new JLabel("Download folder:");
        gc.gridx = 0; gc.gridy = 1; gc.weightx = 0;
        top.add(folderLbl, gc);

        folderField = new JTextField();
        folderField.setEditable(false);
        folderField.setToolTipText("Текущая папка загрузок");
        gc.gridx = 1; gc.gridy = 1; gc.weightx = 1;
        top.add(folderField, gc);

        chooseFolderBtn = new JButton("Choose…");
        chooseFolderBtn.setToolTipText("Выбрать другую папку для загрузок");
        gc.gridx = 2; gc.gridy = 1; gc.weightx = 0;
        top.add(chooseFolderBtn, gc);

        openFolderBtn = new JButton("Open");
        openFolderBtn.setToolTipText("Открыть папку в проводнике");
        gc.gridx = 3; gc.gridy = 1; gc.weightx = 0;
        top.add(openFolderBtn, gc);

        root.add(top, BorderLayout.NORTH);

        // Центр: статус/лог
        statusArea = new JTextArea();
        statusArea.setEditable(false);
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(statusArea);
        root.add(scroll, BorderLayout.CENTER);

        // Низ: прогресс
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("");
        progressBar.setMinimum(0);
        progressBar.setMaximum(100);
        progressBar.setValue(0);
        root.add(progressBar, BorderLayout.SOUTH);

        // Менеджер
        manager = new VideoDownloadManager();

        // Инициализация папки при старте
        boolean ready = manager.initOutputDirOnStartup(frame);
        if (!ready) {
            JOptionPane.showMessageDialog(
                    frame,
                    "Папка для загрузок не выбрана. Приложение будет закрыто.",
                    "Выход",
                    JOptionPane.INFORMATION_MESSAGE
            );
            System.exit(0);
            return;
        }
        folderField.setText(manager.getSelectedOutputPath());
        openFolderBtn.setEnabled(new File(manager.getSelectedOutputPath()).exists());

        // Clipboard bootstrap: если в буфере уже лежит URL — подставим
        try {
            var cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (cb.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                String s = (String) cb.getData(DataFlavor.stringFlavor);
                if (looksLikeUrl(s)) urlField.setText(s.trim());
            }
        } catch (Exception ignored) {}

        // Drag & Drop URL в поле
        new DropTarget(urlField, new DropTargetAdapter() {
            @Override public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    if (dtde.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                        String s = (String) dtde.getTransferable().getTransferData(DataFlavor.stringFlavor);
                        if (looksLikeUrl(s)) urlField.setText(s.trim());
                    } else if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @SuppressWarnings("unchecked")
                        List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                        if (!files.isEmpty()) urlField.setText(files.get(0).toURI().toString());
                    }
                } catch (Exception ignored) {
                } finally {
                    dtde.dropComplete(true); // корректно завершаем DnD-жест
                }
            }
        });

        // Actions
        downloadBtn.addActionListener(e -> startDownload());
        cancelBtn.addActionListener(e -> {
            manager.cancelDownload();
            appendStatus("Cancel requested");
            setBusy(false);
        });
        chooseFolderBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Выберите папку для загрузок");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setAcceptAllFileFilterUsed(false);
            int res = chooser.showOpenDialog(frame);
            if (res == JFileChooser.APPROVE_OPTION) {
                Path selected = chooser.getSelectedFile().toPath();
                try {
                    manager.setSelectedOutputPath(selected.toString());
                    folderField.setText(selected.toAbsolutePath().normalize().toString());
                    openFolderBtn.setEnabled(new File(manager.getSelectedOutputPath()).exists());
                    appendStatus("Download folder set to: " + folderField.getText());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                            frame,
                            "Не удалось установить папку:\n" + selected + "\n" + ex.getMessage(),
                            "Ошибка",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        });
        openFolderBtn.addActionListener(e -> {
            try {
                File f = Paths.get(manager.getSelectedOutputPath()).toFile();
                if (!f.exists()) {
                    JOptionPane.showMessageDialog(frame, "Папка не существует:\n" + f, "Нет папки", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                Desktop.getDesktop().open(f);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Не удалось открыть папку:\n" + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Shortcuts
        frame.getRootPane().setDefaultButton(downloadBtn); // Enter = Download
        InputMap im = root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap am = root.getActionMap();
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "cancel");
        am.put("cancel", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (downloading) cancelBtn.doClick();
            }
        });
        im.put(KeyStroke.getKeyStroke("control L"), "focusUrl");
        am.put("focusUrl", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                urlField.requestFocusInWindow();
                urlField.selectAll();
            }
        });

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // удобство: сразу фокус в поле URL
        SwingUtilities.invokeLater(() -> urlField.requestFocusInWindow());
    }

    private void startDownload() {
        String url = urlField.getText().trim();
        if (url.isEmpty() || !looksLikeUrl(url)) {
            urlField.requestFocusInWindow();
            urlField.selectAll();
            JOptionPane.showMessageDialog(frame, "Введите корректный http/https URL.", "Некорректный URL", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // сбрасываем анти-дубликат перед новой загрузкой
        lastShownSavedPath = null;

        setBusy(true);
        appendStatus("Starting download…");

        manager.downloadVideo(url, new DownloadListener() {
            @Override public void onStatusUpdate(String status) {
                // Простейший парсер прогресса из текста, если когда-то начнёте прокидывать его из менеджера
                maybeUpdateProgress(status);
                appendStatus(status);

                var saved = manager.getLastSavedFile(); // нужен соответствующий геттер в менеджере
                if (saved != null) {
                    String full = saved.toAbsolutePath().normalize().toString();
                    if (!full.equals(lastShownSavedPath)) {
                        lastShownSavedPath = full;
                        appendStatus("Saved to: " + full);
                        copyToClipboard(full);
                    }
                }

                String s = status.toLowerCase();
                if (s.contains("download complete") || s.contains("download failed")) {
                    SwingUtilities.invokeLater(() -> setBusy(false));
                }
            }
        });
    }

    private void setBusy(boolean busy) {
        downloading = busy;
        downloadBtn.setEnabled(!busy);
        cancelBtn.setEnabled(busy);
        chooseFolderBtn.setEnabled(!busy);
        openFolderBtn.setEnabled(!busy);
        urlField.setEnabled(!busy);
        progressBar.setIndeterminate(busy);
        progressBar.setString(busy ? "Working…" : "");
        if (!busy) {
            progressBar.setIndeterminate(false);
            progressBar.setValue(0);
            progressBar.setString("");
        }
    }

    private void appendStatus(String msg) {
        SwingUtilities.invokeLater(() -> {
            statusArea.append(msg + System.lineSeparator());
            statusArea.setCaretPosition(statusArea.getDocument().getLength());
        });
    }

    private boolean looksLikeUrl(String s) {
        try {
            URI u = new URI(s.trim());
            String scheme = u.getScheme();
            return scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        } catch (Exception e) {
            return false;
        }
    }

    /** Пытаемся выдрать проценты из строки статуса, если когда-нибудь пойдёт поток от yt-dlp */
    private void maybeUpdateProgress(String status) {
        // Примеры yt-dlp: "[download]  42.5% of 123.45MiB at 3.21MiB/s ETA 00:10"
        String s = status.trim();
        int i = s.indexOf('%');
        if (i > 0) {
            // найдём начало числа перед '%'
            int j = i - 1;
            while (j >= 0 && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.')) j--;
            try {
                double p = Double.parseDouble(s.substring(j + 1, i));
                int val = Math.max(0, Math.min(100, (int) Math.round(p)));
                SwingUtilities.invokeLater(() -> {
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(val);
                    progressBar.setString(val + "%");
                });
            } catch (Exception ignored) {
                // оставим индикатор как есть
            }
        }
    }

    private void copyToClipboard(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(text), null);
        } catch (Exception ignored) {}
    }
}
