package org.randomfetcher;

import javax.swing.*;
import java.awt.*;
import java.io.Serial;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * Updated Swing front‑end for QRNG demo.
 * <p>
 * Changes vs. v1:
 * <ul>
 *   <li>Correct initialisation (initUI() is now public and always called on EDT).</li>
 *   <li>All long‑running tasks migrated to {@link SwingWorker} for proper threading.</li>
 *   <li>Null‑checks via {@link Objects#requireNonNull(Object)}.</li>
 *   <li>Tiny clean‑ups & small UX tweaks.</li>
 * </ul>
 */
@SuppressWarnings({"serial", "initialization"})
public class RandomFetcherUI extends JFrame {
    @Serial
    private static final long serialVersionUID = 1L;

    /* -------------------- UI widgets -------------------- */
    private final JComboBox<Integer> hundreds = new JComboBox<>();
    private final JComboBox<Integer> tens     = new JComboBox<>();
    private final JComboBox<Integer> units    = new JComboBox<>();

    private final JLabel  statusLbl = new JLabel(LBL_DEFAULT);
    private final JLabel  countLbl  = new JLabel("016", SwingConstants.CENTER);
    private final JTextField urlField = new JTextField(30);

    private final JButton clearBtn   = new JButton("Clear");
    private final JButton logsBtn    = new JButton("Show Logs");
    private final JButton qrngBtn    = new JButton("Get QRNG");
    private final JButton analyseBtn = new JButton("Analyse");

    private final LogManager logManager;

    private volatile List<Integer> lastSequence = Collections.emptyList();

    /* ---- constants ---- */
    private static final int WIDTH = 580, HEIGHT = 190;
    private static final String LBL_DEFAULT = "Select amount & press Get QRNG";

    /* ==================================================== */
    public RandomFetcherUI(LogManager logManager) {
        this.logManager = Objects.requireNonNull(logManager);
    }

    /** Entry helper: build and show UI on EDT. */
    public static void createAndShow(LogManager manager) {
        EventQueue.invokeLater(() -> {
            RandomFetcherUI ui = new RandomFetcherUI(manager);
            ui.initUI();
            ui.setVisible(true);
        });
    }

    /** Initialises Swing components (call on EDT!). */
    public void initUI() {
        setTitle("QRNG Demo");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(WIDTH, HEIGHT);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(5, 5));

        /* ---------- populate combos ---------- */
        for (int i = 0; i <= 9; i++) {
            hundreds.addItem(i);
            tens.addItem(i);
            units.addItem(i);
        }
        tens.setSelectedItem(1);
        units.setSelectedItem(6);

        /* ---------- selectors ---------- */
        Runnable updateCount = () -> countLbl.setText(String.format("%03d", getSelectedCount()));
        hundreds.addActionListener(e -> updateCount.run());
        tens.addActionListener(e -> updateCount.run());
        units.addActionListener(e -> updateCount.run());

        JPanel selector = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        selector.add(hundreds); selector.add(new JLabel("×100"));
        selector.add(tens);     selector.add(new JLabel("×10"));
        selector.add(units);    selector.add(new JLabel("×1"));

        countLbl.setFont(new Font("Monospaced", Font.BOLD, 28));
        countLbl.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.add(selector, BorderLayout.WEST);
        topRow.add(countLbl,  BorderLayout.EAST);

        /* ---------- centre ---------- */
        JPanel centre = new JPanel(new BorderLayout(5, 5));
        attachPopup(urlField);
        centre.add(topRow, BorderLayout.NORTH);
        centre.add(urlField, BorderLayout.CENTER);
        centre.add(statusLbl, BorderLayout.SOUTH);
        add(centre, BorderLayout.CENTER);

        /* ---------- buttons ---------- */
        JPanel east = new JPanel(new GridLayout(4, 1, 5, 5));
        east.add(qrngBtn);
        east.add(analyseBtn);
        east.add(logsBtn);
        east.add(clearBtn);
        add(east, BorderLayout.EAST);

        /* ---------- actions ---------- */
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

    /* ==================================================== */
    private void runQRNG() {
        final int count = getSelectedCount();
        if (count == 0) {
            setStatus("Count must be > 0");
            return;
        }
        qrngBtn.setEnabled(false);
        setStatus("Requesting " + count + " random bytes…");
        logManager.appendLog("QRNG request started (" + count + " bytes)");

        SwingWorker<List<Integer>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Integer> doInBackground() throws Exception {
                int[] bytes = RandomFetcher.fetchBytes(count);
                return Arrays.stream(bytes).boxed().toList();
            }

            @Override
            protected void done() {
                try {
                    lastSequence = get();
                    urlField.setText(lastSequence.toString());
                    setStatus("QRNG success (" + count + " bytes)");
                    logManager.appendLog("QRNG success: " + lastSequence);
                } catch (InterruptedException | ExecutionException ex) {
                    setStatus("QRNG error: " + ex.getMessage());
                    logManager.appendLog("QRNG error: " + ex);
                    Thread.currentThread().interrupt();
                } finally {
                    qrngBtn.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    /* ==================================================== */
    private void showAnalysis() {
        if (lastSequence.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No sequence fetched yet.\nPress 'Get QRNG' first.",
                    "Analyse", JOptionPane.WARNING_MESSAGE);
            return;
        }

        analyseBtn.setEnabled(false);

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                StringBuilder report = new StringBuilder(buildLocalReport());
                try {
                    String gpt = OpenAIAnalyzer.analyze(lastSequence);
                    logManager.appendLog("LLM analysis done");
                    report.append("\n<h3>🤖 LLM Summary</h3><p style='max-width:600px; font-family: sans-serif;'>")
                            .append(gpt)
                            .append("</p>");
                } catch (Exception ex) {
                    logManager.appendLog("LLM analysis failed: " + ex);
                    report.append("\n<h3>🤖 LLM Summary</h3><p style='color:red;'>⚠ Error: ")
                            .append(ex.getMessage()).append("</p>");
                }
                return "<html><body style='font-family: monospace;'><pre>" + report + "</pre></body></html>";
            }

            @Override
            protected void done() {
                try {
                    showAnalysisWindow(get());
                } catch (InterruptedException | ExecutionException ex) {
                    JOptionPane.showMessageDialog(RandomFetcherUI.this,
                            "Error analysing sequence: " + ex.getMessage(),
                            "Analyse", JOptionPane.ERROR_MESSAGE);
                    Thread.currentThread().interrupt();
                } finally {
                    analyseBtn.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    /* ==================================================== */
    int getSelectedCount() {
        return hundreds.getSelectedIndex() * 100 + tens.getSelectedIndex() * 10 + units.getSelectedIndex();
    }

    static void attachPopup(JTextField f) {
        JPopupMenu m = new JPopupMenu();
        JMenuItem c = new JMenuItem("Copy"), p = new JMenuItem("Paste"), x = new JMenuItem("Cut");
        c.addActionListener(e -> f.copy());
        p.addActionListener(e -> f.paste());
        x.addActionListener(e -> f.cut());
        m.add(c); m.add(p); m.add(x);
        f.setComponentPopupMenu(m);
    }

    void setStatus(String s) { statusLbl.setText(s); }

    /* ==================================================== */
    private String buildLocalReport() {
        RandomnessTester tester = new RandomnessTester(lastSequence, 0, 255);
        return String.format("""
=== Sequence analysis ===
<b>Count:</b> %d
<b>Kolmogorov–Smirnov (p > 0.05):</b> %s
<b>Chi-Square (8 bins):</b> %s
<b>Runs-test (Wald–Wolfowitz):</b> %s
<b>Autocorr (lag 1):</b> %.4f
<b>Max consecutive repeats:</b> %d
<b>CRC-32:</b> 0x%X
""",
                lastSequence.size(),
                tester.kolmogorovSmirnovTest(0.05) ? "Passed" : "Failed",
                tester.chiSquareTest(8, 0.05)      ? "Passed" : "Failed",
                tester.runsTest(0.05)              ? "Passed" : "Failed",
                tester.autocorrelation(1),
                tester.countConsecutiveRepeats(),
                tester.crc32());
    }

    private void showAnalysisWindow(String html) {
        JEditorPane pane = new JEditorPane("text/html", html);
        pane.setEditable(false);
        JScrollPane scroll = new JScrollPane(pane);
        scroll.setPreferredSize(new Dimension(640, 400));

        JDialog dlg = new JDialog(this, "Analysis", false);
        dlg.getContentPane().add(scroll);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    /* ==================================================== */
    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            RandomFetcherUI ui = new RandomFetcherUI(new LogManager());
            ui.initUI();
            ui.setVisible(true);
        });
    }
}
