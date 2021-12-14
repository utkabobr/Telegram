package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPrefsHelper {
    private static SharedPreferences tabletSharedPrefs;
    private static SharedPreferences themeSharedPrefs;
    private static SharedPreferences systemConfigSharedPrefs;

    public static SharedPreferences getTabletSharedPrefs() {
        if (tabletSharedPrefs == null)
            tabletSharedPrefs = ApplicationLoader.applicationContext.getSharedPreferences("tabletConfig", Context.MODE_PRIVATE);
        return tabletSharedPrefs;
    }

    public static SharedPreferences getThemeSharedPrefs() {
        if (themeSharedPrefs == null)
            themeSharedPrefs = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Context.MODE_PRIVATE);
        return themeSharedPrefs;
    }

    public static SharedPreferences getSystemConfigSharedPrefs() {
        if (systemConfigSharedPrefs == null)
            systemConfigSharedPrefs = ApplicationLoader.applicationContext.getSharedPreferences("systemConfig", Context.MODE_PRIVATE);
        return systemConfigSharedPrefs;
    }

    public static class TabletPrefs {
        public static float getSideWidth() {
            return getTabletSharedPrefs().getFloat("sideWidth", 0.35f);
        }

        public static void setSideWidth(float w) {
            getTabletSharedPrefs().edit().putFloat("sideWidth", w).apply();
        }
    }
}
