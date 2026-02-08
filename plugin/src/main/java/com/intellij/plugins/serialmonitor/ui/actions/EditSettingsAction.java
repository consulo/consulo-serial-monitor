package com.intellij.plugins.serialmonitor.ui.actions;

import com.intellij.plugins.serialmonitor.Parity;
import com.intellij.plugins.serialmonitor.SerialPortProfile;
import com.intellij.plugins.serialmonitor.SerialProfileService;
import com.intellij.plugins.serialmonitor.StopBits;
import com.intellij.plugins.serialmonitor.ui.SerialMonitor;
import consulo.application.AllIcons;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.serialMonitor.localize.SerialMonitorLocalize;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.awt.ComboboxSpeedSearch;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.JBCheckBox;
import consulo.ui.ex.awt.JBUI;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry_Cherkas, Ilia Motornyi
 */
public class EditSettingsAction extends DumbAwareAction {

    private final @Nonnull LocalizeValue myName;
    private final SerialMonitor serialMonitor;

    public EditSettingsAction(@Nonnull LocalizeValue myName, @Nonnull SerialMonitor serialMonitor) {
        super(SerialMonitorLocalize.editSettingsTitle(),
              SerialMonitorLocalize.editSettingsTooltip(),
              AllIcons.General.Settings);
        this.myName = myName;
        this.serialMonitor = serialMonitor;
    }

    @Override
    public void actionPerformed(@Nonnull AnActionEvent e) {
        SettingsDialog settingsDialog = new SettingsDialog(e.getData(Project.KEY));
        boolean okClicked = settingsDialog.showAndGet();
        if (okClicked) {
            serialMonitor.notifyProfileChanged();
        }
    }

    private class SettingsDialog extends DialogWrapper {
        private final SerialPortProfile profile;

        protected SettingsDialog(@Nullable Project project) {
            super(project, false, IdeModalityType.IDE);
            this.profile = serialMonitor.getPortProfile();
            setTitle(SerialMonitorLocalize.dialogTitleSerialPortSettings(myName.get()));
            init();
        }

        @Override
        protected @Nullable JComponent createCenterPanel() {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(JBUI.Borders.empty(10));

            // Port name row (read-only)
            JPanel portRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
            portRow.add(new JLabel(SerialMonitorLocalize.labelPort().get()));
            JLabel portLabel = new JLabel(profile.getPortName());
            portLabel.setBorder(BorderFactory.createLineBorder(JBColor.border()));
            portRow.add(portLabel);
            panel.add(portRow);

            // Baud rate and bits row
            JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
            row1.add(new JLabel(SerialMonitorLocalize.labelBaud().get()));
            JComboBox<Integer> baudCombo = new JComboBox<>(SerialPortProfile.STANDARD_BAUDS.toArray(new Integer[0]));
            baudCombo.setEditable(true);
            baudCombo.setSelectedItem(profile.getBaudRate());
            ComboboxSpeedSearch.installOn(baudCombo);
            baudCombo.addActionListener(e -> {
                Object item = baudCombo.getSelectedItem();
                if (item != null) {
                    try {
                        int baud = item instanceof Integer ? (Integer) item : Integer.parseInt(item.toString());
                        profile.setBaudRate(baud);
                    } catch (NumberFormatException ignored) {
                    }
                }
            });
            row1.add(baudCombo);

            row1.add(new JLabel(SerialMonitorLocalize.labelBits().get()));
            JComboBox<Integer> bitsCombo = new JComboBox<>(SerialPortProfile.SERIAL_BITS.toArray(new Integer[0]));
            bitsCombo.setSelectedItem(profile.getBits());
            bitsCombo.addActionListener(e -> {
                Integer bits = (Integer) bitsCombo.getSelectedItem();
                if (bits != null) {
                    profile.setBits(bits);
                }
            });
            row1.add(bitsCombo);
            panel.add(row1);

            // Stop bits and parity row
            JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
            row2.add(new JLabel(SerialMonitorLocalize.labelStopBits().get()));
            JComboBox<StopBits> stopBitsCombo = new JComboBox<>(StopBits.values());
            stopBitsCombo.setSelectedItem(profile.getStopBits());
            stopBitsCombo.addActionListener(e -> {
                StopBits stopBits = (StopBits) stopBitsCombo.getSelectedItem();
                if (stopBits != null) {
                    profile.setStopBits(stopBits);
                }
            });
            row2.add(stopBitsCombo);

            row2.add(new JLabel(SerialMonitorLocalize.labelParity().get()));
            JComboBox<Parity> parityCombo = new JComboBox<>(Parity.values());
            parityCombo.setSelectedItem(profile.getParity());
            parityCombo.addActionListener(e -> {
                Parity parity = (Parity) parityCombo.getSelectedItem();
                if (parity != null) {
                    profile.setParity(parity);
                }
            });
            row2.add(parityCombo);
            panel.add(row2);

            // New line and encoding row
            JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
            row3.add(new JLabel(SerialMonitorLocalize.labelNewLine().get()));
            JComboBox<SerialProfileService.NewLine> newLineCombo = new JComboBox<>(SerialProfileService.NewLine.values());
            newLineCombo.setSelectedItem(profile.getNewLine());
            newLineCombo.addActionListener(e -> {
                SerialProfileService.NewLine newLine = (SerialProfileService.NewLine) newLineCombo.getSelectedItem();
                if (newLine != null) {
                    profile.setNewLine(newLine);
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
            ComboboxSpeedSearch.installOn(encodingCombo);
            encodingCombo.addActionListener(e -> {
                String encoding = (String) encodingCombo.getSelectedItem();
                if (encoding != null) {
                    profile.setEncoding(encoding);
                }
            });
            row3.add(encodingCombo);
            panel.add(row3);

            // Checkboxes row
            JPanel row4 = new JPanel(new FlowLayout(FlowLayout.LEFT));
            row4.add(new JLabel(SerialMonitorLocalize.labelLocalEcho().get()));
            JBCheckBox localEchoCheck = new JBCheckBox("", profile.getLocalEcho());
            localEchoCheck.addActionListener(e -> profile.setLocalEcho(localEchoCheck.isSelected()));
            row4.add(localEchoCheck);

            row4.add(new JLabel(SerialMonitorLocalize.labelShowHardwareFlowControl().get()));
            JBCheckBox hwControlCheck = new JBCheckBox("", profile.getShowHardwareControls());
            hwControlCheck.addActionListener(e -> profile.setShowHardwareControls(hwControlCheck.isSelected()));
            row4.add(hwControlCheck);
            panel.add(row4);

            return panel;
        }
    }
}
