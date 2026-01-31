package com.intellij.plugins.serialmonitor;

import com.intellij.plugins.serialmonitor.ui.ConnectPanel;
import consulo.application.dumb.DumbAware;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowFactory;
import consulo.serialMonitor.localize.SerialMonitorLocalize;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.content.ContentManager;
import consulo.ui.ex.toolWindow.ToolWindow;
import jakarta.annotation.Nonnull;

import javax.swing.*;

/**
 * @author Dmitry_Cherkas, Ilia Motornyi
 */
public class SerialMonitorToolWindowFactory implements ToolWindowFactory, DumbAware {
  @Override
  public void init(@Nonnull ToolWindow toolWindow) {
    toolWindow.setTabsSplittingAllowed(true);
    toolWindow.setToHideOnEmptyContent(false);
    toolWindow.setStripeTitle(SerialMonitorLocalize.toolwindowStripeTitle());
    toolWindow.setAvailable(true);
  }

  @Override
  public void createToolWindowContent(@Nonnull Project project, @Nonnull ToolWindow toolWindow) {
    ContentManager manager = toolWindow.getContentManager();
    JPanel portPanel = new ConnectPanel(toolWindow);
    Content content = manager.getFactory().createContent(portPanel, SerialMonitorLocalize.toolwindowPortTabTitle(), true);
    content.setCloseable(false);
    manager.addContent(content);
  }

}
