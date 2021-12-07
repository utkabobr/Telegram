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
import android.os.Build;

public class BuildVars {

    public static boolean DEBUG_VERSION = false;
    public static boolean LOGS_ENABLED = false;
    public static boolean USE_CLOUD_STRINGS = true;
    public static boolean CHECK_UPDATES = true;
    public static boolean NO_SCOPED_STORAGE = Build.VERSION.SDK_INT <= 29;
    public static int BUILD_VERSION = BuildConfig.VERSION_CODE;
    public static String BUILD_VERSION_STRING = BuildConfig.VERSION_NAME;
    public static int APP_ID = BuildConfig.APP_ID;
    public static String APP_HASH = BuildConfig.APP_HASH;
    public static String APPCENTER_HASH = "a5b5c4f5-51da-dedc-9918-d9766a22ca7c";
    // PUBLIC
    public static boolean DEBUG_PRIVATE_VERSION = false;
   // public static String APPCENTER_HASH_DEBUG = "f9726602-67c9-48d2-b5d0-4761f1c1a8f3";
    // PRIVATE
    //public static boolean DEBUG_PRIVATE_VERSION = true;
    //public static String APPCENTER_HASH_DEBUG = DEBUG_PRIVATE_VERSION ? "29d0a6f1-b92f-493a-9fce-445681d767ec" : "f9726602-67c9-48d2-b5d0-4761f1c1a8f3";
    //
    public static String SMS_HASH = isStandaloneApp() ? "w0lkcmTZkKh" : (DEBUG_VERSION ? "O2P2z+/jBpJ" : "oLeq9AcOZkT");
    public static String PLAYSTORE_APP_URL = String.format("https://play.google.com/store/apps/details?id=%s", BuildConfig.APPLICATION_ID);

    static {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx != null) {
            LOGS_ENABLED = DEBUG_VERSION || SharedPrefsHelper.getSystemConfigSharedPrefs().getBoolean("logsEnabled", DEBUG_VERSION);
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
