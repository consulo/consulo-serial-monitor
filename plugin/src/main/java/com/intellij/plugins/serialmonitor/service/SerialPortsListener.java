package com.intellij.plugins.serialmonitor.service;

import jakarta.annotation.Nonnull;

@FunctionalInterface
public interface SerialPortsListener {

    @Nonnull
    Class<SerialPortsListener> SERIAL_PORTS_TOPIC = SerialPortsListener.class;

    void portsStatusChanged();
}
