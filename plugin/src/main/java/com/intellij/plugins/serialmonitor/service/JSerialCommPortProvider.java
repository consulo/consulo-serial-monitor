package com.intellij.plugins.serialmonitor.service;

import com.fazecast.jSerialComm.SerialPort;
import consulo.annotation.component.ServiceImpl;
import consulo.platform.Platform;
import jakarta.annotation.Nonnull;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Singleton
@ServiceImpl
public final class JSerialCommPortProvider implements SerialPortProvider {

    @Override
    public @Nonnull List<String> scanAvailablePorts() {
        SerialPort[] commPorts = SerialPort.getCommPorts();
        List<String> result = new ArrayList<>(commPorts.length);
        for (SerialPort port : commPorts) {
            result.add(Platform.current().os().isWindows() ? port.getSystemPortName() : port.getSystemPortPath());
        }
        return result;
    }

    @Override
    public @Nonnull com.intellij.plugins.serialmonitor.service.SerialPort createPort(@Nonnull String portName) throws SerialPortException {
        return JSerialCommPort.create(portName);
    }
}
