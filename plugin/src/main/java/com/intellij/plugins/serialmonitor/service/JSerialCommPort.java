package com.intellij.plugins.serialmonitor.service;

import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.fazecast.jSerialComm.SerialPortInvalidPortException;
import com.intellij.plugins.serialmonitor.Parity;
import com.intellij.plugins.serialmonitor.SerialPortProfile;
import com.intellij.plugins.serialmonitor.StopBits;
import consulo.platform.Platform;
import consulo.serialMonitor.localize.SerialMonitorLocalize;
import jakarta.annotation.Nonnull;

import static com.fazecast.jSerialComm.SerialPort.*;

public class JSerialCommPort implements SerialPort {

    private final com.fazecast.jSerialComm.SerialPort serialPort;

    private JSerialCommPort(@Nonnull com.fazecast.jSerialComm.SerialPort port) {
        this.serialPort = port;
    }

    public static @Nonnull JSerialCommPort create(@Nonnull String systemPortName) throws SerialPortException {
        try {
            return new JSerialCommPort(com.fazecast.jSerialComm.SerialPort.getCommPort(systemPortName));
        }
        catch (SerialPortInvalidPortException e) {
            throw new SerialPortException(e.getMessage() != null ? e.getMessage() : "");
        }
    }

    private static String getSystemName(@Nonnull com.fazecast.jSerialComm.SerialPort port) {
        return Platform.current().os().isWindows() ? port.getSystemPortName() : port.getSystemPortPath();
    }

    @Override
    public @Nonnull String getSystemName() {
        return getSystemName(serialPort);
    }

    @Override
    public @Nonnull String getDescriptiveName() {
        return Platform.current().os().isWindows() ? serialPort.getPortDescription() : serialPort.getDescriptivePortName();
    }

    @Override
    public void connect(@Nonnull SerialPortProfile profile, @Nonnull SerialPortListener listener, boolean rts, boolean dtr) throws SerialPortException {
        checkSuccess(setRTSInternal(rts), () -> SerialMonitorLocalize.serialPortRtsInitFailed().get());
        checkSuccess(setDTRInternal(dtr), () -> SerialMonitorLocalize.serialPortDtrInitFailed().get());

        if (!addListener(listener)) {
            throw new SerialPortException(SerialMonitorLocalize.serialPortListenerFailed().get());
        }

        int portStopBits = convertStopBits(profile.getStopBits());
        int portParity = convertParity(profile.getParity());
        checkSuccess(
            serialPort.setComPortParameters(profile.getBaudRate(), profile.getBits(), portStopBits, portParity),
            () -> SerialMonitorLocalize.serialPortParametersWrong().get()
        );

        checkSuccess(serialPort.openPort(), () -> SerialMonitorLocalize.serialPortOpenFailed().get());
    }

    private int convertParity(@Nonnull Parity parity) {
        return switch (parity) {
            case EVEN -> EVEN_PARITY;
            case ODD -> ODD_PARITY;
            default -> NO_PARITY;
        };
    }

    private int convertStopBits(@Nonnull StopBits stopBits) {
        return switch (stopBits) {
            case BITS_2 -> TWO_STOP_BITS;
            case BITS_1_5 -> ONE_POINT_FIVE_STOP_BITS;
            default -> ONE_STOP_BIT;
        };
    }

    @Override
    public void disconnect() throws SerialPortException {
        checkSuccess(serialPort.closePort(),
            () -> SerialMonitorLocalize.portCloseError(serialPort.getSystemPortName(), "").get());
    }

    @Override
    public int write(byte[] data) {
        return serialPort.writeBytes(data, data.length);
    }

    @Override
    public void setRTS(boolean value) throws SerialPortException {
        checkSuccess(setRTSInternal(value), () -> SerialMonitorLocalize.serialPortRtsUpdateFailed().get());
    }

    @Override
    public void setDTR(boolean value) throws SerialPortException {
        checkSuccess(setDTRInternal(value), () -> SerialMonitorLocalize.serialPortDtrUpdateFailed().get());
    }

    private boolean setRTSInternal(boolean value) {
        return value ? serialPort.setRTS() : serialPort.clearRTS();
    }

    private boolean setDTRInternal(boolean value) {
        return value ? serialPort.setDTR() : serialPort.clearDTR();
    }

    @Override
    public boolean getCTS() {
        return serialPort.getCTS();
    }

    @Override
    public boolean getDSR() {
        return serialPort.getDSR();
    }

    public boolean addListener(@Nonnull SerialPortListener listener) {
        return serialPort.addDataListener(new SerialPortDataListener() {
            @Override
            public int getListeningEvents() {
                return LISTENING_EVENT_DATA_RECEIVED | LISTENING_EVENT_CTS | LISTENING_EVENT_DSR | LISTENING_EVENT_PORT_DISCONNECTED;
            }

            @Override
            public void serialEvent(SerialPortEvent event) {
                int eventType = event.getEventType();

                if ((eventType & LISTENING_EVENT_DATA_RECEIVED) != 0) {
                    listener.onDataReceived(event.getReceivedData());
                }
                if ((eventType & LISTENING_EVENT_CTS) != 0) {
                    listener.onCTSChanged(serialPort.getCTS());
                }
                if ((eventType & LISTENING_EVENT_DSR) != 0) {
                    listener.onDSRChanged(serialPort.getDSR());
                }
                if ((eventType & LISTENING_EVENT_PORT_DISCONNECTED) != 0) {
                    serialPort.closePort();
                }
            }
        });
    }

    private void checkSuccess(boolean success, @Nonnull java.util.function.Supplier<String> lazyMessage) throws SerialPortException {
        if (!success) {
            throw new SerialPortException(lazyMessage.get());
        }
    }
}
