package org.videodownloader;

import java.lang.reflect.Field;
import java.util.List;

final class TestUtil {
    /** Подменяет неизменяемое static List в целевом классе. */
    @SuppressWarnings("unchecked")
    static void replacePrivateStaticList(Class<?> target, String field, List<?> newValue) {
        try {
            Field f = target.getDeclaredField(field);
            f.setAccessible(true);
            f.set(null, newValue);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
