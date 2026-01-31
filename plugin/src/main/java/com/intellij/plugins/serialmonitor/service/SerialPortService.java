package com.intellij.plugins.serialmonitor.service;

import com.intellij.plugins.serialmonitor.SerialMonitorException;
import com.intellij.plugins.serialmonitor.SerialPortProfile;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.serialMonitor.localize.SerialMonitorLocalize;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Singleton
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public final class SerialPortService implements Disposable {

    private static final Logger LOG = Logger.getInstance(SerialPortService.class);

    private volatile Set<String> portNames = Collections.emptySet();
    private final Map<String, SerialConnection> connections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final Comparator<String> NAME_COMPARATOR = (name1, name2) -> {
        int[] split1 = splitName(name1);
        int[] split2 = splitName(name2);
        String base1 = name1.substring(0, split1[0]);
        String base2 = name2.substring(0, split2[0]);
        int result = String.CASE_INSENSITIVE_ORDER.compare(base1, base2);
        return result == 0 ? split1[1] - split2[1] : result;
    };

    public static @Nonnull SerialPortService getInstance() {
        return Application.get().getInstance(SerialPortService.class);
    }

    public SerialPortService() {
        scheduler.scheduleWithFixedDelay(this::rescanPorts, 0, 500, TimeUnit.MILLISECONDS);
    }

    private int[] splitName(@Nonnull String s) {
        int digitIdx = s.length() - 1;
        Integer num = null;
        int mul = 1;
        while (digitIdx >= 0 && Character.isDigit(s.charAt(digitIdx))) {
            num = (num == null ? 0 : num) + mul * (s.charAt(digitIdx) - '0');
            mul *= 10;
            digitIdx--;
        }
        // Order entries without a number before ones with a number
        return new int[]{digitIdx + 1, num == null ? -1 : num};
    }

    private @Nonnull Set<String> scanPorts() {
        SerialPortProvider portProvider = Application.get().getInstance(SerialPortProvider.class);
        try {
            List<String> availablePorts = portProvider.scanAvailablePorts();
            TreeSet<String> result = new TreeSet<>(NAME_COMPARATOR);
            result.addAll(availablePorts);
            return result;
        } catch (Exception e) {
            LOG.warn("Failed to scan ports", e);
            return Collections.emptySet();
        }
    }

    private void rescanPorts() {
        Set<String> portList = scanPorts();
        Set<String> oldPorts = portNames;

        for (String name : oldPorts) {
            if (!portList.contains(name)) {
                // port disappeared
                SerialConnection connection = connections.get(name);
                if (connection != null) {
                    connection.closeSilently(false);
                }
            }
        }

        for (String name : portList) {
            SerialConnection connection = connections.get(name);
            if (connection != null && connection.getStatus() == PortStatus.UNAVAILABLE_DISCONNECTED) {
                connection.setStatus(PortStatus.DISCONNECTED);
            }
        }

        portNames = portList;
        portMessageTopic().portsStatusChanged();
    }

    private @Nonnull SerialPortsListener portMessageTopic() {
        return Application.get().getMessageBus().syncPublisher(SerialPortsListener.SERIAL_PORTS_TOPIC);
    }

    public @Nonnull Set<String> getPortsNames() {
        return portNames;
    }

    public @Nonnull SerialConnection newConnection(@Nonnull String portName) {
        SerialConnection existing = connections.get(portName);
        if (existing != null) {
            Disposer.dispose(existing);
        }
        SerialConnection serialConnection = new SerialConnection(portName);
        connections.put(portName, serialConnection);
        return serialConnection;
    }

    public @Nonnull PortStatus portStatus(@Nonnull String portName) {
        if (!portNames.contains(portName)) {
            return connections.containsKey(portName) ? PortStatus.UNAVAILABLE_DISCONNECTED : PortStatus.UNAVAILABLE;
        }
        SerialConnection connection = connections.get(portName);
        return connection != null ? connection.getStatus() : PortStatus.READY;
    }

    public @Nullable String portDescriptiveName(@Nonnull String portName) {
        try {
            SerialPortProvider provider = Application.get().getInstance(SerialPortProvider.class);
            return provider.createPort(portName).getDescriptiveName();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void dispose() {
        scheduler.shutdownNow();
    }

    public class SerialConnection implements Disposable {
        private final String portName;

        private Consumer<byte[]> dataListener;
        private Consumer<Boolean> dsrListener;
        private Consumer<Boolean> ctsListener;

        private volatile SerialPort port;
        private volatile PortStatus status = PortStatus.DISCONNECTED;
        private boolean localEcho = false;
        private boolean rts = true;
        private boolean dtr = true;

        public SerialConnection(@Nonnull String portName) {
            this.portName = portName;
        }

        public String getPortName() {
            return portName;
        }

        public void setDataListener(@Nullable Consumer<byte[]> dataListener) {
            this.dataListener = dataListener;
        }

        public void setDsrListener(@Nullable Consumer<Boolean> dsrListener) {
            this.dsrListener = dsrListener;
        }

        public void setCtsListener(@Nullable Consumer<Boolean> ctsListener) {
            this.ctsListener = ctsListener;
        }

        public boolean getRts() {
            return rts;
        }

        public void setRts(boolean value) throws SerialMonitorException {
            try {
                if (port != null) {
                    port.setRTS(value);
                }
                rts = value;
            } catch (SerialPortException e) {
                throw new SerialMonitorException(SerialMonitorLocalize.portModifyError(portName, e.getMessage()).get());
            }
        }

        public boolean getDtr() {
            return dtr;
        }

        public void setDtr(boolean value) throws SerialMonitorException {
            try {
                if (port != null) {
                    port.setDTR(value);
                }
                dtr = value;
            } catch (SerialPortException e) {
                throw new SerialMonitorException(SerialMonitorLocalize.portModifyError(portName, e.getMessage()).get());
            }
        }

        public boolean getCts() {
            try {
                return port != null && port.getCTS();
            } catch (SerialPortException e) {
                LOG.info("Failed to get CTS", e);
                return false;
            }
        }

        public boolean getDsr() {
            try {
                return port != null && port.getDSR();
            } catch (SerialPortException e) {
                LOG.info("Failed to get DSR", e);
                return false;
            }
        }

        @Override
        public void dispose() {
            closeSilently(true);
            connections.remove(portName, this);
            Application.get().executeOnPooledThread(SerialPortService.this::rescanPorts);
        }

        public @Nonnull PortStatus getStatus() {
            return status;
        }

        void setStatus(@Nonnull PortStatus value) {
            status = value;
        }

        public void close(boolean portAvailable) throws SerialMonitorException {
            try {
                if (port != null) {
                    port.disconnect();
                }
            } catch (SerialPortException e) {
                throw new SerialMonitorException(SerialMonitorLocalize.portCloseError(portName, e.getMessage()).get());
            } finally {
                status = portAvailable ? PortStatus.DISCONNECTED : PortStatus.UNAVAILABLE_DISCONNECTED;
                port = null;
                portMessageTopic().portsStatusChanged();
            }
        }

        public void closeSilently(boolean portAvailable) {
            try {
                close(portAvailable);
            } catch (SerialMonitorException ignored) {
            }
        }

        private final SerialPort.SerialPortListener listener = new SerialPort.SerialPortListener() {
            @Override
            public void onDataReceived(byte[] data) {
                Consumer<byte[]> listener = dataListener;
                if (listener != null) {
                    listener.accept(data);
                }
            }

            @Override
            public void onCTSChanged(boolean state) {
                Application.get().invokeLater(() -> {
                    Consumer<Boolean> listener = ctsListener;
                    if (listener != null) {
                        listener.accept(state);
                    }
                });
            }

            @Override
            public void onDSRChanged(boolean state) {
                Application.get().invokeLater(() -> {
                    Consumer<Boolean> listener = dsrListener;
                    if (listener != null) {
                        listener.accept(state);
                    }
                });
            }
        };

        public void connect(@Nonnull SerialPortProfile profile) throws SerialMonitorException {
            this.status = PortStatus.CONNECTING;
            this.localEcho = profile.getLocalEcho();

            SerialPort newPort = null;
            try {
                SerialPortProvider provider = Application.get().getInstance(SerialPortProvider.class);
                newPort = provider.createPort(portName);
                portMessageTopic().portsStatusChanged();

                newPort.connect(profile, listener, rts, dtr);

                port = newPort;
                status = PortStatus.CONNECTED;
                portMessageTopic().portsStatusChanged();
            } catch (Exception e) {
                if (newPort != null) {
                    try {
                        newPort.disconnect();
                    } catch (SerialPortException ignored) {
                    }
                }

                status = PortStatus.UNAVAILABLE_DISCONNECTED;
                portMessageTopic().portsStatusChanged();

                throw new SerialMonitorException(SerialMonitorLocalize.portConnectError(portName, e.getMessage()).get());
            }
        }

        public void write(byte[] data) {
            SerialPort currentPort = port;
            if (currentPort != null) {
                try {
                    currentPort.write(data);
                } catch (SerialPortException e) {
                    LOG.warn("Failed to write to port", e);
                }
            }
            if (localEcho) {
                Consumer<byte[]> listener = dataListener;
                if (listener != null) {
                    listener.accept(data);
                }
            }
        }
    }
}
