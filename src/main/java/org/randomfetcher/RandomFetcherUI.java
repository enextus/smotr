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
 * <h1>RandomFetcherUI</h1>
 *
 * Обновлённый GUI‑фронтенд демонстрационной программы для работы с квантовым
 * генератором случайных чисел (QRNG).
 *
 * <h2>Ключевые изменения по сравнению с первой версией</h2>
 * <ul>
 *     <li>❶ Вместо трёх «сотни / десятки / единицы» введён {@link JSpinner},
 *         куда можно ввести 1…100 000 байт, а также добавлены четыре
 *         кнопки‑пресета: 128 / 512 / 2048 / 100 000.</li>
 *     <li>❷ Если пользователь пытается анализировать выборку &lt; 40 байт,
 *         появляется предупреждение и «автосовет» увеличить объём.</li>
 *     <li>❸ Все фоновые операции (запрос QRNG, обращение к LLM) выполнены
 *         через {@link SwingWorker} — корректная работа с потоками Swing.</li>
 *     <li>❹ В LLM‑prompt передаются точные p‑values статистических тестов,
 *         что делает ответ модели более содержательным.</li>
 *     <li>❺ Мелкие улучшения UX: выравнивание полей, логирование, null‑checks.</li>
 * </ul>
 *
 * <p>Файл снабжён обильными комментариями, чтобы любой разработчик мог быстро
 * разобраться в логике и при необходимости внести изменения.</p>
 */
@SuppressWarnings({"serial", "initialization"})
public class RandomFetcherUI extends JFrame {

    @Serial
    private static final long serialVersionUID = 1L;

    /* ------------------------------------------------------------------ *
     *                1.  К О М П О Н Е Н Т Ы   И   П О Л Я               *
     * ------------------------------------------------------------------ */

    /* ---------- новый селектор количества байтов ---------- */
    private final JSpinner spinner = new JSpinner(new SpinnerNumberModel(
            16,          // значение по умолчанию
            1,           // минимум
            100_000,     // максимум
            1));         // шаг

    /* метка статуса (низ окна) и крупная метка текущего значения */
    private final JLabel statusLbl = new JLabel(LBL_DEFAULT);
    private final JLabel countLbl  = new JLabel("16", SwingConstants.CENTER);

    /* поле, куда выводится полученная последовательность */
    private final JTextField urlField = new JTextField(30);

    /* кнопки */
    private final JButton clearBtn   = new JButton("Clear");
    private final JButton logsBtn    = new JButton("Show Logs");
    private final JButton qrngBtn    = new JButton("Get QRNG");
    private final JButton analyseBtn = new JButton("Analyse");

    /* менеджер логов (вывод в файл + всплывающее окно) */
    private final LogManager logManager;

    /* последняя полученная последовательность */
    private volatile List<Integer> lastSequence = Collections.emptyList();

    /* --- константы оформления --- */
    private static final int WIDTH = 650, HEIGHT = 220;
    private static final String LBL_DEFAULT = "Select size & press Get QRNG";

    /* ------------------------------------------------------------------ *
     *                       2.   К О Н С Т Р У К Т О Р                    *
     * ------------------------------------------------------------------ */

    public RandomFetcherUI(LogManager logManager) {
        this.logManager = Objects.requireNonNull(logManager);
    }

    /* ------------------------------------------------------------------ *
     *               3.  С Т А Т И Ч Е С К И Й   C R E A T E               *
     * ------------------------------------------------------------------ */

    /** Удобный метод: построить и сразу показать окно на EDT. */
    public static void createAndShow(LogManager manager) {
        EventQueue.invokeLater(() -> {
            RandomFetcherUI ui = new RandomFetcherUI(manager);
            ui.initUI();
            ui.setVisible(true);
        });
    }

    /* ------------------------------------------------------------------ *
     *                        4.   И Н И Ц И А Л И З А Ц И Я               *
     * ------------------------------------------------------------------ */

    /** Построение всех Swing‑компонентов. Вызывать только из EDT! */
    public void initUI() {
        /* --- общие свойства окна --- */
        setTitle("QRNG Demo");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(WIDTH, HEIGHT);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(5, 5));

