package org.videodownloader;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.List;

/** Утилита для тестов: безопасно подменяет private static final List<T> */
final class TestUtil {
    private TestUtil() {}

    static <T> void replacePrivateStaticList(
            Class<?> target, String fieldName, List<T> newValue) throws Exception {

        Field f = target.getDeclaredField(fieldName);
        f.setAccessible(true);                 // получаем VarHandle
        VarHandle vh = MethodHandles.privateLookupIn(target, MethodHandles.lookup())
                .unreflectVarHandle(f);

        vh.setVolatile(null, newValue);        // null → для static-поля
    }
}
