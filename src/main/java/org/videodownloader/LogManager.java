package org.videodownloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

/**
 * Класс для управления логами приложения.
 * Использует SLF4J/Logback для логирования и отображает логи в графическом окне.
 * Поддерживает ленивую инициализацию окна логов и запись логов в файл через Logback.
 */
public class LogManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogManager.class);
    private JFrame logFrame;
    private JTextArea logTextArea;

    /**
     * Конструктор класса LogManager.
     * Инициализирует начальные логи без создания окна (ленивая инициализация).
     */
    public LogManager() {
        LOGGER.info("LogManager initialized");
    }

    /**
     * Инициализирует графическое окно логов.
     * Создаёт окно с JTextArea для отображения логов и добавляет прокрутку.
     */
    private void initializeLogContent() {
        logFrame = new JFrame("Logs");
        logFrame.setSize(400, 300);
        logFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setLineWrap(true);
        logTextArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(logTextArea);
        logFrame.add(scrollPane, BorderLayout.CENTER);

        LOGGER.info("Log window initialized");
    }

    /**
     * Отображает окно логов.
     * Если окно ещё не создано, вызывает initializeLogContent().
     */
    public void showLogWindow() {
        if (logFrame == null) {
            initializeLogContent();
        }
        logFrame.setVisible(true);
        LOGGER.debug("Log window shown");
    }

    /**
     * Добавляет сообщение в текстовую область логов и записывает его через SLF4J.
     *
     * @param message сообщение для добавления
     */
    public void appendLog(String message) {
        if (logTextArea != null) {
            logTextArea.append(message + "\n");
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
        }
        LOGGER.info(message);
    }

}
