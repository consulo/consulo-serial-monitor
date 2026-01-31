package com.intellij.plugins.serialmonitor.service;

import com.intellij.plugins.serialmonitor.SerialPortProfile;
import jakarta.annotation.Nonnull;

public interface SerialPort {

    @Nonnull String getSystemName();

    @Nonnull String getDescriptiveName();

    void connect(@Nonnull SerialPortProfile profile, @Nonnull SerialPortListener listener, boolean rts, boolean dtr) throws SerialPortException;

    void disconnect() throws SerialPortException;

    int write(byte[] data) throws SerialPortException;

    void setRTS(boolean value) throws SerialPortException;

    void setDTR(boolean value) throws SerialPortException;

    boolean getCTS() throws SerialPortException;

    boolean getDSR() throws SerialPortException;

    interface SerialPortListener {
        default void onDataReceived(byte[] data) {}
        default void onCTSChanged(boolean state) {}
        default void onDSRChanged(boolean state) {}
    }
}
