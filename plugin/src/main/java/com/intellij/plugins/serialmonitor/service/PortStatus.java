package com.intellij.plugins.serialmonitor.service;

import consulo.serial.monitor.icon.SerialMonitorIconGroup;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;

public enum PortStatus {
    UNAVAILABLE(SerialMonitorIconGroup.invalid()),
    UNAVAILABLE_DISCONNECTED(SerialMonitorIconGroup.invalid()),
    BUSY(SerialMonitorIconGroup.invalid()),
    CONNECTING(SerialMonitorIconGroup.invalid()),
    DISCONNECTED(SerialMonitorIconGroup.connectpassive()),
    READY(Image.empty(Image.DEFAULT_ICON_SIZE)),
    CONNECTED(SerialMonitorIconGroup.connectactive());

    private final Image icon;

    PortStatus(@Nonnull Image icon) {
        this.icon = icon;
    }

    public @Nonnull Image getIcon() {
        return icon;
    }
}
