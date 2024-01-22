package org.youtube;

import javax.swing.*;
// Класс App запускает GUI приложения.
// The App class starts the GUI of the application.
public class App {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            VideoDownloaderFrame frame = new VideoDownloaderFrame();
            frame.setVisible(true);
        });
    }

}
