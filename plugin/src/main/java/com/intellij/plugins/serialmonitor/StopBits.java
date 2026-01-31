package com.intellij.plugins.serialmonitor;

import consulo.localize.LocalizeValue;
import consulo.serialMonitor.localize.SerialMonitorLocalize;
import jakarta.annotation.Nonnull;

import java.util.function.Supplier;

public enum StopBits {
    BITS_1(SerialMonitorLocalize::uartStopbits1),
    BITS_2(SerialMonitorLocalize::uartStopbits2),
    BITS_1_5(SerialMonitorLocalize::uartStopbits15);

    private final Supplier<LocalizeValue> displayValue;

    StopBits(@Nonnull Supplier<LocalizeValue> displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public @Nonnull String toString() {
        return displayValue.get().get();
    }
}
