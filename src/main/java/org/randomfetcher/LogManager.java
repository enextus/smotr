package org.randomfetcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

/**
 * Менеджер логов приложения.
 *
 * === RU ===
 * Основные функции:
 *  - Записывает сообщения в лог (консоль/файл, в зависимости от SLF4J конфигурации).
 *  - Может показывать отдельное Swing-окно с текстовой областью логов.
 *  - UI создаётся "лениво" — только при первом вызове showLogWindow().
 *  - Обеспечивает потокобезопасность для UI.
 *  - Хранит backlog — сообщения, пришедшие до появления окна, и отображает их при инициализации.
 *  - Ограничивает объём буфера, чтобы окно не «распухало».
 *
 * === EN ===
 * Main responsibilities:
 *  - Logs messages to console or file via SLF4J (Logback-configurable).
 *  - Optionally shows a Swing window displaying live log messages.
 *  - UI is lazily initialized — only when showLogWindow() is first called.
 *  - Ensures thread-safety for Swing components.
 *  - Maintains a backlog of messages received before the window exists and flushes them upon creation.
 *  - Limits text area buffer to avoid memory bloat.
 */
public final class LogManager {

    // -----------------------------------------------------------------------
    // 1. ПОЛЯ И КОНСТАНТЫ / FIELDS AND CONSTANTS
    // -----------------------------------------------------------------------

    /** SLF4J логгер — вывод идёт по настройке logback.xml. */
    private static final Logger LOGGER = LoggerFactory.getLogger(LogManager.class);

    /** Максимальное количество символов в текстовой области (~100 кБ). */
    private static final int MAX_CHARS = 100_000;

    /** Основное окно логов. Volatile — чтобы другие потоки "увидели" его. */
    private volatile JFrame logFrame;

    /** Swing-компонент: область, куда пишутся сообщения. */
    private volatile JTextArea logTextArea;

    /**
     * Буфер сообщений, пришедших до появления окна.
     * synchronizedList — для безопасной работы из разных потоков.
     * Эти строки будут отображены при первом создании UI.
     */
    private final List<String> backlog =
            Collections.synchronizedList(new ArrayList<>());

    // -----------------------------------------------------------------------
    // 2. КОНСТРУКТОР / CONSTRUCTOR
    // -----------------------------------------------------------------------

    /**
     * Конструктор. UI ещё не создаётся — он ленивый.
     * Просто логгирует и готовит внутренние поля.
     */
    public LogManager() {
        LOGGER.info("LogManager initialized");
    }

    // -----------------------------------------------------------------------
    // 3. ВНУТРЕННИЕ МЕТОДЫ / INTERNAL METHODS
    // -----------------------------------------------------------------------

    /**
     * Инициализация UI-компонентов: создаёт окно, текстовую область и заливает backlog.
     *
     * === RU ===
     * Метод **должен** вызываться из EDT (Event Dispatch Thread).
     * Обычно вызывается через invokeAndWait() из showLogWindow().
     *
     * === EN ===
     * This method **must** run on the EDT.
     * It is typically invoked via invokeAndWait() inside showLogWindow().
     */
    private void initializeLogContent() {
        // 1. Создаём Swing-окно
        logFrame = new JFrame("Logs");
        logFrame.setSize(400, 300);
        logFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        // 2. Текстовая область (не редактируемая)
        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setLineWrap(true);
        logTextArea.setWrapStyleWord(true);

        // 3. Добавляем прокрутку
        JScrollPane scrollPane = new JScrollPane(logTextArea);
        logFrame.add(scrollPane, BorderLayout.CENTER);

        // 4. Выводим накопленные строки backlog (если были)
        backlog.forEach(s -> logTextArea.append(s + System.lineSeparator()));
        backlog.clear();

        // 5. Автопрокрутка вниз (чтобы сразу видеть последние строки)
        SwingUtilities.invokeLater(() ->
                logTextArea.setCaretPosition(logTextArea.getDocument().getLength())
        );

        LOGGER.info("Log window initialized");
    }

    // -----------------------------------------------------------------------
    // 4. ПУБЛИЧНЫЕ МЕТОДЫ / PUBLIC METHODS
    // -----------------------------------------------------------------------

    /**
     * Показывает окно логов.
     * Если графики нет (например, серверный режим) — ничего не делает.
     */
    public void showLogWindow() {
        // 1. Проверяем режим без графики
        if (GraphicsEnvironment.isHeadless()) {
            LOGGER.debug("Headless mode – UI disabled");
            return;
        }

        // 2. Создаём окно при первом вызове
        if (logFrame == null) {
            Runnable initTask = this::initializeLogContent;

            if (SwingUtilities.isEventDispatchThread()) {
                // Уже в EDT — можно сразу вызывать
                initTask.run();
            } else {
                try {
                    // Иначе безопасно передаём в EDT
                    SwingUtilities.invokeAndWait(initTask);
                } catch (Exception e) {
                    LOGGER.error("Unable to init log window", e);
                    return;
                }
            }
        }

        // 3. Показываем окно
        logFrame.setVisible(true);
        logFrame.toFront(); // Поверх всех
        LOGGER.debug("Log window shown");
    }

    /**
     * Записывает строку в лог (файл/консоль) и в UI (если он существует).
     *
     * @param message строка без символа новой строки (он добавляется автоматически)
     */
    public void appendLog(String message) {
        // 1. Запись в основной логгер
        LOGGER.info(message);

        // 2. Если окно ещё не создано — добавляем в backlog и выходим
        if (logTextArea == null) {
            backlog.add(message);
            return;
        }

        // 3. Готовим обновление UI
        Runnable updateUI = () -> {
            if (logTextArea == null) return; // Окно могли закрыть

            // 3.1 Ограничиваем буфер (обрезаем ~10% при переполнении)
            int len = logTextArea.getDocument().getLength();
            if (len > MAX_CHARS) {
                try {
                    logTextArea.getDocument().remove(0, MAX_CHARS / 10);
                } catch (javax.swing.text.BadLocationException ignored) {
                    // Ничего страшного — просто пропустим
                }
            }

            // 3.2 Проверяем, был ли пользователь внизу окна
            boolean atBottom = logTextArea.getCaretPosition() >= len - 1;

            // 3.3 Добавляем новую строку
            logTextArea.append(message + System.lineSeparator());

            // 3.4 Автопрокрутка вниз
            if (atBottom) {
                logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
            }
        };

        // 4. Запускаем обновление в правильном потоке
        if (SwingUtilities.isEventDispatchThread()) {
            updateUI.run();
        } else {
            SwingUtilities.invokeLater(updateUI);
        }
    }

}
