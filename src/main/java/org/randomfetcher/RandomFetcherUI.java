package org.randomfetcher;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Simple Swing front‑end for QRNG demo.
 * <p>
 *  Added in <em>v1.3.1‑SNAPSHOT</em>:
 *  <ul>
 *      <li>Button <strong>Analyse</strong> – opens a window with statistical tests of the last fetched sequence.</li>
 *      <li>Internal storage of the most recent sequence (List&lt;Integer&gt; lastSequence).</li>
 *  </ul>
 */
public class RandomFetcherUI extends JFrame {

    /* UI constants */
    private static final int WIDTH = 580, HEIGHT = 190;
    private static final String LBL_DEFAULT = "Select amount & press Get QRNG";

    /* drop‑down lists */
    private final JComboBox<Integer> hundreds = new JComboBox<>();
    private final JComboBox<Integer> tens     = new JComboBox<>();
    private final JComboBox<Integer> units    = new JComboBox<>();

    /* display of the selected count */
    private final JLabel countLbl = new JLabel("016", SwingConstants.CENTER);

    private final JTextField urlField = new JTextField(30);
    private final JLabel statusLbl    = new JLabel(LBL_DEFAULT);

    private final JButton clearBtn   = new JButton("Clear");
    private final JButton logsBtn    = new JButton("Show Logs");
    private final JButton qrngBtn    = new JButton("Get QRNG");
    private final JButton analyseBtn = new JButton("Analyse");

    private final LogManager logManager;

    /* stores the most recently fetched sequence (immutable) */
    private List<Integer> lastSequence = Collections.emptyList();

    public RandomFetcherUI(LogManager logManager) {
        this.logManager = logManager;
        initUI();
    }

    /* ---------- UI ---------- */
    private void initUI() {
        setTitle("QRNG Demo");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(WIDTH, HEIGHT);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(5, 5));

        /* populate combo boxes 0‑9 */
        for (int i = 0; i <= 9; i++) {
            hundreds.addItem(i);
            tens.addItem(i);
            units.addItem(i);
        }
        tens.setSelectedItem(1);
        units.setSelectedItem(6);

        /* listener to update the count label */
        var updateCount = (Runnable) () ->
                countLbl.setText(String.format("%03d", getSelectedCount()));
        hundreds.addActionListener(e -> updateCount.run());
        tens.addActionListener(e      -> updateCount.run());
        units.addActionListener(e     -> updateCount.run());

        /* selection panel */
        JPanel selector = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        selector.add(hundreds); selector.add(new JLabel("×100"));
        selector.add(tens);     selector.add(new JLabel("×10"));
        selector.add(units);    selector.add(new JLabel("×1"));

        /* big count label */
        countLbl.setFont(new Font("Monospaced", Font.BOLD, 28));
        countLbl.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.add(selector, BorderLayout.WEST);
        topRow.add(countLbl, BorderLayout.EAST);

        /* ----- center area ----- */
        JPanel center = new JPanel(new BorderLayout(5, 5));
        attachPopup(urlField);

        center.add(topRow,   BorderLayout.NORTH);
        center.add(urlField, BorderLayout.CENTER);
        center.add(statusLbl,BorderLayout.SOUTH);

        add(center, BorderLayout.CENTER);

        /* ----- buttons on the right ----- */
        JPanel east = new JPanel(new GridLayout(4, 1, 5, 5));
        east.add(qrngBtn);
        east.add(analyseBtn);
        east.add(logsBtn);
        east.add(clearBtn);
        add(east, BorderLayout.EAST);

        /* handlers */
        clearBtn.addActionListener(e -> {
            urlField.setText("");
            setStatus(LBL_DEFAULT);
            lastSequence = Collections.emptyList();
            logManager.appendLog("URL field cleared");
        });

        logsBtn.addActionListener(e -> {
            logManager.showLogWindow();
            logManager.appendLog("Log window opened");
        });

        qrngBtn.addActionListener(e -> runQRNG());
        analyseBtn.addActionListener(e -> showAnalysis());
    }

    /* ---------- QRNG ---------- */
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
                List<Integer> list = Arrays.stream(bytes).boxed().toList();
                SwingUtilities.invokeLater(() -> {
                    lastSequence = List.copyOf(list);
                    urlField.setText(list.toString());
                    setStatus("QRNG success (" + count + " bytes)");
                    logManager.appendLog("QRNG success: " + list);
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

    /* ---------- Analyse ---------- */
    private void showAnalysis() {
        if (lastSequence.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No sequence fetched yet.\nPress 'Get QRNG' first.",
                    "Analyse", JOptionPane.WARNING_MESSAGE);
            return;
        }

        new Thread(() -> {
            try {
                RandomnessTester tester = new RandomnessTester(lastSequence, 0, 255);
                StringBuilder sb = new StringBuilder();

                sb.append("=== Sequence analysis ===\n");
                sb.append("Count: ").append(lastSequence.size()).append('\n');
                sb.append("Kolmogorov–Smirnov (p > 0.05): ")
                        .append(tester.kolmogorovSmirnovTest(0.05) ? "Passed" : "Failed").append('\n');
                sb.append("Chi‑Square (8 bins): ")
                        .append(tester.chiSquareTest(8, 0.05) ? "Passed" : "Failed").append('\n');
                sb.append("Runs‑test (Wald–Wolfowitz): ")
                        .append(tester.runsTest(0.05) ? "Passed" : "Failed").append('\n');
                sb.append(String.format("Autocorr (lag 1): %.4f\n", tester.autocorrelation(1)));
                sb.append("Max consecutive repeats: ")
                        .append(tester.countConsecutiveRepeats()).append('\n');
                sb.append("CRC‑32: 0x")
                        .append(Long.toHexString(tester.crc32()).toUpperCase()).append('\n');

                SwingUtilities.invokeLater(() -> showAnalysisWindow(sb.toString()));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "Error analysing sequence: " + ex.getMessage(),
                        "Analyse", JOptionPane.ERROR_MESSAGE));
            }
        }, "Analysis-thread").start();
    }

    private void showAnalysisWindow(String text) {
        JTextArea area = new JTextArea(text, 12, 40);
        area.setEditable(false);
        area.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JDialog dlg = new JDialog(this, "Analysis", false);
        dlg.getContentPane().add(scroll);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    /* ---------- helpers ---------- */
    int getSelectedCount() {
        return 100 * (Integer) hundreds.getSelectedItem()
                + 10  * (Integer) tens.getSelectedItem()
                +       (Integer) units.getSelectedItem();
    }

    static void attachPopup(JTextField f) {
        JPopupMenu m = new JPopupMenu();
        JMenuItem c = new JMenuItem("Copy"),
                p = new JMenuItem("Paste"),
                x = new JMenuItem("Cut");
        c.addActionListener(e -> f.copy());
        p.addActionListener(e -> f.paste());
        x.addActionListener(e -> f.cut());
        m.add(c); m.add(p); m.add(x);
        f.setComponentPopupMenu(m);
    }

    void setStatus(String s) {
        statusLbl.setText(s);
    }

    /* ---------- main (demo) ---------- */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new RandomFetcherUI(new LogManager()).setVisible(true));
    }
}
