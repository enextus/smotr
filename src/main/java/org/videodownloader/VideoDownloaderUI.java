package org.videodownloader;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public class VideoDownloaderUI extends JFrame {

    /* ───── UI-константы ───── */
    private static final int WIDTH = 560, HEIGHT = 170;

    private static final String LBL_DEFAULT = "Select amount & press Get QRNG";
    private static final String BTN_CLEAR   = "Clear";
    private static final String BTN_SHOW    = "Show Logs";
    private static final String BTN_QRNG    = "Get QRNG";

    /* ───── компоненты ───── */
    private final JComboBox<Integer> hundreds = new JComboBox<>();
    private final JComboBox<Integer> tens     = new JComboBox<>();
    private final JComboBox<Integer> units    = new JComboBox<>();

    private final JTextField urlField = new JTextField(30);
    private final JLabel statusLbl    = new JLabel(LBL_DEFAULT);

    private final JButton clearBtn = new JButton(BTN_CLEAR);
    private final JButton logsBtn  = new JButton(BTN_SHOW);
    private final JButton qrngBtn  = new JButton(BTN_QRNG);

    private final LogManager logManager;

    public VideoDownloaderUI(LogManager logManager) {
        this.logManager = logManager;
        initUI();
    }

    /* ────────── построение интерфейса ────────── */

    private void initUI() {
        setTitle("QRNG Demo");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(WIDTH, HEIGHT);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(5, 5));

        /* ---------- A. селекторы ---------- */
        for (int i = 0; i <= 9; i++) {
            hundreds.addItem(i);
            tens.addItem(i);
            units.addItem(i);
        }
        tens.setSelectedItem(1);
        units.setSelectedItem(6);

        JPanel selector = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        selector.add(hundreds);   selector.add(new JLabel("×100"));
        selector.add(tens);       selector.add(new JLabel("×10"));
        selector.add(units);      selector.add(new JLabel("×1"));

        /* ---------- B. центральная область ---------- */
        JPanel center = new JPanel(new BorderLayout(5, 5));
        attachPopup(urlField);

        center.add(selector, BorderLayout.NORTH);   // селекторы сверху
        center.add(urlField,  BorderLayout.CENTER); // поле результата
        center.add(statusLbl, BorderLayout.SOUTH);  // статус

        add(center, BorderLayout.CENTER);

        /* ---------- C. панель кнопок ---------- */
        JPanel east = new JPanel(new GridLayout(3, 1, 5, 5));
        east.add(qrngBtn);   // Get QRNG (первой)
        east.add(logsBtn);   // Show Logs
        east.add(clearBtn);  // Clear
        add(east, BorderLayout.EAST);

        /* ---------- обработчики ---------- */
        clearBtn.addActionListener(e -> {
            urlField.setText("");
            setStatus(LBL_DEFAULT);
            logManager.appendLog("URL field cleared");
        });

        logsBtn.addActionListener(e -> {
            logManager.showLogWindow();
            logManager.appendLog("Log window opened");
        });

        qrngBtn.addActionListener(e -> runQRNG());
    }

    /* ────────── QRNG-логика ────────── */
    private void runQRNG() {
        int count = getSelectedCount();
        if (count == 0) {
            setStatus("Count must be > 0");
            return;
        }

        qrngBtn.setEnabled(false);
        setStatus("Requesting " + count + " random bytes…");
        logManager.appendLog("QRNG request started (" + count + " bytes)");

        new Thread(() -> {
            try {
                int[] bytes = RandomFetcher.fetchBytes(count);
                String text = Arrays.toString(bytes);

                SwingUtilities.invokeLater(() -> {
                    urlField.setText(text);
                    setStatus("QRNG success (" + count + " bytes)");
                    logManager.appendLog("QRNG success: " + text);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setStatus("QRNG error: " + ex.getMessage());
                    logManager.appendLog("QRNG error: " + ex);
                });
            } finally {
                SwingUtilities.invokeLater(() -> qrngBtn.setEnabled(true));
            }
        }, "QRNG-thread").start();
    }

    /* ────────── helpers ────────── */
    private int getSelectedCount() {
        int h = (Integer) hundreds.getSelectedItem();
        int t = (Integer) tens.getSelectedItem();
        int u = (Integer) units.getSelectedItem();
        return h * 100 + t * 10 + u;
    }

    private static void attachPopup(JTextField field) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem copy = new JMenuItem("Copy"), paste = new JMenuItem("Paste"), cut = new JMenuItem("Cut");
        copy.addActionListener(e -> field.copy());
        paste.addActionListener(e -> field.paste());
        cut.addActionListener(e -> field.cut());
        menu.add(copy); menu.add(paste); menu.add(cut);
        field.setComponentPopupMenu(menu);
    }

    private void setStatus(String msg) { statusLbl.setText(msg); }
}
