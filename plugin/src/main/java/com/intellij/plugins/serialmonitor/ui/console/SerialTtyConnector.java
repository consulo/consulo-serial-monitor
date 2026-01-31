package com.intellij.plugins.serialmonitor.ui.console;

import com.intellij.plugins.serialmonitor.service.SerialPortService;
import com.jediterm.terminal.Questioner;
import com.jediterm.terminal.TtyConnector;
import jakarta.annotation.Nonnull;

import java.awt.*;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class SerialTtyConnector implements TtyConnector {

    private final JeditermConsoleView consoleView;
    private final SerialPortService.SerialConnection connection;
    private Charset charset = StandardCharsets.US_ASCII;
    private boolean localEcho = false;

    public SerialTtyConnector(@Nonnull JeditermConsoleView consoleView, @Nonnull SerialPortService.SerialConnection connection) {
        this.consoleView = consoleView;
        this.connection = connection;
    }

    public @Nonnull Charset getCharset() {
        return charset;
    }

    public void setCharset(@Nonnull Charset charset) {
        this.charset = charset;
    }

    public boolean isLocalEcho() {
        return localEcho;
    }

    public void setLocalEcho(boolean localEcho) {
        this.localEcho = localEcho;
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        return consoleView.readChars(buf, offset, length);
    }

    @Override
    public void write(byte[] bytes) {
        connection.write(bytes);
    }

    @Override
    public void write(@Nonnull String string) {
        write(string.getBytes(charset));
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public int waitFor() {
        return 0;
    }

    @Override
    public @Nonnull String getName() {
        return connection.getPortName();
    }

    @Override
    public boolean init(Questioner questioner) {
        return false;
    }

    @Override
    public void close() {
    }

    @Override
    public void resize(Dimension dimension, Dimension dimension1) {

    }
}
