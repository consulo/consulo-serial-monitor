package com.intellij.plugins.serialmonitor;

import consulo.util.xml.serializer.annotation.Attribute;
import consulo.util.xml.serializer.annotation.Tag;
import jakarta.annotation.Nonnull;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Tag("serial-profile")
public class SerialPortProfile {

    public static final List<Integer> STANDARD_BAUDS = Collections.unmodifiableList(Arrays.asList(
            300, 600, 1200, 2400, 4800, 9600, 19200, 28800, 38400, 57600, 76800, 115200, 128000,
            230400, 256000, 460800, 576000, 921600, 1024000
    ));

    public static final List<Integer> SERIAL_BITS = Collections.unmodifiableList(Arrays.asList(8, 7, 6, 5));

    @Attribute
    private String portName = "";

    @Attribute
    private int baudRate = 0;

    @Attribute
    private int bits = 8;

    @Attribute
    private StopBits stopBits = StopBits.BITS_1;

    @Attribute
    private Parity parity = Parity.NONE;

    @Attribute("new-line")
    private SerialProfileService.NewLine newLine = SerialProfileService.NewLine.CRLF;

    @Attribute
    private String encoding = StandardCharsets.US_ASCII.name();

    @Attribute
    private boolean localEcho = false;

    @Attribute
    private boolean showHardwareControls = false;

    public SerialPortProfile() {
    }

    public SerialPortProfile(@Nonnull String portName, int baudRate, int bits, @Nonnull StopBits stopBits,
                             @Nonnull Parity parity, @Nonnull SerialProfileService.NewLine newLine,
                             @Nonnull String encoding, boolean localEcho, boolean showHardwareControls) {
        this.portName = portName;
        this.baudRate = baudRate;
        this.bits = bits;
        this.stopBits = stopBits;
        this.parity = parity;
        this.newLine = newLine;
        this.encoding = encoding;
        this.localEcho = localEcho;
        this.showHardwareControls = showHardwareControls;
    }

    public String defaultName() {
        if (bits != 8 || stopBits != StopBits.BITS_1 || parity != Parity.NONE) {
            return portName + "-" + baudRate + "-" + bits + parity.shortName() + stopBits;
        } else {
            return portName + "-" + baudRate;
        }
    }

    public @Nonnull SerialPortProfile copy() {
        return new SerialPortProfile(portName, baudRate, bits, stopBits, parity, newLine, encoding, localEcho, showHardwareControls);
    }

    // Getters and Setters
    public String getPortName() {
        return portName;
    }

    public void setPortName(String portName) {
        this.portName = portName;
    }

    public int getBaudRate() {
        return baudRate;
    }

    public void setBaudRate(int baudRate) {
        this.baudRate = baudRate;
    }

    public int getBits() {
        return bits;
    }

    public void setBits(int bits) {
        this.bits = bits;
    }

    public @Nonnull StopBits getStopBits() {
        return stopBits;
    }

    public void setStopBits(@Nonnull StopBits stopBits) {
        this.stopBits = stopBits;
    }

    public @Nonnull Parity getParity() {
        return parity;
    }

    public void setParity(@Nonnull Parity parity) {
        this.parity = parity;
    }

    public @Nonnull SerialProfileService.NewLine getNewLine() {
        return newLine;
    }

    public void setNewLine(@Nonnull SerialProfileService.NewLine newLine) {
        this.newLine = newLine;
    }

    public @Nonnull String getEncoding() {
        return encoding;
    }

    public void setEncoding(@Nonnull String encoding) {
        this.encoding = encoding;
    }

    public boolean getLocalEcho() {
        return localEcho;
    }

    public void setLocalEcho(boolean localEcho) {
        this.localEcho = localEcho;
    }

    public boolean getShowHardwareControls() {
        return showHardwareControls;
    }

    public void setShowHardwareControls(boolean showHardwareControls) {
        this.showHardwareControls = showHardwareControls;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SerialPortProfile that = (SerialPortProfile) o;
        return baudRate == that.baudRate &&
               bits == that.bits &&
               localEcho == that.localEcho &&
               showHardwareControls == that.showHardwareControls &&
               Objects.equals(portName, that.portName) &&
               stopBits == that.stopBits &&
               parity == that.parity &&
               newLine == that.newLine &&
               Objects.equals(encoding, that.encoding);
    }

    @Override
    public int hashCode() {
        return Objects.hash(portName, baudRate, bits, stopBits, parity, newLine, encoding, localEcho, showHardwareControls);
    }
}
