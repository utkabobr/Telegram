package org.telegram.messenger;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.net.wifi.WifiManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.cast.SessionState;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.SessionTransferCallback;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CastManager {
    private static Executor executor = Executors.newSingleThreadExecutor();
    private static boolean available;

    // We're using Application context
    @SuppressLint("StaticFieldLeak")
    private static CastContext castContext;

    private static String myIP;
    private static CastMediaServer mediaServer;

    public static void init() {
        if (!PushListenerController.GooglePushListenerServiceProvider.INSTANCE.hasServices()) return;

        try {
            CastContext.getSharedInstance(ApplicationLoader.applicationContext, executor).addOnCompleteListener(command -> {
                if (command.isSuccessful()) {
                    onCastInitialized(command.getResult());
                    available = true;
                } else {
                    available = false;
                }
                AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.castStateUpdated));
            });
        } catch (Exception e) {
            available = false;
        }
    }

    private static void onCastInitialized(CastContext ctx) {
        castContext = ctx;
        castContext.getSessionManager().addSessionManagerListener(new SessionManagerListener<Session>() {
            @Override
            public void onSessionEnded(@NonNull Session session, int error) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("Cast session ended: " + session + ", " + error);
                }
                AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.castSessionUpdated));
            }

            @Override
            public void onSessionEnding(@NonNull Session session) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("Cast session ending: " + session);
                }
                if (mediaServer != null) {
                    mediaServer.stop();
                    mediaServer = null;
                }
                AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.castSessionUpdated));
            }

            @Override
            public void onSessionResumeFailed(@NonNull Session session, int error) {}

            @Override
            public void onSessionResumed(@NonNull Session session, boolean wasSuspended) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("Cast session resumed: " + session + ", " + wasSuspended);
                }
                AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.castSessionUpdated));
            }

            @Override
            public void onSessionResuming(@NonNull Session session, @NonNull String sessionId) {}

            @Override
            public void onSessionStartFailed(@NonNull Session session, int error) {
                AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.castStartFailed));
            }

            @Override
            public void onSessionStarted(@NonNull Session session, @NonNull String sessionId) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("Cast session started: " + session + ", " + sessionId);
                }
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.castSessionUpdated);
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.castNeedSwapToRemote);
                });
            }

            @Override
            public void onSessionStarting(@NonNull Session session) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("Cast session starting: " + session);
                }
                myIP = getWifiIP();
                if (mediaServer == null) {
                    mediaServer = new CastMediaServer(0);
                    try {
                        mediaServer.start();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.castSessionUpdated));
            }

            @Override
            public void onSessionSuspended(@NonNull Session session, int reason) {}
        });
        castContext.addSessionTransferCallback(new SessionTransferCallback() {
            @Override
            public void onTransferFailed(int transferType, int reason) {}

            @Override
            public void onTransferred(int transferType, @NonNull SessionState sessionState) {
//                if (transferType == SessionTransferCallback.TRANSFER_TYPE_FROM_REMOTE_TO_LOCAL) {
//                    MediaLoadRequestData data = sessionState.getLoadRequestData();
//                    if (data != null) {
//                        MediaInfo info = data.getMediaInfo();
//                        if (info != null) {
//                            long pos = data.getCurrentTime();
//                            // TODO: Maybe resume playback on a local device?
//                        }
//                    }
//                }
            }
        });
    }

    private static String getWifiIP() {
        WifiManager wifiManager = (WifiManager) ApplicationLoader.applicationContext.getSystemService(Context.WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        String ipAddressString;
        try {
            ipAddressString = InetAddress.getByAddress(BigInteger.valueOf(ipAddress).toByteArray()).getHostAddress();
        } catch (UnknownHostException ex) {
            FileLog.e("Failed to get IP address for cast");
            ipAddressString = null;
        }

        return ipAddressString;
    }

    public static Uri serve(Uri uri) {
        return mediaServer.serve(myIP, uri);
    }

    public static void stopCast() {
        if (castContext != null) {
            castContext.getSessionManager().endCurrentSession(true);
        }
    }

    public static boolean isCasting() {
        Session session = getCastSession();
        return session != null && (session.isConnecting() || session.isConnected());
    }

    public static CastContext getCastContext() {
        return castContext;
    }

    @Nullable
    public static Session getCastSession() {
        return castContext != null ? castContext.getSessionManager().getCurrentSession() : null;
    }

    public static boolean isCastAvailable() {
        return available;
    }
}
