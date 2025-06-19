package org.videodownloader;

import javax.swing.*;
import java.awt.*;

// The App class starts the GUI of the application.
public class App {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            VideoDownloaderFrame frame = new VideoDownloaderFrame();
            frame.setVisible(true);
        });
    }


    /**
     * Размещает окно по центру того монитора, на котором в момент запуска находится курсор мыши.
     * Если курсор не определён (edge‑case — touch‑launch), используется стандартное центрирование.
     */
    private static void positionOnCurrentMonitor(JFrame frame) {
        PointerInfo pi = MouseInfo.getPointerInfo();
        if (pi == null) {                       // бывает при RDP или очень ранних стадиях запуска
            frame.setLocationRelativeTo(null);
            return;
        }

        Point mouse = pi.getLocation();
        GraphicsDevice targetDevice = null;
        for (GraphicsDevice dev : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            if (dev.getDefaultConfiguration().getBounds().contains(mouse)) {
                targetDevice = dev;
                break;
            }
        }

        if (targetDevice == null) {             // на всякий случай
            frame.setLocationRelativeTo(null);
            return;
        }

        Rectangle bounds = targetDevice.getDefaultConfiguration().getBounds();
        Dimension size = frame.getSize();

        int x = bounds.x + (bounds.width - size.width) / 2;
        int y = bounds.y + (bounds.height - size.height) / 2;
        frame.setLocation(x, y);
    }
}