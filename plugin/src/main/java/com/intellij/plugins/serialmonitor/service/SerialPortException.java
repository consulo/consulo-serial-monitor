package com.intellij.plugins.serialmonitor.service;

import jakarta.annotation.Nonnull;

public class SerialPortException extends Exception {
    public SerialPortException(@Nonnull String message) {
        super(message);
    }
}
