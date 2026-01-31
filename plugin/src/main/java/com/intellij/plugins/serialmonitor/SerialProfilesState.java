package com.intellij.plugins.serialmonitor;

import consulo.util.xml.serializer.annotation.AbstractCollection;
import consulo.util.xml.serializer.annotation.Tag;
import jakarta.annotation.Nonnull;

import java.util.HashMap;
import java.util.Map;

@Tag("serial-connections")
public class SerialProfilesState {

    @Tag("default")
    private SerialPortProfile defaultProfile;

    @AbstractCollection(elementTag = "serial-profile")
    private Map<String, SerialPortProfile> profiles = new HashMap<>();

    public SerialProfilesState() {
        this.defaultProfile = new SerialPortProfile();
        this.defaultProfile.setBaudRate(115200);
    }

    public SerialProfilesState(@Nonnull SerialPortProfile defaultProfile, @Nonnull Map<String, SerialPortProfile> profiles) {
        this.defaultProfile = defaultProfile;
        this.profiles = profiles;
    }

    public @Nonnull SerialPortProfile getDefaultProfile() {
        return defaultProfile;
    }

    public void setDefaultProfile(@Nonnull SerialPortProfile defaultProfile) {
        this.defaultProfile = defaultProfile;
    }

    public @Nonnull Map<String, SerialPortProfile> getProfiles() {
        return profiles;
    }

    public void setProfiles(@Nonnull Map<String, SerialPortProfile> profiles) {
        this.profiles = profiles;
    }

    public @Nonnull SerialProfilesState copy() {
        Map<String, SerialPortProfile> profilesCopy = new HashMap<>();
        for (Map.Entry<String, SerialPortProfile> entry : profiles.entrySet()) {
            profilesCopy.put(entry.getKey(), entry.getValue().copy());
        }
        return new SerialProfilesState(defaultProfile.copy(), profilesCopy);
    }
}