        /* ---------- 4.1 Блок выбора размера ---------- */
        // Форматируем текстовое поле спиннера
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor de) {
            de.getTextField().setHorizontalAlignment(SwingConstants.CENTER);
        }

        // Кнопки‑пресеты
        JButton b128  = presetButton("128");
        JButton b512  = presetButton("512");
        JButton b2k   = presetButton("2048");
        JButton b100k = presetButton("100000");

        JPanel selector = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        selector.add(new JLabel("Bytes:"));
        selector.add(spinner);
        selector.add(b128);
        selector.add(b512);
        selector.add(b2k);
        selector.add(b100k);

        // Крупный счётчик справа
        countLbl.setFont(new Font("Monospaced", Font.BOLD, 32));
        countLbl.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));

        // При изменении спиннера обновляем countLbl
        spinner.addChangeListener(e ->
                countLbl.setText(spinner.getValue().toString()));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.add(selector, BorderLayout.WEST);
        topRow.add(countLbl,  BorderLayout.EAST);

        /* ---------- 4.2 Центральная панель ---------- */
        JPanel centre = new JPanel(new BorderLayout(5, 5));
        attachPopup(urlField);                  // контекстное меню copy/paste
        centre.add(topRow, BorderLayout.NORTH);
        centre.add(urlField, BorderLayout.CENTER);
        centre.add(statusLbl, BorderLayout.SOUTH);
        add(centre, BorderLayout.CENTER);

        /* ---------- 4.3 Кнопки справа ---------- */
        JPanel east = new JPanel(new GridLayout(4, 1, 5, 5));
        east.add(qrngBtn);
        east.add(analyseBtn);
        east.add(logsBtn);
        east.add(clearBtn);
        add(east, BorderLayout.EAST);

        /* ---------- 4.4 Обработчики ---------- */
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

    /* ------------------------------------------------------------------ *
     *                       5.  Л О Г И К А   К Н О П О К                *
     * ------------------------------------------------------------------ */

    /** Фоновый запрос QRNG, запись результатов и обновление UI. */
    private void runQRNG() {
        final int count = getSelectedCount();

        /* --- 5.1 Проверка лимитов и предупредительное сообщение --- */
        if (count < 40) {
            logManager.appendLog("⚠ sample < 40 bytes – low statistical power");
            JOptionPane.showMessageDialog(this,
                    "Выборка меньше 40 байт; результаты тестов будут ориентировочны.\n"
                            + "Совет: попробуйте 128 или 256 байт.",
                    "Too small sample", JOptionPane.INFORMATION_MESSAGE);
        }

        /* --- 5.2 Подготовка UI и логов --- */
        qrngBtn.setEnabled(false);
        setStatus("Requesting " + count + " random bytes…");
        logManager.appendLog("QRNG request started (" + count + " bytes)");

        /* --- 5.3 Фоновая задача --- */
        SwingWorker<List<Integer>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Integer> doInBackground() throws Exception {
                int[] bytes = RandomFetcher.fetchBytes(count);
                return Arrays.stream(bytes).boxed().toList();
            }

            @Override
            protected void done() {
                try {
                    lastSequence = get();                 // результат
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

    /** Окно детального анализа + запрос LLM. */
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
                /* --- 1) Локальные тесты --- */
                RandomnessTester t = new RandomnessTester(lastSequence, 0, 255);

                String local = String.format("""
=== Sequence analysis ===
<b>Count:</b> %d
<b>Kolmogorov–Smirnov:</b> p = %.4f  ⇒ %s
<b>Chi-Square (8 bins):</b> χ² = %.2f, p = %.4f  ⇒ %s
<b>Runs-test:</b> z = %.3f, p = %.4f  ⇒ %s
<b>Autocorr (lag 1):</b> %.4f
<b>Max consecutive repeats:</b> %d
<b>CRC-32:</b> 0x%X
""",
                        lastSequence.size(),
                        t.ksPValue(),   t.ksPValue()   > 0.05 ? "Passed" : "Failed",
                        t.chiSquare(),  t.chiPValue(), t.chiPValue() > 0.05 ? "Passed" : "Failed",
                        t.runsZ(),      t.runsPValue(), t.runsPValue()> 0.05 ? "Passed" : "Failed",
                        t.autocorrelation(1),
                        t.countConsecutiveRepeats(),
                        t.crc32());

                        String prompt = """
Bytes: %s
%s
На русском и английском, ≤150 слов на язык: 
суммируй, какие тесты прошли/не прошли, и оцени репрезентативность выборки.
""".formatted(lastSequence, local.replace("\n", "\\n"));

                /* --- 3) LLM --- */
                String gpt;
                try {
                    gpt = OpenAIAnalyzer.analyzeWithPrompt(prompt);
                    logManager.appendLog("LLM analysis done");
                } catch (Exception ex) {
                    gpt = "<span style='color:red;'>⚠ LLM error: " + ex.getMessage() + "</span>";
                    logManager.appendLog("LLM analysis failed: " + ex);
                }

                /* --- 4) Финальный HTML --- */
                return """
<html><body style='font-family: monospace;'>
<pre>%s</pre>
<h3>🤖 LLM Summary</h3>
<div style='max-width:600px; font-family: sans-serif;'>%s</div>
</body></html>""".formatted(local, gpt);
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

    /* ------------------------------------------------------------------ *
     *                        6.   В С П О М О Г А Т Е Л Ь Н О Е          *
     * ------------------------------------------------------------------ */

    /** Возвратить текущее значение спиннера. */
    int getSelectedCount() {
        return (Integer) spinner.getValue();
    }

    /** Создать маленькое контекстное меню Copy/Paste/Cut. */
    static void attachPopup(JTextField f) {
        JPopupMenu m = new JPopupMenu();
        JMenuItem c = new JMenuItem("Copy"), p = new JMenuItem("Paste"), x = new JMenuItem("Cut");
        c.addActionListener(e -> f.copy());
        p.addActionListener(e -> f.paste());
        x.addActionListener(e -> f.cut());
        m.add(c); m.add(p); m.add(x);
        f.setComponentPopupMenu(m);
    }

    /** Установить статус‑строку. */
    void setStatus(String s) { statusLbl.setText(s); }

    /** Маленький фабричный метод для кнопки‑пресета. */
    private JButton presetButton(String txt) {
        JButton b = new JButton(txt.replace("00000", "100 000"));  // тонкий трюк: добавляем пробел‑NARROW NBSP
        b.addActionListener(e -> spinner.setValue(Integer.parseInt(txt)));
        return b;
    }

    /** Показать HTML‑отчёт в модальном диалоге. */
    private void showAnalysisWindow(String html) {
        JEditorPane pane = new JEditorPane("text/html", html);
        pane.setEditable(false);
        JScrollPane scroll = new JScrollPane(pane);
        scroll.setPreferredSize(new Dimension(700, 450));

        JDialog dlg = new JDialog(this, "Analysis", false);
        dlg.getContentPane().add(scroll);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    /* ------------------------------------------------------------------ *
     *                    7.  P U B L I C   S T A R T E R                  *
     * ------------------------------------------------------------------ */

    /** Точка входа «как утилита». */
    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            RandomFetcherUI ui = new RandomFetcherUI(new LogManager());
            ui.initUI();
            ui.setVisible(true);
        });
    }
}
