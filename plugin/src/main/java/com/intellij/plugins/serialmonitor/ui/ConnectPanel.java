package com.intellij.plugins.serialmonitor.ui;

import com.intellij.plugins.serialmonitor.SerialPortProfile;
import com.intellij.plugins.serialmonitor.service.PortStatus;
import com.intellij.plugins.serialmonitor.service.SerialPortsListener;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.ActionToolbar;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.content.ContentManager;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.ui.image.Image;
import consulo.util.dataholder.Key;
import consulo.util.lang.EmptyRunnable;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.*;

public class ConnectPanel extends OnePixelSplitter {

    public static final Key<SerialMonitor> SERIAL_MONITOR = Key.create(SerialMonitor.class.getName());

    @Nonnull
    private final Project myProject;
    private final ToolWindow toolWindow;
    private final ConnectableList ports;
    private Disposable disposable;
    private final ActionToolbar listToolbar;

    public ConnectPanel(@Nonnull Project project, @Nonnull ToolWindow toolWindow) {
        super(false, 0.4f, 0.1f, 0.9f);
        myProject = project;
        this.toolWindow = toolWindow;
        this.ports = new ConnectableList(this);
        this.disposable = Disposable.newDisposable();

        this.listToolbar = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.TOOLBAR, ports.getToolbarActions(), true);
        listToolbar.setTargetComponent(ports);

        setAndLoadSplitterProportionKey("ConnectPanel.splitterProportionKey");

        JPanel firstPanel = new JBPanel<>(new BorderLayout());
        firstPanel.add(listToolbar.getComponent(), BorderLayout.NORTH);
        JBScrollPane scrollPane = new JBScrollPane(ports);
        scrollPane.setBorder(null);
        firstPanel.add(scrollPane, BorderLayout.CENTER);
        setFirstComponent(firstPanel);

        ports.addListSelectionListener(e -> selectionChanged());
        setSecondComponent(new JBPanel<>());
    }

    private void selectionChanged() {
        JComponent secondComp = getSecondComponent();
        if (secondComp != null) {
            secondComp.removeAll();
        }
        Disposer.dispose(disposable);
        disposable = Disposable.newDisposable("Serial Profile Parameters");
        Disposer.register(toolWindow.getContentManager(), disposable);

        String portName = ports.getSelectedPortName();
        JPanel panel;
        if (portName != null) {
            panel = SerialSettingsPanel.portSettings(ports, portName, disposable, myProject);
        }
        else {
            panel = SerialSettingsPanel.profileSettings(ports, disposable, myProject);
        }

        if (panel != null) {
            JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(new BorderLayoutPanel().addToTop(panel), true);
            setSecondComponent(scrollPane);
            invalidate();
        }
    }

    private @Nullable Content contentByPortName(@Nullable String portName) {
        if (portName == null) {
            return null;
        }
        ContentManager contentManager = toolWindow.getContentManager();
        for (Content content : contentManager.getContents()) {
            SerialMonitor serialMonitor = content.getUserData(SERIAL_MONITOR);
            if (serialMonitor != null && portName.equals(serialMonitor.getPortProfile().getPortName())) {
                return content;
            }
        }
        return null;
    }

    private @Nullable SerialMonitor monitorByProfile(@Nonnull SerialPortProfile profile) {
        ContentManager contentManager = toolWindow.getContentManager();
        for (Content content : contentManager.getContents()) {
            SerialMonitor serialMonitor = content.getUserData(SERIAL_MONITOR);
            if (serialMonitor != null && serialMonitor.getPortProfile() == profile) {
                return serialMonitor;
            }
        }
        return null;
    }

    /**
     * Returns the SerialMonitor with which the port is currently opened in this panel, if any.
     */
    public @Nullable SerialMonitor getOpenedMonitor(@Nonnull String portName) {
        Content content = contentByPortName(portName);
        return content != null ? content.getUserData(SERIAL_MONITOR) : null;
    }

    public void openConsole(@Nullable String portName) {
        Content content = contentByPortName(portName);
        if (content != null) {
            toolWindow.getContentManager().setSelectedContent(content);
        }
    }

    public void disconnectPort(@Nullable String portName) {
        Content content = contentByPortName(portName);
        if (content != null) {
            SerialMonitor monitor = content.getUserData(SERIAL_MONITOR);
            if (monitor != null) {
                monitor.disconnect();
            }
        }
    }

    public void notifyProfileChanged(@Nonnull SerialPortProfile profile) {
        SerialMonitor monitor = monitorByProfile(profile);
        if (monitor != null) {
            monitor.notifyProfileChanged();
        }
    }

    public void reconnectProfile(@Nonnull SerialPortProfile profile, @Nonnull @Nls String name) {
        Content openedTab = contentByPortName(profile.getPortName());
        if (openedTab != null) {
            toolWindow.getContentManager().removeContent(openedTab, true);
        }
        connectProfile(profile, name);
    }

    public void connectProfile(@Nonnull SerialPortProfile profile) {
        connectProfile(profile, profile.defaultName());
    }

    public void connectProfile(@Nonnull SerialPortProfile profile, @Nonnull @Nls String name) {
        ContentManager contentManager = toolWindow.getContentManager();
        SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true);
        Content content = contentManager.getFactory().createContent(panel, name, true);
        content.putUserData(ToolWindow.SHOW_CONTENT_ICON, true);

        SerialMonitor serialMonitor = new SerialMonitor(myProject, name, profile);
        content.putUserData(SERIAL_MONITOR, serialMonitor);
        panel.setContent(serialMonitor.getComponent());
        content.setDisposer(serialMonitor);
        content.setCloseable(true);
        contentManager.addContent(content);

        SerialPortsListener handler = () -> {
            SwingUtilities.invokeLater(() -> {
                if (!toolWindow.isDisposed()) {
                    PortStatus status = serialMonitor.getStatus();
                    content.setIcon(status == PortStatus.DISCONNECTED ? Image.empty(Image.DEFAULT_ICON_SIZE) : status.getIcon());
                }
            });
        };

        myProject.getMessageBus().connect(content).subscribe(SerialPortsListener.SERIAL_PORTS_TOPIC, handler);

        serialMonitor.connect();
        contentManager.setSelectedContent(content, true);
        toolWindow.setAvailable(true);
        toolWindow.show(EmptyRunnable.getInstance());
        toolWindow.activate(null, true);
    }

    @Nonnull
    ToolWindow getToolWindow() {
        return toolWindow;
    }
}
