package org.randomfetcher;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.Serial;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Simple Swing front‑end for QRNG demo.
 */
@SuppressWarnings({"serial","initialization"})
public class RandomFetcherUI extends JFrame {
    @Serial
    private static final long serialVersionUID = 1L;

    // GUI components (transient to avoid serialization issues)
    private transient final JComboBox<Integer> hundreds = new JComboBox<>();
    private transient final JComboBox<Integer> tens     = new JComboBox<>();
    private transient final JComboBox<Integer> units    = new JComboBox<>();
    private transient final JLabel statusLbl           = new JLabel(LBL_DEFAULT);
    private transient final JLabel countLbl            = new JLabel("016", SwingConstants.CENTER);
    private transient final JTextField urlField         = new JTextField(30);

    private final JButton clearBtn   = new JButton("Clear");
    private final JButton logsBtn    = new JButton("Show Logs");
    private final JButton qrngBtn    = new JButton("Get QRNG");
    private final JButton analyseBtn = new JButton("Analyse");

    private final LogManager logManager;
    private List<Integer> lastSequence = Collections.emptyList();

    // Window size and default status text
    private static final int WIDTH = 580, HEIGHT = 190;
    private static final String LBL_DEFAULT = "Select amount & press Get QRNG";

    public RandomFetcherUI(LogManager logManager) {
        this.logManager = logManager;
        // no UI initialization here to avoid "this-escape" warning
    }

    /**
     * Static factory: create and display on EDT.
     */
    public static void createAndShow(LogManager manager) {
        SwingUtilities.invokeLater(() -> {
            RandomFetcherUI ui = new RandomFetcherUI(manager);
            ui.initUI();
            ui.setVisible(true);
        });
    }private void initUI() {
        setTitle("QRNG Demo");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(WIDTH, HEIGHT);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(5, 5));

        // Name components for UI tests
        qrngBtn.setName("btnGetQrng");
        analyseBtn.setName("btnAnalyse");
        logsBtn.setName("btnLogs");
        clearBtn.setName("btnClear");
        statusLbl.setName("statusLbl");

        // Populate combo boxes
        for (int i = 0; i <= 9; i++) {
            hundreds.addItem(i);
            tens.addItem(i);
            units.addItem(i);
        }
        tens.setSelectedItem(1);
        units.setSelectedItem(6);

        // Update count label
        Runnable updateCount = () -> countLbl.setText(String.format("%03d", getSelectedCount()));
        hundreds.addActionListener(e -> updateCount.run());
        tens.addActionListener(e    -> updateCount.run());
        units.addActionListener(e   -> updateCount.run());

        // Top selector panel
        JPanel selector = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        selector.add(hundreds); selector.add(new JLabel("×100"));
        selector.add(tens);     selector.add(new JLabel("×10"));
        selector.add(units);    selector.add(new JLabel("×1"));

        countLbl.setFont(new Font("Monospaced", Font.BOLD, 28));
        countLbl.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        JPanel topRow = new JPanel(new BorderLayout());
        topRow.add(selector, BorderLayout.WEST);
        topRow.add(countLbl, BorderLayout.EAST);

        // Center area
        JPanel center = new JPanel(new BorderLayout(5, 5));
        attachPopup(urlField);
        center.add(topRow, BorderLayout.NORTH);
        center.add(urlField, BorderLayout.CENTER);
        center.add(statusLbl, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        // Buttons panel
        JPanel east = new JPanel(new GridLayout(4, 1, 5, 5));
        east.add(qrngBtn);
        east.add(analyseBtn);
        east.add(logsBtn);
        east.add(clearBtn);
        add(east, BorderLayout.EAST);

        // Handlers
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

    private void runQRNG() {
        int count = getSelectedCount();
        if (count == 0) {
            setStatus("Count must be > 0");
            return;
        }

        qrngBtn.setEnabled(false);
        setStatus(String.format("Requesting %d random bytes…", count));
        logManager.appendLog(String.format("QRNG request started (%d bytes)", count));

        new Thread(() -> {
            try {
                int[] bytes = RandomFetcher.fetchBytes(count);
                List<Integer> list = Arrays.stream(bytes).boxed().toList();
                SwingUtilities.invokeLater(() -> {
                    lastSequence = List.copyOf(list);
                    urlField.setText(list.toString());
                    setStatus(String.format("QRNG success (%d bytes)", count));
                    logManager.appendLog(String.format("QRNG success: %s", list));
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setStatus(String.format("QRNG error: %s", ex.getMessage()));
                    logManager.appendLog(String.format("QRNG error: %s", ex));
                });
            } finally {
                SwingUtilities.invokeLater(() -> qrngBtn.setEnabled(true));
            }
        }, "QRNG-thread").start();
    }

    private void showAnalysis() {
        if (lastSequence.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No sequence fetched yet.\nPress 'Get QRNG' first.",
                    "Analyse", JOptionPane.WARNING_MESSAGE);
            return;
        }

        new Thread(() -> {
            try {
                String report = buildLocalReport();
                try {
                    String gptReport = OpenAIAnalyzer.analyze(lastSequence);
                    logManager.appendLog("GPT-3.5 analysis done");
                    report += """
<h3>🤖 GPT-3.5-Turbo Summary</h3>
<p style='max-width:600px; font-family: sans-serif;'>%s</p>
""".formatted(gptReport);
                } catch (Exception ex) {
                    logManager.appendLog("LLM analysis failed: " + ex);
                    report += """
<h3>🤖 GPT-3.5-Turbo Summary</h3>
<p style='color:red;'>⚠ Error getting LLM answer: %s</p>
""".formatted(ex.getMessage());
                }

                String finalReport = """
<html><body style='font-family: monospace;'><pre>%s</pre></body></html>
""".formatted(report);

                SwingUtilities.invokeLater(() -> showAnalysisWindow(finalReport));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        String.format("Error analysing sequence: %s", ex.getMessage()),
                        "Analyse", JOptionPane.ERROR_MESSAGE));
            }
        }, "Analysis-thread").start();
    }

    private @NotNull String buildLocalReport() {
        RandomnessTester tester = new RandomnessTester(lastSequence, 0, 255);
        return """
=== Sequence analysis ===
<b>Count:</b> %d
<b>Kolmogorov–Smirnov (p > 0.05):</b> %s
<b>Chi-Square (8 bins):</b> %s
<b>Runs-test (Wald–Wolfowitz):</b> %s
<b>Autocorr (lag 1):</b> %.4f
<b>Max consecutive repeats:</b> %d
<b>CRC-32:</b> 0x%X
""".formatted(
                lastSequence.size(),
                tester.kolmogorovSmirnovTest(0.05) ? "Passed" : "Failed",
                tester.chiSquareTest(8, 0.05)      ? "Passed" : "Failed",
                tester.runsTest(0.05)              ? "Passed" : "Failed",
                tester.autocorrelation(1),
                tester.countConsecutiveRepeats(),
                tester.crc32()
        );
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

    int getSelectedCount() {
        // Null-safe unboxing
        return 100 * safeGet(hundreds)
                + 10  * safeGet(tens)
                +       safeGet(units);
    }

    private static int safeGet(JComboBox<Integer> box) {
        Integer v = (Integer) box.getSelectedItem();
        return v != null ? v : 0;
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new RandomFetcherUI(new LogManager()).setVisible(true));
    }
}
