package org.randomfetcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

/**
 * Менеджер логов приложения.
 *
 * <h2>Что делает класс</h2>
 * <ul>
 *     <li>Логирует сообщения через SLF4J / Logback (файл, консоль — зависит от конфигурации).</li>
 *     <li>При необходимости выводит сообщения в небольшое Swing‑окно.</li>
 *     <li>Создаёт UI лениво — ровно в момент первого показа.</li>
 *     <li>Потокобезопасен: любые модификации Swing‑компонентов выполняются в EDT.<br>
 *         И сами ссылки на компоненты объявлены <code>volatile</code> для корректной видимости.</li>
 *     <li>Ограничивает размер внутреннего текстового буфера (кольцевой усечение), чтобы окно
 *         не «распухало» на гигабайты при длительной работе.</li>
 * </ul>
 */
public final class LogManager {

    /* ------------------------------------------------------------------ *
     *                 1. П О Л Я   И   К О Н С Т А Н Т Ы                 *
     * ------------------------------------------------------------------ */

    /** Логгер (конкретный вывод задаётся logback.xml). */
    private static final Logger LOGGER = LoggerFactory.getLogger(LogManager.class);

    /** Максимальный объём текста в окне (≈ 100 кБ). */
    private static final int MAX_CHARS = 100_000;

    /** Окно‑контейнер. <br>volatile — чтобы другие потоки «увидели» созданный объект. */
    private volatile JFrame logFrame;

    /** Текстовая область для вывода логов. */
    private volatile JTextArea logTextArea;

    /* ------------------------------------------------------------------ *
     *                      2.   К О Н С Т Р У К Т О Р                    *
     * ------------------------------------------------------------------ */

    /** Создаём объект. Само окно пока не строим — это «ленивая» часть. */
    public LogManager() {
        LOGGER.info("LogManager initialized");
    }

    /* ------------------------------------------------------------------ *
     *             3.   В Н У Т Р Е Н Н И Е   М Е Т О Д Ы                 *
     * ------------------------------------------------------------------ */

    /**
     * Фактическое создание UI‑окна и всех его компонентов.
     * <p><strong>Должно запускаться в EDT!</strong><br>
     * Внешний код гарантирует это через {@link SwingUtilities#invokeAndWait(Runnable)} либо
     * прямой вызов, если уже находится в EDT.</p>
     */
    private void initializeLogContent() {
        /* ---------- создаём окно ---------- */
        logFrame = new JFrame("Logs");
        logFrame.setSize(400, 300);
        logFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        /* ---------- текстовая область ---------- */
        logTextArea = new JTextArea();
        logTextArea.setEditable(false);      // только просмотр
        logTextArea.setLineWrap(true);       // перенос строк
        logTextArea.setWrapStyleWord(true);  // перенос по словам

        /* ---------- прокрутка ---------- */
        JScrollPane scrollPane = new JScrollPane(logTextArea);
        logFrame.add(scrollPane, BorderLayout.CENTER);

        LOGGER.info("Log window initialized");
    }

    /* ------------------------------------------------------------------ *
     *                 4.   П У Б Л И Ч Н Ы Е   М Е Т О Д Ы               *
     * ------------------------------------------------------------------ */

    /**
     * Показать окно логов. <br>
     * Если приложение запущено в headless‑режиме (без графики), просто фиксируем событие и выходим.
     */
    public void showLogWindow() {
        // 1) Никакого GUI в headless
        if (GraphicsEnvironment.isHeadless()) {
            LOGGER.debug("Headless mode – UI disabled");
            return;
        }

        // 2) Создаём окно при первом вызове
        if (logFrame == null) {
            // Runnable, который реально строит окно
            Runnable initTask = this::initializeLogContent;

            // Вызываем корректно в EDT
            if (SwingUtilities.isEventDispatchThread()) {
                initTask.run();
            } else {
                try {
                    SwingUtilities.invokeAndWait(initTask);
                } catch (Exception e) {
                    LOGGER.error("Unable to init log window", e);
                    return;
                }
            }
        }

        // 3) Теперь окно точно существует — показываем
        logFrame.setVisible(true);
        logFrame.toFront();       // выводим поверх
        LOGGER.debug("Log window shown");
    }

    /**
     * Записать строку лога в файл/консоль и, при наличии UI, отобразить в окне.
     *
     * @param message текст без символа перевода строки
     */
    public void appendLog(String message) {
        /* ---------- 1. Пишем в системный лог (файл/консоль) ---------- */
        LOGGER.info(message);

        /* ---------- 2. UI‑часть: если окно ещё не создано — выходим ---- */
        if (logTextArea == null) return;

        /* ---------- 3. Обновление окна (должно быть в EDT) ------------ */
        Runnable updateUI = () -> {
            if (logTextArea == null) return;  // окно могли закрыть

            /* 3.1 Ограничиваем буфер:
                   если длина превышает лимит, обрезаем ~10 % старого текста от начала */
            int len = logTextArea.getDocument().getLength();
            if (len > MAX_CHARS) {
                try {
                    logTextArea.getDocument().remove(0, MAX_CHARS / 10);
                } catch (javax.swing.text.BadLocationException ignored) { }
            }

            /* 3.2 Проверяем, находился ли пользователь внизу текста.
                   Если да — автоскроллим; если листал вверх — caret не прыгает. */
            boolean atBottom = logTextArea.getCaretPosition() >= len - 1;

            /* 3.3 Добавляем новое сообщение */
            logTextArea.append(message + System.lineSeparator());

            if (atBottom) {
                logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
            }
        };

        // Вызываем updateUI в правильном потоке
        if (SwingUtilities.isEventDispatchThread()) {
            updateUI.run();
        } else {
            SwingUtilities.invokeLater(updateUI);
        }
    }
}
