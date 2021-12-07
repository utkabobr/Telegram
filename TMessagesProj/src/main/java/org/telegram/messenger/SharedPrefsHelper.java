package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPrefsHelper {
    private static SharedPreferences themeSharedPrefs;
    private static SharedPreferences systemConfigSharedPrefs;

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
}
