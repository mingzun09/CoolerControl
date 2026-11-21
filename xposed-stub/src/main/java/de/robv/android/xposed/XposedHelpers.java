package de.robv.android.xposed;

public final class XposedHelpers {
    private XposedHelpers() {}
    public static Class<?> findClassIfExists(String className, ClassLoader classLoader) { return null; }
    public static Class<?> findClass(String className, ClassLoader classLoader) { return null; }
    public static void setStaticBooleanField(Class<?> clazz, String fieldName, boolean value) {}
    public static boolean getStaticBooleanField(Class<?> clazz, String fieldName) { return false; }
    public static void setObjectField(Object obj, String fieldName, Object value) {}
    public static Object getObjectField(Object obj, String fieldName) { return null; }
}
