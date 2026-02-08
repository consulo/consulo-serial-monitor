package com.intellij.plugins.serialmonitor;

import com.intellij.plugins.serialmonitor.ui.ConnectPanel;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowFactory;
import consulo.serial.monitor.icon.SerialMonitorIconGroup;
import consulo.serialMonitor.localize.SerialMonitorLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.content.ContentManager;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.ui.ex.toolWindow.ToolWindowAnchor;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;

import javax.swing.*;

/**
 * @author Dmitry_Cherkas, Ilia Motornyi
 */
@ExtensionImpl
public class SerialMonitorToolWindowFactory implements ToolWindowFactory, DumbAware {
    public static final String ID = "SERIAL_MONITOR";

    @RequiredUIAccess
    @Override
    public void init(Project project, @Nonnull ToolWindow toolWindow) {
        //toolWindow.setTabsSplittingAllowed(true);
        toolWindow.setToHideOnEmptyContent(false);
        toolWindow.setAvailable(true);
    }

    @Nonnull
    @Override
    public String getId() {
        return ID;
    }

    @Nonnull
    @Override
    public Image getIcon() {
        return SerialMonitorIconGroup.toolwindow();
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return SerialMonitorLocalize.toolwindowStripeTitle();
    }

    @Nonnull
    @Override
    public ToolWindowAnchor getAnchor() {
        return ToolWindowAnchor.BOTTOM;
    }

    @Override
    public boolean canCloseContents() {
        return true;
    }

    @Override
    public void createToolWindowContent(@Nonnull Project project, @Nonnull ToolWindow toolWindow) {
        ContentManager manager = toolWindow.getContentManager();
        JPanel portPanel = new ConnectPanel(project, toolWindow);
        Content content = manager.getFactory().createContent(portPanel, SerialMonitorLocalize.toolwindowPortTabTitle().get(), true);
        content.setCloseable(false);
        manager.addContent(content);
    }
}
