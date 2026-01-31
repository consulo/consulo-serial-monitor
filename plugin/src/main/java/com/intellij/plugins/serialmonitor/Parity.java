package com.intellij.plugins.serialmonitor;

import consulo.localize.LocalizeValue;
import consulo.serialMonitor.localize.SerialMonitorLocalize;
import jakarta.annotation.Nonnull;

import java.util.function.Supplier;

public enum Parity {
    ODD(SerialMonitorLocalize::uartParityOdd),
    EVEN(SerialMonitorLocalize::uartParityEven),
    NONE(SerialMonitorLocalize::uartParityNone);

    private final Supplier<LocalizeValue> displayValue;

    Parity(@Nonnull Supplier<LocalizeValue> displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public @Nonnull String toString() {
        return displayValue.get().get();
    }

    public @Nonnull String shortName() {
        return String.valueOf(toString().charAt(0)).toUpperCase();
    }
}
