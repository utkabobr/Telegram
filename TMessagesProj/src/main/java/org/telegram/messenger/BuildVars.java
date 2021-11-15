/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

public class BuildVars {

    public static boolean DEBUG_VERSION = false;
    public static boolean LOGS_ENABLED = false;
    public static boolean DEBUG_PRIVATE_VERSION = false;
    public static boolean USE_CLOUD_STRINGS = true;
    public static boolean CHECK_UPDATES = true;
    public static boolean NO_SCOPED_STORAGE = Build.VERSION.SDK_INT <= 29;
    public static int BUILD_VERSION;
    public static String BUILD_VERSION_STRING;
    public static int APP_ID = BuildConfig.APP_ID;
    public static String APP_HASH = BuildConfig.APP_HASH;
    public static String SMS_HASH = isStandaloneApp() ? "w0lkcmTZkKh" : (DEBUG_VERSION ? "O2P2z+/jBpJ" : "oLeq9AcOZkT");
    public static String PLAYSTORE_APP_URL;

    static {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx != null) {
            SharedPreferences sharedPreferences = ctx.getSharedPreferences("systemConfig", Context.MODE_PRIVATE);
            LOGS_ENABLED = DEBUG_VERSION || sharedPreferences.getBoolean("logsEnabled", DEBUG_VERSION);

            PackageManager pm = ctx.getPackageManager();
            try {
                PackageInfo info = pm.getPackageInfo(ctx.getPackageName(), 0);
                BUILD_VERSION = info.versionCode;
                BUILD_VERSION_STRING = info.versionName;
            } catch (PackageManager.NameNotFoundException ignored) {}
            PLAYSTORE_APP_URL = String.format("https://play.google.com/store/apps/details?id=%s", ctx.getPackageName());
        }
    }

    @SuppressWarnings("ConstantConditions")
    public static boolean isStandaloneApp() {
        return BuildConfig.BUILD_TYPE.equals("standalone");
    }

    @SuppressWarnings("ConstantConditions")
    public static boolean isBetaApp() {
        return BuildConfig.BUILD_TYPE.equals("debug");
    }
}
