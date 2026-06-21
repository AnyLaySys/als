package com.termux.x11.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

import java.lang.reflect.Method;

public class SamsungDexUtils {
    private static Method requestMetaKeyEventMethod;
    private static Object manager;

    static {
        try {
            Class<?> clazz = Class.forName("com.samsung.android.view.SemWindowManager");
            Method obtain = clazz.getMethod("getInstance");
            requestMetaKeyEventMethod = clazz.getDeclaredMethod("requestMetaKeyEvent", android.content.ComponentName.class, boolean.class);
            manager = obtain.invoke(null);
        } catch (Exception ignored) {
            requestMetaKeyEventMethod = null;
            manager = null;
        }
    }

    static public boolean available() {
        return requestMetaKeyEventMethod != null && manager != null;
    }

    static public void dexMetaKeyCapture(Activity activity, boolean enable) {
        if (!available())
            return;

        try {
            requestMetaKeyEventMethod.invoke(manager, activity.getComponentName(), enable);
        } catch (Exception it) {
        }
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    public static boolean checkDeXEnabled(Context ctx) {
        Configuration config = ctx.getResources().getConfiguration();
        try {
            Class<?> c = config.getClass();
            return c.getField("SEM_DESKTOP_MODE_ENABLED").getInt(c)
                    == c.getField("semDesktopModeEnabled").getInt(config);
        } catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException ignored) {}
        return false;
    }
}
