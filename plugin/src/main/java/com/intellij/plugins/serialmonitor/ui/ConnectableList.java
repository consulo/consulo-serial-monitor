package com.intellij.plugins.serialmonitor.ui;

import com.intellij.plugins.serialmonitor.SerialPortProfile;
import com.intellij.plugins.serialmonitor.SerialProfileService;
import com.intellij.plugins.serialmonitor.service.PortStatus;
import com.intellij.plugins.serialmonitor.service.SerialPortService;
import com.intellij.plugins.serialmonitor.service.SerialPortsListener;
import consulo.application.Application;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.serial.monitor.icon.SerialMonitorIconGroup;
import consulo.serialMonitor.localize.SerialMonitorLocalize;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.ColoredListCellRenderer;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.PopupHandler;
import consulo.ui.ex.awt.speedSearch.ListSpeedSearch;
import consulo.ui.image.Image;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

public class ConnectableList extends JBList<Object> {

    private final ConnectPanel parentPanel;
    private final ActionGroup toolbarActions;
    private final AnAction[] defaultActions;

    private final AnAction removeProfile;
    private final AnAction duplicateProfile;
    private final AnAction createProfile;

    public ConnectableList(@Nonnull ConnectPanel parentPanel) {
        this.parentPanel = parentPanel;

        this.removeProfile = new DumbAwareAction(SerialMonitorLocalize.actionRemoveProfileText(), LocalizeValue.empty(), PlatformIconGroup.generalRemove()) {
            {
                registerCustomShortcutSet(CommonShortcuts.getDelete(), ConnectableList.this);
            }

            @Override
            public void actionPerformed(@Nonnull AnActionEvent e) {
                Object selected = getSelectedValue();
                if (!(selected instanceof ConnectableProfile profile)) return;
                String entityName = profile.getEntityName();

                int result = Messages.showYesNoDialog(
                        ConnectableList.this,
                        SerialMonitorLocalize.dialogMessageAreYouSure().get(),
                        SerialMonitorLocalize.dialogTitleDeleteProfile(entityName).get(),
                        Messages.getQuestionIcon());
                if (result == Messages.YES) {
                    SerialProfileService service = SerialProfileService.getInstance();
                    Map<String, SerialPortProfile> newProfiles = new HashMap<>(service.getProfiles());
                    newProfiles.remove(entityName);
                    clearSelection();
                    service.setProfiles(newProfiles);
                }
            }
        };

        this.duplicateProfile = new DumbAwareAction(SerialMonitorLocalize.actionDuplicateProfileText(), LocalizeValue.empty(), PlatformIconGroup.actionsCopy()) {
            {
                registerCustomShortcutSet(CommonShortcuts.getDuplicate(), ConnectableList.this);
            }

            @Override
            public void actionPerformed(@Nonnull AnActionEvent e) {
                Object selected = getSelectedValue();
                if (!(selected instanceof ConnectableProfile profile)) return;
                String entityName = profile.getEntityName();
                SerialSettingsPanel.createNewProfile(ConnectableList.this, entityName, null);
            }
        };

        this.createProfile = new DumbAwareAction(SerialMonitorLocalize.actionCreateProfileText(), LocalizeValue.empty(), PlatformIconGroup.generalAdd()) {
            {
                registerCustomShortcutSet(CommonShortcuts.getNew(), ConnectableList.this);
            }

            @Override
            public void actionPerformed(@Nonnull AnActionEvent e) {
                String portName = getSelectedPortName();
                SerialSettingsPanel.createNewProfile(ConnectableList.this, null, portName);
            }
        };

        this.defaultActions = new AnAction[]{
                new DumbAwareAction(SerialMonitorLocalize.actionConnectText(), LocalizeValue.empty(), SerialMonitorIconGroup.connectpassive()) {
                    @Override
                    public void actionPerformed(@Nonnull AnActionEvent e) {
                    }

                    @Override
                    public @Nonnull ActionUpdateThread getActionUpdateThread() {
                        return ActionUpdateThread.EDT;
                    }

                    @Override
                    public void update(@Nonnull AnActionEvent e) {
                        e.getPresentation().setEnabled(false);
                    }
                }
        };

        this.toolbarActions = new ActionGroup() {
            @Override
            public AnAction[] getChildren(@Nullable AnActionEvent e) {
                Object selectedValue = getSelectedValue();
                if (selectedValue instanceof Connectable connectable) {
                    return connectable.getActions();
                }
                if (e != null && ActionPlaces.POPUP.equals(e.getPlace())) {
                    return AnAction.EMPTY_ARRAY;
                }
                return defaultActions;
            }
        };

        setSelectionModel(new DefaultListSelectionModel() {
            {
                setSelectionMode(SINGLE_SELECTION);
            }

            @Override
            public void setSelectionInterval(int index0, int index1) {
                int newIndex = index1;
                int delta = newIndex < getSelectedIndex() ? -1 : 1;

                while (newIndex >= 0 && newIndex < getModel().getSize()) {
                    Object element = getModel().getElementAt(newIndex);
                    if (element instanceof Connectable) {
                        super.setSelectionInterval(newIndex, newIndex);
                        break;
                    }
                    newIndex += delta;
                }
            }
        });

        setCellRenderer(new ColoredListCellRenderer<>() {
            @Override
            protected void customizeCellRenderer(@Nonnull JList<?> list, Object value, int index, boolean selected, boolean hasFocus) {
                if (value instanceof Connectable connectable) {
                    setIcon(connectable.getIcon());
                    append(connectable.getName());
                    String description = connectable.getDescription();
                    if (description != null) {
                        append(" " + description, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                    }
                } else {
                    String label = value instanceof String ? (String) value : "";
                    append(label, SimpleTextAttributes.GRAY_SMALL_ATTRIBUTES);
                }
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int index = locationToIndex(e.getPoint());
                if (index >= 0 && index < getModel().getSize()) {
                    Object element = getModel().getElementAt(index);
                    if (element instanceof Connectable) {
                        setSelectedValue(element, true);
                    } else {
                        clearSelection();
                    }
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() >= 2) {
                    int index = locationToIndex(e.getPoint());
                    if (index >= 0 && index < getModel().getSize()) {
                        Object element = getModel().getElementAt(index);
                        if (element instanceof Connectable connectable) {
                            if (connectable.getStatus() == PortStatus.READY) {
                                connectable.connect();
                            } else {
                                parentPanel.openConsole(connectable.getPortName());
                            }
                        }
                    }
                }
            }
        });

        ListSpeedSearch.installOn(this, item -> item instanceof Connectable c ? c.getEntityName() : null);
        PopupHandler.installPopupHandler(this, ActionGroup.newImmutableBuilder().addAll(toolbarActions).build(), ActionPlaces.POPUP);

        // Subscribe to port status changes and update model
        Application.get().getMessageBus().connect()
                .subscribe(SerialPortsListener.SERIAL_PORTS_TOPIC, this::updateModel);

        // Initial model update
        updateModel();
    }

    public void updateModel() {
        SwingUtilities.invokeLater(() -> {
            Object savedSelection = getSelectedValue();
            String savedKey = savedSelection instanceof Connectable c ? c.getSelectionKey() : null;

            DefaultListModel<Object> newModel = new DefaultListModel<>();
            String profilesSeparator = SerialMonitorLocalize.connectionProfiles().get();
            newModel.addElement(profilesSeparator);

            SerialProfileService profileService = SerialProfileService.getInstance();
            SerialPortService portService = SerialPortService.getInstance();

            for (Map.Entry<String, SerialPortProfile> entry : profileService.getProfiles().entrySet()) {
                String profileName = entry.getKey();
                SerialPortProfile profile = entry.getValue();

                SerialMonitor monitor = parentPanel.getOpenedMonitor(profile.getPortName());
                PortStatus status = monitor != null ? monitor.getStatus() : portService.portStatus(profile.getPortName());
                boolean isUsed = monitor != null && monitor.getPortProfile() == profile;
                newModel.addElement(new ConnectableProfile(profileName, status, isUsed));
            }

            String portsSeparator = SerialMonitorLocalize.availablePorts().get();
            newModel.addElement(portsSeparator);

            for (String portName : portService.getPortsNames()) {
                PortStatus status = portService.portStatus(portName);
                boolean isUsed = parentPanel.getOpenedMonitor(portName) != null;
                newModel.addElement(new ConnectablePort(portName, status, isUsed));
            }

            setModel(newModel);
            clearSelection();
            if (savedKey != null) {
                select(savedKey);
            }
            invalidate();
        });
    }

    private void select(@Nonnull String selectionKey) {
        for (int i = 0; i < getModel().getSize(); i++) {
            Object element = getModel().getElementAt(i);
            if (element instanceof Connectable c && selectionKey.equals(c.getSelectionKey())) {
                setSelectedIndex(i);
                break;
            }
        }
    }

    public void selectProfile(@Nonnull String profileName) {
        select("R:" + profileName);
    }

    public void selectPort(@Nonnull String portName) {
        select("O:" + portName);
    }

    public @Nullable String getSelectedPortName() {
        Object value = getSelectedValue();
        return value instanceof ConnectablePort port ? port.getEntityName() : null;
    }

    public @Nullable Pair<String, SerialPortProfile> getSelectedProfile() {
        Object value = getSelectedValue();
        if (value instanceof ConnectableProfile profile) {
            String profileName = profile.getEntityName();
            SerialPortProfile serialProfile = SerialProfileService.getInstance().getProfiles().get(profileName);
            return new Pair<>(profileName, serialProfile);
        }
        return null;
    }

    public @Nonnull ActionGroup getToolbarActions() {
        return toolbarActions;
    }

    @Nonnull ConnectPanel getParentPanel() {
        return parentPanel;
    }

    // Inner classes for Connectable items

    public abstract class Connectable {
        private final String entityName;
        private final PortStatus status;
        private final boolean isUsed;

        protected final AnAction disconnectAction;
        protected final AnAction openConsole;
        protected final AnAction connectAction;

        protected Connectable(@Nonnull String entityName, @Nonnull PortStatus status, boolean isUsed) {
            this.entityName = entityName;
            this.status = status;
            this.isUsed = isUsed;

            this.disconnectAction = new DumbAwareAction(SerialMonitorLocalize.actionDisconnectText(), LocalizeValue.empty(), SerialMonitorIconGroup.disconnect()) {
                @Override
                public void actionPerformed(@Nonnull AnActionEvent e) {
                    parentPanel.disconnectPort(getPortName());
                }

                @Override
                public @Nonnull ActionUpdateThread getActionUpdateThread() {
                    return ActionUpdateThread.BGT;
                }

                @Override
                public void update(@Nonnull AnActionEvent e) {
                    e.getPresentation().setEnabled(Connectable.this.isUsed);
                }
            };

            this.openConsole = new DumbAwareAction(SerialMonitorLocalize.actionOpenConsoleText(), LocalizeValue.empty(), PlatformIconGroup.generalOpennewtab()) {
                @Override
                public void actionPerformed(@Nonnull AnActionEvent e) {
                    parentPanel.openConsole(getPortName());
                }

                @Override
                public @Nonnull ActionUpdateThread getActionUpdateThread() {
                    return ActionUpdateThread.BGT;
                }

                @Override
                public void update(@Nonnull AnActionEvent e) {
                    e.getPresentation().setEnabled(Connectable.this.isUsed);
                }
            };

            this.connectAction = new DumbAwareAction(SerialMonitorLocalize.actionConnectText(), LocalizeValue.empty(), SerialMonitorIconGroup.connectactive()) {
                @Override
                public void actionPerformed(@Nonnull AnActionEvent e) {
                    if (Connectable.this.status == PortStatus.READY) {
                        Connectable.this.connect();
                    }
                }

                @Override
                public @Nonnull ActionUpdateThread getActionUpdateThread() {
                    return ActionUpdateThread.EDT;
                }

                @Override
                public void update(@Nonnull AnActionEvent e) {
                    e.getPresentation().setEnabled(Connectable.this.status == PortStatus.READY);
                }
            };
        }

        public String getEntityName() {
            return entityName;
        }

        public @Nonnull PortStatus getStatus() {
            return status;
        }

        public boolean isUsed() {
            return isUsed;
        }

        public abstract @Nonnull String getSelectionKey();

        public abstract @Nonnull AnAction[] getActions();

        public abstract void connect();

        public abstract @Nullable String getPortName();

        public abstract @Nonnull Image getIcon();

        public abstract @Nonnull String getName();

        public @Nullable String getDescription() {
            return null;
        }
    }

    public class ConnectableProfile extends Connectable {

        public ConnectableProfile(@Nonnull String profileName, @Nonnull PortStatus status, boolean isUsed) {
            super(profileName, status, isUsed);
        }

        @Override
        public @Nullable String getPortName() {
            SerialPortProfile profile = SerialProfileService.getInstance().getProfiles().get(getEntityName());
            return profile != null ? profile.getPortName() : null;
        }

        @Override
        public @Nonnull String getSelectionKey() {
            return "R:" + getEntityName();
        }

        @Override
        public @Nonnull Image getIcon() {
            PortStatus status = getStatus();
            if ((status == PortStatus.CONNECTED || status == PortStatus.DISCONNECTED) && !isUsed()) {
                return Image.empty(Image.DEFAULT_ICON_SIZE);
            }
            return status.getIcon();
        }

        @Override
        public @Nonnull String getName() {
            return getEntityName();
        }

        @Override
        public void connect() {
            SerialPortProfile profile = SerialProfileService.getInstance().getProfiles().get(getEntityName());
            if (profile != null) {
                parentPanel.connectProfile(profile, getEntityName());
            }
        }

        @Override
        public @Nonnull AnAction[] getActions() {
            return switch (getStatus()) {
                case UNAVAILABLE -> new AnAction[]{duplicateProfile, AnSeparator.getInstance(), removeProfile};
                case CONNECTING, BUSY, UNAVAILABLE_DISCONNECTED ->
                        new AnAction[]{openConsole, duplicateProfile, AnSeparator.getInstance(), removeProfile};
                case DISCONNECTED ->
                        new AnAction[]{openConsole, connectAction, duplicateProfile, AnSeparator.getInstance(), removeProfile};
                case CONNECTED ->
                        new AnAction[]{openConsole, disconnectAction, duplicateProfile, AnSeparator.getInstance(), removeProfile};
                case READY ->
                        new AnAction[]{connectAction, duplicateProfile, AnSeparator.getInstance(), removeProfile};
            };
        }
    }

    public class ConnectablePort extends Connectable {

        public ConnectablePort(@Nonnull String portName, @Nonnull PortStatus status, boolean isUsed) {
            super(portName, status, isUsed);
        }

        @Override
        public String getPortName() {
            return getEntityName();
        }

        @Override
        public @Nonnull String getSelectionKey() {
            return "O:" + getEntityName();
        }

        @Override
        public @Nonnull Image getIcon() {
            return getStatus().getIcon();
        }

        @Override
        public @Nonnull String getName() {
            return getEntityName();
        }

        @Override
        public @Nullable String getDescription() {
            return SerialPortService.getInstance().portDescriptiveName(getEntityName());
        }

        @Override
        public void connect() {
            parentPanel.connectProfile(SerialProfileService.getInstance().copyDefaultProfile(getEntityName()));
        }

        @Override
        public @Nonnull AnAction[] getActions() {
            if (getStatus() == PortStatus.CONNECTED) {
                return new AnAction[]{openConsole, disconnectAction, createProfile};
            }
            return new AnAction[]{connectAction, createProfile};
        }
    }
}
