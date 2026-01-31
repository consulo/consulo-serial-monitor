package com.intellij.plugins.serialmonitor.service;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import jakarta.annotation.Nonnull;

import java.util.List;

@ServiceAPI(ComponentScope.APPLICATION)
public interface SerialPortProvider {
    /**
     * Return the list of available serial ports.
     * To connect to the ports, use the {@link #createPort} method.
     */
    @Nonnull List<String> scanAvailablePorts();

    /**
     * Creates a {@link SerialPort} handle for a given port name.
     *
     * @throws SerialPortException if the port is not available.
     */
    @Nonnull SerialPort createPort(@Nonnull String portName) throws SerialPortException;
}
