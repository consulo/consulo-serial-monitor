package com.intellij.plugins.serialmonitor.ui;

import com.intellij.plugins.serialmonitor.SerialMonitorException;
import com.intellij.plugins.serialmonitor.SerialPortProfile;
import com.intellij.plugins.serialmonitor.service.PortStatus;
import com.intellij.plugins.serialmonitor.service.SerialPortService;
import com.intellij.plugins.serialmonitor.service.SerialPortsListener;
import com.intellij.plugins.serialmonitor.ui.actions.EditSettingsAction;
import com.intellij.plugins.serialmonitor.ui.console.JeditermSerialMonitorDuplexConsoleView;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import consulo.application.Application;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.execution.debug.icon.ExecutionDebugIconGroup;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationType;
import consulo.project.ui.util.ProjectUIUtil;
import consulo.serialMonitor.localize.SerialMonitorLocalize;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.ActionToolbar;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import static com.intellij.uiDesigner.core.GridConstraints.*;

public class SerialMonitor implements Disposable, SerialPortsListener {

    private static final String HISTORY_KEY = "serialMonitor.commands";
    private static final String SERIAL_NOTIFICATION_GROUP_NAME = "Serial Monitor Notification";
    private static final int SIZE_POLICY_RESIZEABLE = SIZEPOLICY_CAN_GROW + SIZEPOLICY_CAN_SHRINK + SIZEPOLICY_WANT_GROW;

    private final Project project;
    private final SerialPortProfile portProfile;
    private final JBLoadingPanel myPanel;
    private final JBPanel<JBPanel<?>> myTopPanel;
    private final JButton mySend;
    private final TextFieldWithStoredHistory myCommand;
    private final JBCheckBox myLineEnd;
    private final JPanel myHardwareControls;
    private final JComponent ctsComponent;
    private final JComponent dsrComponent;
    private final JeditermSerialMonitorDuplexConsoleView duplexConsoleView;

    public SerialMonitor(@Nonnull Project project, @Nonnull String name, @Nonnull SerialPortProfile portProfile) {
        this.project = project;
        this.portProfile = portProfile;

        this.myPanel = new JBLoadingPanel(new GridLayoutManager(2, 2, JBUI.emptyInsets(), 0, 0), this, 300);
        this.myTopPanel = new JBPanel<>(new GridLayoutManager(1, 4, JBUI.insets(5, 10), 5, 0));

        myPanel.setLoadingText(SerialMonitorLocalize.connecting().get());
        duplexConsoleView = JeditermSerialMonitorDuplexConsoleView.create(project, portProfile, myPanel);
        Disposer.register(this, duplexConsoleView);

        JComponent consoleComponent = duplexConsoleView.getComponent();
        DefaultActionGroup toolbarActions = new DefaultActionGroup();
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, toolbarActions, false);
        toolbarActions.addAll(duplexConsoleView.createConsoleActions());
        EditSettingsAction editProfileAction = new EditSettingsAction(LocalizeValue.of(name), this);
        toolbarActions.add(editProfileAction);
        toolbar.setTargetComponent(consoleComponent);
        toolbar.getComponent().setBorder(toolbarBorder());

