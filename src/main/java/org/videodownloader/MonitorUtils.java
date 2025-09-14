package org.videodownloader;

import java.awt.*;

public final class MonitorUtils {
    private MonitorUtils() {}

    /** Возвращает устройство экрана, под которым сейчас курсор мыши. */
    public static GraphicsDevice deviceUnderMouse() {
        try {
            PointerInfo pi = MouseInfo.getPointerInfo();
            if (pi == null) {
                return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            }
            Point p = pi.getLocation();
            for (GraphicsDevice gd : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                Rectangle b = gd.getDefaultConfiguration().getBounds();
                if (b.contains(p)) return gd;
            }
        } catch (HeadlessException ignored) {}
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
    }

    /** Перемещает окно в центр монитора, где сейчас мышь. */
    public static void moveToMouseScreen(Window w) {
        GraphicsDevice gd = deviceUnderMouse();
        Rectangle b = gd.getDefaultConfiguration().getBounds();

        // Важно: окно должно иметь размер (setSize/pack уже вызваны)
        int ww = Math.max(w.getWidth(), 1);
        int wh = Math.max(w.getHeight(), 1);

        int x = b.x + (b.width  - ww) / 2;
        int y = b.y + (b.height - wh) / 2;
        w.setLocation(x, y);
    }
}
