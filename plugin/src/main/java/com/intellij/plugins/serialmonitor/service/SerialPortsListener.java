package com.intellij.plugins.serialmonitor.service;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.TopicAPI;
import jakarta.annotation.Nonnull;

@TopicAPI(ComponentScope.APPLICATION)
public interface SerialPortsListener {

    @Nonnull
    Class<SerialPortsListener> SERIAL_PORTS_TOPIC = SerialPortsListener.class;

    void portsStatusChanged();
}