        myCommand = new TextFieldWithStoredHistory(HISTORY_KEY);
        myCommand.setHistorySize(10);
        myCommand.addKeyboardListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.isControlDown() && e.getKeyChar() == KeyEvent.VK_ENTER) {
                    myCommand.hidePopup();
                    mySend.doClick();
                }
            }
        });

        myLineEnd = new JBCheckBox(SerialMonitorLocalize.checkboxSendEol().get(), true);

        mySend = new JButton(SerialMonitorLocalize.sendTitle().get());
        mySend.setEnabled(false);
        mySend.addActionListener(e -> {
            send(myCommand.getText());
            myCommand.addCurrentTextToHistory();
            myCommand.setText("");
        });

        SerialPortService.SerialConnection connection = duplexConsoleView.getConnection();

        // Hardware controls panel
        myHardwareControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

        JCheckBox rtsCheckbox = new JCheckBox(SerialMonitorLocalize.hardwareFlowControlRts().get());
        rtsCheckbox.setToolTipText(SerialMonitorLocalize.hardwareFlowControlRtsTooltip().get());
        rtsCheckbox.setSelected(connection.getRts());
        rtsCheckbox.addActionListener(e -> {
            try {
                connection.setRts(rtsCheckbox.isSelected());
            } catch (SerialMonitorException ex) {
                errorNotification(ex.getMessage(), project);
            }
        });

        JCheckBox dtrCheckbox = new JCheckBox(SerialMonitorLocalize.hardwareFlowControlDtr().get());
        dtrCheckbox.setToolTipText(SerialMonitorLocalize.hardwareFlowControlDtrTooltip().get());
        dtrCheckbox.setSelected(connection.getDtr());
        dtrCheckbox.addActionListener(e -> {
            try {
                connection.setDtr(dtrCheckbox.isSelected());
            } catch (SerialMonitorException ex) {
                errorNotification(ex.getMessage(), project);
            }
        });

        Color statusColor = new JBColor(new Color(0, 255, 0), new Color(0, 255, 0));
        Icon statusIcon = TargetAWT.to(ExecutionDebugIconGroup.threadThreadatbreakpoint());

        ctsComponent = new JLabel(statusIcon);
        ctsComponent.setToolTipText(SerialMonitorLocalize.hardwareFlowControlCtsTooltip().get());
        JLabel ctsLabel = new JLabel(SerialMonitorLocalize.hardwareFlowControlCts().get());
        ctsLabel.setToolTipText(SerialMonitorLocalize.hardwareFlowControlCtsTooltip().get());

        dsrComponent = new JLabel(statusIcon);
        dsrComponent.setToolTipText(SerialMonitorLocalize.hardwareFlowControlDsrTooltip().get());
        JLabel dsrLabel = new JLabel(SerialMonitorLocalize.hardwareFlowControlDsr().get());
        dsrLabel.setToolTipText(SerialMonitorLocalize.hardwareFlowControlDsrTooltip().get());

        myHardwareControls.add(rtsCheckbox);
        myHardwareControls.add(dtrCheckbox);
        myHardwareControls.add(ctsComponent);
        myHardwareControls.add(ctsLabel);
        myHardwareControls.add(dsrComponent);
        myHardwareControls.add(dsrLabel);
        myHardwareControls.setBorder(JBUI.Borders.emptyRight(10));

        connection.setDsrListener(this::onDSRChanged);
        connection.setCtsListener(this::onCTSChanged);
        Application.get().getMessageBus().connect(this).subscribe(SerialPortsListener.SERIAL_PORTS_TOPIC, this);

        myTopPanel.add(myCommand,
                new GridConstraints(0, 0, 1, 1, ANCHOR_WEST, FILL_HORIZONTAL, SIZE_POLICY_RESIZEABLE, SIZEPOLICY_FIXED, null, null, null));
        myTopPanel.add(myLineEnd,
                new GridConstraints(0, 1, 1, 1, ANCHOR_EAST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED, null, null, null));
        myTopPanel.add(mySend,
                new GridConstraints(0, 2, 1, 1, ANCHOR_EAST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED, null, null, null));
        myTopPanel.add(myHardwareControls,
                new GridConstraints(0, 3, 1, 1, ANCHOR_EAST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED, null, null, null));

        myTopPanel.setBorder(new CustomLineBorder(1, 1, 1, 1));

        myPanel.add(toolbar.getComponent(),
                new GridConstraints(0, 0, 2, 1, ANCHOR_WEST, FILL_VERTICAL, SIZEPOLICY_FIXED, SIZE_POLICY_RESIZEABLE, null, null, null));

        myPanel.add(myTopPanel,
                new GridConstraints(0, 1, 1, 1, ANCHOR_NORTH, FILL_HORIZONTAL, SIZE_POLICY_RESIZEABLE, SIZEPOLICY_FIXED, null, null, null));
        myPanel.add(consoleComponent,
                new GridConstraints(1, 1, 1, 1, ANCHOR_CENTER, FILL_BOTH, SIZE_POLICY_RESIZEABLE, SIZE_POLICY_RESIZEABLE, null, null, null));

        duplexConsoleView.addSwitchListener(this::hideSendControls, this);
        hideSendControls(duplexConsoleView.isPrimaryConsoleEnabled());
        updateHardwareVisibility();
    }

    public @Nonnull PortStatus getStatus() {
        return duplexConsoleView.getStatus();
    }

    @Override
    public void portsStatusChanged() {
        SwingUtilities.invokeLater(() -> {
            mySend.setEnabled(duplexConsoleView.getStatus() == PortStatus.CONNECTED);
            onCTSChanged(duplexConsoleView.getConnection().getCts());
            onDSRChanged(duplexConsoleView.getConnection().getDsr());
        });
    }

    public void notifyProfileChanged() {
        duplexConsoleView.reconnect();
        updateHardwareVisibility();
    }

    private void send(@Nonnull String txt) {
        String s = txt;
        if (myLineEnd.isSelected()) {
            s += portProfile.getNewLine().getValue();
        }

        if (!s.isEmpty()) {
            byte[] bytes = s.getBytes(duplexConsoleView.getCharset());
            Application.get().executeOnPooledThread(() -> {
                try {
                    duplexConsoleView.getConnection().write(bytes);
                } catch (Exception sme) {
                    errorNotification(sme.getMessage(), project);
                }
            });
        }
    }

    public @Nonnull JComponent getComponent() {
        return myPanel;
    }

    public void disconnect() {
        duplexConsoleView.connect(false);
    }

    @Override
    public void dispose() {
    }

    public void connect() {
        duplexConsoleView.connect(true);
    }

    public boolean isTimestamped() {
        return duplexConsoleView.isTimestamped();
    }

    public boolean isHex() {
        return !duplexConsoleView.isPrimaryConsoleEnabled();
    }

    public @Nonnull SerialPortProfile getPortProfile() {
        return portProfile;
    }

    private Border toolbarBorder() {
        return JBUI.Borders.compound(JBUI.Borders.customLineRight(JBColor.border()), JBUI.Borders.empty(9));
    }

    private void updateHardwareVisibility() {
        myHardwareControls.setVisible(portProfile.getShowHardwareControls());
        updateTopPanelVisibility();
    }

    private void hideSendControls(boolean q) {
        mySend.setVisible(!q);
        myCommand.setVisible(!q);
        myLineEnd.setVisible(!q);
        updateTopPanelVisibility();
    }

    private void updateTopPanelVisibility() {
        myTopPanel.setVisible(mySend.isVisible() || myCommand.isVisible() || myLineEnd.isVisible() || myHardwareControls.isVisible());
    }

    private void onCTSChanged(boolean state) {
        ctsComponent.setEnabled(state);
    }

    private void onDSRChanged(boolean state) {
        dsrComponent.setEnabled(state);
    }

    public static void errorNotification(@Nonnull String content, @Nonnull Project project) {
        NotificationGroup notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup(SERIAL_NOTIFICATION_GROUP_NAME);
        if (notificationGroup != null) {
            notificationGroup.createNotification(content, NotificationType.ERROR).notify(project);
        }
    }

    public static void errorNotification(@Nonnull String content, @Nonnull Component component) {
        errorNotification(content, ProjectUIUtil.getProjectForComponent(component));
    }

    public static class Companion {
        public static void errorNotification(@Nonnull String content, @Nonnull Component component) {
            SerialMonitor.errorNotification(content, component);
        }
    }
}
