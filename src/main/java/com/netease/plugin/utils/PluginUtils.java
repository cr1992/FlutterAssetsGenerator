package com.netease.plugin.utils;

import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;

public class PluginUtils {
    public static void showNotify(String message) {
        NotificationGroup notificationGroup = new NotificationGroup("Assets generator", NotificationDisplayType.BALLOON, true);
        ApplicationManager.getApplication().invokeLater(() -> {
            Notification notification = notificationGroup.createNotification(message, NotificationType.INFORMATION);
            Notifications.Bus.notify(notification);
        });
    }
}
