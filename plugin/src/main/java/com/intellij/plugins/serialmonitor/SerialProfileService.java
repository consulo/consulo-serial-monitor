package com.intellij.plugins.serialmonitor;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.RoamingType;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.localize.LocalizeValue;
import consulo.serialMonitor.localize.SerialMonitorLocalize;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@Singleton
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
@State(name = "SerialPortProfiles",
       storages = @Storage(value = "serial-port-profiles.xml", roamingType = RoamingType.PER_OS))
public final class SerialProfileService implements PersistentStateComponent<SerialProfilesState> {

    private volatile SerialProfilesState myState = new SerialProfilesState();

    public static @Nonnull SerialProfileService getInstance() {
        return Application.get().getInstance(SerialProfileService.class);
    }

    @Override
    public @Nonnull SerialProfilesState getState() {
        return myState;
    }

    @Override
    public void loadState(@Nonnull SerialProfilesState state) {
        myState = state;
    }

    public void setDefaultProfile(@Nonnull SerialPortProfile defaultProfile) {
        SerialProfilesState newState = myState.copy();
        newState.setDefaultProfile(defaultProfile);
        myState = newState;
    }

    public void setProfiles(@Nonnull Map<String, SerialPortProfile> profiles) {
        SerialProfilesState newState = myState.copy();
        newState.setProfiles(new HashMap<>(profiles));
        myState = newState;
    }

    public @Nonnull Map<String, SerialPortProfile> getProfiles() {
        return myState.getProfiles();
    }

    public @Nonnull SerialPortProfile copyDefaultProfile(@Nullable String portName) {
        SerialPortProfile profile = myState.getDefaultProfile().copy();
        if (portName != null) {
            profile.setPortName(portName);
        }
        return profile;
    }

    public int defaultBaudRate() {
        return myState.getDefaultProfile().getBaudRate();
    }

    public enum NewLine {
        CR(SerialMonitorLocalize::uartNewlineCr, "\r"),
        LF(SerialMonitorLocalize::uartNewlineLf, "\n"),
        CRLF(SerialMonitorLocalize::uartNewlineCrlf, "\r\n");

        private final Supplier<LocalizeValue> displayValue;
        private final String value;

        NewLine(@Nonnull Supplier<LocalizeValue> displayValue, @Nonnull String value) {
            this.displayValue = displayValue;
            this.value = value;
        }

        public @Nonnull String getValue() {
            return value;
        }

        @Override
        public @Nonnull String toString() {
            return displayValue.get().get();
        }
    }
}
