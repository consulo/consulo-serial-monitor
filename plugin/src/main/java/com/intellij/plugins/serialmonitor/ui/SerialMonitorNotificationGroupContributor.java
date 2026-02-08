package com.intellij.plugins.serialmonitor.ui;

import consulo.annotation.component.ExtensionImpl;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationGroupContributor;
import jakarta.annotation.Nonnull;

import java.util.function.Consumer;

/**
 * @author VISTALL
 * @since 2026-02-08
 */
@ExtensionImpl
public class SerialMonitorNotificationGroupContributor implements NotificationGroupContributor {
    @Override
    public void contribute(@Nonnull Consumer<NotificationGroup> consumer) {
        consumer.accept(SerialMonitor.NOTIFICATION_GROUP);
    }
}
