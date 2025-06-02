package org.videodownloader;

import javax.swing.*;

// The App class starts the GUI of the application.
public class App {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            VideoDownloaderFrame frame = new VideoDownloaderFrame();
            frame.setVisible(true);
        });
    }

}
