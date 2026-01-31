package com.intellij.plugins.serialmonitor.ui;

import com.intellij.plugins.serialmonitor.Parity;
import com.intellij.plugins.serialmonitor.SerialPortProfile;
import com.intellij.plugins.serialmonitor.SerialProfileService;
import com.intellij.plugins.serialmonitor.StopBits;
import com.intellij.plugins.serialmonitor.service.PortStatus;
import com.intellij.plugins.serialmonitor.service.SerialPortService;
import consulo.application.ApplicationPropertiesComponent;
import consulo.disposer.Disposable;
import consulo.serialMonitor.localize.SerialMonitorLocalize;
import consulo.ui.Hyperlink;
import consulo.ui.ex.InputValidator;
import consulo.ui.ex.InputValidatorEx;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.Charset;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SerialSettingsPanel {

    private static final Pattern NAME_PATTERN = Pattern.compile("(.+)\\((\\d+)\\)\\s*");
    private static final String RECONNECT_PROFILE_DIALOG_KEY = "com.intellij.plugins.serialmonitor.reconnect.dialog.dont.show.again";

    private SerialSettingsPanel() {
    }

    public static void createNewProfile(@Nonnull ConnectableList connectableList, @Nullable String oldProfileName, @Nullable String newPortName) {
        SerialProfileService service = SerialProfileService.getInstance();
        Map<String, SerialPortProfile> profiles = new HashMap<>(service.getProfiles());

        SerialPortProfile newProfile;
        if (oldProfileName == null) {
            newProfile = service.copyDefaultProfile(newPortName);
        }
        else {
            SerialPortProfile existing = profiles.get(oldProfileName);
            newProfile = existing != null ? existing.copy() : service.copyDefaultProfile(newPortName);
        }

        int i = 0;
        String nameBase = oldProfileName != null ? oldProfileName : newProfile.defaultName();
        String newName = nameBase;

        Matcher matcher = NAME_PATTERN.matcher(nameBase);
        if (matcher.matches()) {
            nameBase = matcher.group(1).trim();
            i = Integer.parseInt(matcher.group(2));
            newName = null;
        }

        while (newName == null || profiles.containsKey(newName)) {
            i++;
            newName = nameBase + " (" + i + ")";
        }

        String finalNewName = newName;
        InputValidator validator = new InputValidatorEx() {
            @Override
            public boolean checkInput(String inputString) {
                return inputString != null && !inputString.isBlank() && !profiles.containsKey(inputString);
            }

            @Override
            public boolean canClose(String inputString) {
                return checkInput(inputString);
            }

            @Override
            public @Nullable String getErrorText(String inputString) {
                if (inputString == null || inputString.isBlank()) {
                    return SerialMonitorLocalize.textEnterUniqueProfileName().get();
                }
                if (checkInput(inputString)) {
                    return null;
                }
                return SerialMonitorLocalize.textProfileAlreadyExists().get();
            }
        };

        String finalName = Messages.showInputDialog(
            connectableList,
            SerialMonitorLocalize.dialogMessageName().get(),
            SerialMonitorLocalize.dialogTitleNewProfile().get(),
            null,
            finalNewName,
            validator
        );

        if (finalName != null) {
            profiles.put(finalName, newProfile);
            service.setProfiles(profiles);
            connectableList.updateModel();
            SwingUtilities.invokeLater(() -> connectableList.selectProfile(finalName));
        }
    }

    public static @Nullable JPanel portSettings(@Nonnull ConnectableList connectableList, @Nonnull String portName, @Nonnull Disposable disposable) {
        ConnectPanel parentPanel = connectableList.getParentPanel();
        SerialMonitor monitor = parentPanel.getOpenedMonitor(portName);
        boolean openedInParentPanel = monitor != null;
        PortStatus portStatus = monitor != null ? monitor.getStatus() : SerialPortService.getInstance().portStatus(portName);
        SerialProfileService profileService = SerialProfileService.getInstance();

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(JBUI.Borders.empty(10));

        // Port name row
        JPanel portNameRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        portNameRow.add(new JLabel(SerialMonitorLocalize.labelPortName().get()));
        portNameRow.add(new JLabel(portName));
        panel.add(portNameRow);

        SerialPortProfile profile = profileService.copyDefaultProfile(portName);
        boolean readOnly = portStatus != PortStatus.DISCONNECTED && portStatus != PortStatus.READY && openedInParentPanel;

        addSerialSettings(panel, disposable, profile, readOnly, p -> profileService.setDefaultProfile(p));

        // Buttons row
        JPanel buttonsRow = new JPanel(new FlowLayout(FlowLayout.LEFT));

        if (isPortConnectVisible(portStatus)) {
            JButton connectButton = new JButton(SerialMonitorLocalize.buttonConnect().get());
            connectButton.setToolTipText(getConnectTooltip(portStatus));
            connectButton.addActionListener(e -> {
                SerialPortProfile p = profileService.copyDefaultProfile(portName);
                parentPanel.connectProfile(p);
            });
            buttonsRow.add(connectButton);
        }

        if (isDisconnectVisible(portStatus) && openedInParentPanel) {
            JButton disconnectButton = new JButton(SerialMonitorLocalize.buttonDisconnect().get());
            disconnectButton.addActionListener(e -> parentPanel.disconnectPort(portName));
            buttonsRow.add(disconnectButton);
        }

        if (isOpenConsoleVisible(portStatus) && openedInParentPanel) {
            JButton openConsoleButton = new JButton(SerialMonitorLocalize.buttonOpenConsole().get());
            openConsoleButton.addActionListener(e -> parentPanel.openConsole(portName));
            buttonsRow.add(openConsoleButton);
        }

        if (!isPortConnectVisible(portStatus) && !openedInParentPanel) {
            JButton reconnectButton = new JButton(SerialMonitorLocalize.buttonReconnect().get());
            reconnectButton.addActionListener(e -> {
                SerialPortProfile p = profileService.copyDefaultProfile(portName);
                String name = p.defaultName();
                if (showReconnectDialog(p, name, parentPanel)) {
                    parentPanel.reconnectProfile(p, name);
                }
            });
            buttonsRow.add(reconnectButton);
        }

        Hyperlink createProfileLink = Hyperlink.create(SerialMonitorLocalize.linkLabelCreateProfile());
        createProfileLink.addHyperlinkListener(hyperlinkEvent -> createNewProfile(connectableList, null, portName));

        buttonsRow.add(TargetAWT.to(createProfileLink));

        panel.add(buttonsRow);

        return panel;
    }

    public static @Nullable JPanel profileSettings(@Nonnull ConnectableList connectableList, @Nonnull Disposable disposable) {
        ConnectableList.Pair<String, SerialPortProfile> selectedProfile = connectableList.getSelectedProfile();
        if (selectedProfile == null || selectedProfile.getSecond() == null) {
            return null;
        }

        String profileName = selectedProfile.getFirst();
        SerialPortProfile profile = selectedProfile.getSecond();
        ConnectPanel parentPanel = connectableList.getParentPanel();

        SerialMonitor monitor = parentPanel.getOpenedMonitor(profile.getPortName());
        PortStatus status = monitor != null ? monitor.getStatus() : SerialPortService.getInstance().portStatus(profile.getPortName());
        boolean isUsed = monitor != null && monitor.getPortProfile() == profile;

        SerialProfileService profileService = SerialProfileService.getInstance();

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(JBUI.Borders.empty(10));

        // Profile name row
        JPanel profileRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        profileRow.add(new JLabel(SerialMonitorLocalize.labelProfile().get()));
        profileRow.add(new JLabel(profileName));
        panel.add(profileRow);

        // Port combo box row
        JPanel portRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        portRow.add(new JLabel(SerialMonitorLocalize.labelPort().get()));
        Set<String> portNames = SerialPortService.getInstance().getPortsNames();
        JComboBox<String> portCombo = new JComboBox<>(portNames.toArray(new String[0]));
        portCombo.setEditable(true);
        portCombo.getEditor().setItem(profile.getPortName());
        portCombo.addActionListener(e -> {
            Object item = portCombo.getEditor().getItem();
            if (item != null) {
                String newPortName = item.toString();
                if (!newPortName.equals(profile.getPortName())) {
                    profile.setPortName(newPortName);
                    saveProfile(profileService, profileName, profile);
                    parentPanel.notifyProfileChanged(profile);
                }
            }
        });
        portRow.add(portCombo);
        panel.add(portRow);

        addSerialSettings(panel, disposable, profile, false, p -> {
            saveProfile(profileService, profileName, p);
            parentPanel.notifyProfileChanged(profile);
        });

        // Buttons row
        JPanel buttonsRow = new JPanel(new FlowLayout(FlowLayout.LEFT));

        if (isProfileConnectVisible(status)) {
            JButton connectButton = new JButton(SerialMonitorLocalize.buttonConnect().get());
            connectButton.setEnabled(isConnectEnabled(status));
            connectButton.setToolTipText(getConnectTooltip(status));
            connectButton.addActionListener(e -> parentPanel.connectProfile(profile, profileName));
            buttonsRow.add(connectButton);
        }

        if (!isProfileConnectVisible(status) && !isUsed) {
            JButton reconnectButton = new JButton(SerialMonitorLocalize.buttonReconnect().get());
            reconnectButton.addActionListener(e -> {
                if (showReconnectDialog(profile, profileName, parentPanel)) {
                    parentPanel.reconnectProfile(profile, profileName);
                }
            });
            buttonsRow.add(reconnectButton);
        }

        if (isDisconnectVisible(status) && isUsed) {
            JButton disconnectButton = new JButton(SerialMonitorLocalize.buttonDisconnect().get());
            disconnectButton.addActionListener(e -> parentPanel.disconnectPort(profile.getPortName()));
            buttonsRow.add(disconnectButton);
        }

        if (isOpenConsoleVisible(status) && isUsed) {
            JButton openConsoleButton = new JButton(SerialMonitorLocalize.buttonOpenConsole().get());
            openConsoleButton.addActionListener(e -> parentPanel.openConsole(profile.getPortName()));
            buttonsRow.add(openConsoleButton);
        }

        Hyperlink duplicateLink = Hyperlink.create(SerialMonitorLocalize.linkLabelDuplicateProfile());
        duplicateLink.addHyperlinkListener(e -> createNewProfile(connectableList, profileName, null));
        buttonsRow.add(TargetAWT.to(duplicateLink));

        panel.add(buttonsRow);

        return panel;
    }

    private static void saveProfile(@Nonnull SerialProfileService service, @Nonnull String profileName, @Nonnull SerialPortProfile profile) {
        Map<String, SerialPortProfile> profiles = new HashMap<>(service.getProfiles());
        profiles.put(profileName, profile);
        service.setProfiles(profiles);
    }

    private static void addSerialSettings(@Nonnull JPanel panel, @Nonnull Disposable disposable,
                                          @Nonnull SerialPortProfile profile, boolean readOnly,
                                          @Nonnull Consumer<SerialPortProfile> save) {
        // Baud rate and bits row
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.add(new JLabel(SerialMonitorLocalize.labelBaud().get()));
        JComboBox<Integer> baudCombo = new JComboBox<>(SerialPortProfile.STANDARD_BAUDS.toArray(new Integer[0]));
        baudCombo.setEditable(true);
        baudCombo.setSelectedItem(profile.getBaudRate());
        baudCombo.setEnabled(!readOnly);
        ComboboxSpeedSearch.installOn(baudCombo);
        baudCombo.addActionListener(e -> {
            Object item = baudCombo.getSelectedItem();
            if (item != null) {
                try {
                    int baud = item instanceof Integer ? (Integer) item : Integer.parseInt(item.toString());
                    if (baud != profile.getBaudRate()) {
                        profile.setBaudRate(baud);
                        save.accept(profile);
                    }
                }
                catch (NumberFormatException ignored) {
                }
            }
        });
        row1.add(baudCombo);

        row1.add(new JLabel(SerialMonitorLocalize.labelBits().get()));
        JComboBox<Integer> bitsCombo = new JComboBox<>(SerialPortProfile.SERIAL_BITS.toArray(new Integer[0]));
        bitsCombo.setSelectedItem(profile.getBits());
        bitsCombo.setEnabled(!readOnly);
        bitsCombo.addActionListener(e -> {
            Integer bits = (Integer) bitsCombo.getSelectedItem();
            if (bits != null && bits != profile.getBits()) {
                profile.setBits(bits);
                save.accept(profile);
            }
        });
        row1.add(bitsCombo);
        panel.add(row1);

        // Stop bits and parity row
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.add(new JLabel(SerialMonitorLocalize.labelStopBits().get()));
        JComboBox<StopBits> stopBitsCombo = new JComboBox<>(StopBits.values());
        stopBitsCombo.setSelectedItem(profile.getStopBits());
        stopBitsCombo.setEnabled(!readOnly);
        stopBitsCombo.addActionListener(e -> {
            StopBits stopBits = (StopBits) stopBitsCombo.getSelectedItem();
            if (stopBits != null && stopBits != profile.getStopBits()) {
                profile.setStopBits(stopBits);
                save.accept(profile);
            }
        });
        row2.add(stopBitsCombo);

        row2.add(new JLabel(SerialMonitorLocalize.labelParity().get()));
        JComboBox<Parity> parityCombo = new JComboBox<>(Parity.values());
        parityCombo.setSelectedItem(profile.getParity());
        parityCombo.setEnabled(!readOnly);
        parityCombo.addActionListener(e -> {
            Parity parity = (Parity) parityCombo.getSelectedItem();
            if (parity != null && parity != profile.getParity()) {
                profile.setParity(parity);
                save.accept(profile);
            }
        });
        row2.add(parityCombo);
        panel.add(row2);

        // New line and encoding row
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row3.add(new JLabel(SerialMonitorLocalize.labelNewLine().get()));
        JComboBox<SerialProfileService.NewLine> newLineCombo = new JComboBox<>(SerialProfileService.NewLine.values());
        newLineCombo.setSelectedItem(profile.getNewLine());
        newLineCombo.setEnabled(!readOnly);
        newLineCombo.addActionListener(e -> {
            SerialProfileService.NewLine newLine = (SerialProfileService.NewLine) newLineCombo.getSelectedItem();
            if (newLine != null && newLine != profile.getNewLine()) {
                profile.setNewLine(newLine);
                save.accept(profile);
            }
        });
        row3.add(newLineCombo);

        row3.add(new JLabel(SerialMonitorLocalize.labelEncoding().get()));
        List<String> charsets = new ArrayList<>();
        for (Map.Entry<String, Charset> entry : Charset.availableCharsets().entrySet()) {
            if (entry.getValue().canEncode()) {
                charsets.add(entry.getKey());
            }
        }
        JComboBox<String> encodingCombo = new JComboBox<>(charsets.toArray(new String[0]));
        encodingCombo.setSelectedItem(profile.getEncoding());
        encodingCombo.setEnabled(!readOnly);
        ComboboxSpeedSearch.installOn(encodingCombo);
        encodingCombo.addActionListener(e -> {
            String encoding = (String) encodingCombo.getSelectedItem();
            if (encoding != null && !encoding.equals(profile.getEncoding())) {
                profile.setEncoding(encoding);
                save.accept(profile);
            }
        });
        row3.add(encodingCombo);
        panel.add(row3);

        // Checkboxes row
        JPanel row4 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row4.add(new JLabel(SerialMonitorLocalize.labelLocalEcho().get()));
        JBCheckBox localEchoCheck = new JBCheckBox("", profile.getLocalEcho());
        localEchoCheck.setEnabled(!readOnly);
        localEchoCheck.addActionListener(e -> {
            if (localEchoCheck.isSelected() != profile.getLocalEcho()) {
                profile.setLocalEcho(localEchoCheck.isSelected());
                save.accept(profile);
            }
        });
        row4.add(localEchoCheck);

        row4.add(new JLabel(SerialMonitorLocalize.labelShowHardwareFlowControl().get()));
        JBCheckBox hwControlCheck = new JBCheckBox("", profile.getShowHardwareControls());
        hwControlCheck.setEnabled(!readOnly);
        hwControlCheck.addActionListener(e -> {
            if (hwControlCheck.isSelected() != profile.getShowHardwareControls()) {
                profile.setShowHardwareControls(hwControlCheck.isSelected());
                save.accept(profile);
            }
        });
        row4.add(hwControlCheck);
        panel.add(row4);
    }

    private static boolean showReconnectDialog(@Nonnull SerialPortProfile profile, @Nonnull String profileName, @Nullable Component parentComponent) {
        boolean doNotShowDialog = ApplicationPropertiesComponent.getInstance().getBoolean(RECONNECT_PROFILE_DIALOG_KEY);
        if (doNotShowDialog) {
            return true;
        }

        String title = SerialMonitorLocalize.dialogTitleReconnectToPort(profile.getPortName()).get();
        String description = SerialMonitorLocalize.dialogMessageReconnectToPort(profile.getPortName(), profileName).get();

        return MessageDialogBuilder.okCancel(title, description)
            .yesText(SerialMonitorLocalize.buttonReconnect().get())
            .icon(Messages.getQuestionIcon())
            .doNotAsk(new DialogWrapper.DoNotAskOption.Adapter() {
                @Override
                public void rememberChoice(boolean isSelected, int exitCode) {
                    ApplicationPropertiesComponent.getInstance().setValue(RECONNECT_PROFILE_DIALOG_KEY, isSelected);
                }
            })
            .ask(parentComponent);
    }

    private static boolean isProfileConnectVisible(@Nonnull PortStatus status) {
        return status != PortStatus.CONNECTED && status != PortStatus.DISCONNECTED;
    }

    private static boolean isPortConnectVisible(@Nonnull PortStatus status) {
        return status == PortStatus.READY;
    }

    private static boolean isConnectEnabled(@Nonnull PortStatus status) {
        return status == PortStatus.READY;
    }

    private static boolean isDisconnectVisible(@Nonnull PortStatus status) {
        return status == PortStatus.CONNECTED;
    }

    private static boolean isOpenConsoleVisible(@Nonnull PortStatus status) {
        return status != PortStatus.UNAVAILABLE && status != PortStatus.READY;
    }

    private static @Nullable String getConnectTooltip(@Nonnull PortStatus status) {
        return switch (status) {
            case UNAVAILABLE, UNAVAILABLE_DISCONNECTED -> SerialMonitorLocalize.tooltipPortUnavailable().get();
            case BUSY -> SerialMonitorLocalize.tooltipPortBusy().get();
            case CONNECTING -> SerialMonitorLocalize.tooltipPortConnecting().get();
            default -> null;
        };
    }
}
