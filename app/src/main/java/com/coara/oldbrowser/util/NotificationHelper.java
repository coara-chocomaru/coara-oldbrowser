package com.coara.oldbrowser.util;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

public final class NotificationHelper {
    private NotificationHelper() {
    }

    public static Notification.Builder builder(Context context, String channelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(context, channelId);
        }
        return new Notification.Builder(context);
    }

    public static NotificationManager manager(Context context) {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }
}
