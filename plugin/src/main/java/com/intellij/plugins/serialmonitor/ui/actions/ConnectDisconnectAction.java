package com.intellij.plugins.serialmonitor.ui.actions;

import com.intellij.plugins.serialmonitor.service.PortStatus;
import com.intellij.plugins.serialmonitor.ui.console.JeditermSerialMonitorDuplexConsoleView;
import consulo.application.dumb.DumbAware;
import consulo.localize.LocalizeValue;
import consulo.serial.monitor.icon.SerialMonitorIconGroup;
import consulo.serialMonitor.localize.SerialMonitorLocalize;
import consulo.ui.ex.action.ActionUpdateThread;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.action.ToggleAction;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;

/**
 * @author Dmitry_Cherkas, Ilia Motornyi
 */
public class ConnectDisconnectAction extends ToggleAction implements DumbAware {

  private final @Nonnull JeditermSerialMonitorDuplexConsoleView myConsoleView;

  public ConnectDisconnectAction(@Nonnull JeditermSerialMonitorDuplexConsoleView consoleView) {
    super(SerialMonitorLocalize.connectTitle(), SerialMonitorLocalize.connectTooltip(), SerialMonitorIconGroup.connectactive());
    myConsoleView = consoleView;
  }

  @Override
  public @Nonnull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean isSelected(@Nonnull AnActionEvent e) {
    return myConsoleView.getStatus() == PortStatus.CONNECTED;
  }

  @Override
  public void setSelected(@Nonnull AnActionEvent e, boolean doConnect) {
    myConsoleView.connect(doConnect);
  }

  @Override
  public void update(@Nonnull AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();

    if (myConsoleView.isLoading()) {
      presentation.setEnabled(false);
      return;
    }

    PortStatus status = myConsoleView.getStatus();
    Image icon = null;
    LocalizeValue text = null;
    boolean enabled = false;
    switch (status) {
      case UNAVAILABLE_DISCONNECTED:
      case UNAVAILABLE:
        icon = SerialMonitorIconGroup.invalid();
        text = SerialMonitorLocalize.connectInvalidSettingsTitle();
        break;
      case BUSY:
        icon = SerialMonitorIconGroup.invalid();
        break;
      case READY:
      case DISCONNECTED:
        icon = SerialMonitorIconGroup.connectactive();
        text = SerialMonitorLocalize.connectTitle();
        enabled = true;
        break;
      case CONNECTING:
        icon = PortStatus.BUSY.getIcon();
        break;
      case CONNECTED:
        icon = SerialMonitorIconGroup.connectactive();
        text = SerialMonitorLocalize.disconnectTitle();
        enabled = true;
    }
    presentation.setIcon(icon);
    presentation.setTextValue(text);
    presentation.setEnabled(enabled);
  }

}
