package com.intellij.plugins.serialmonitor.ui.console;

import com.intellij.plugins.serialmonitor.SerialMonitorException;
import com.intellij.plugins.serialmonitor.SerialPortProfile;
import com.intellij.plugins.serialmonitor.service.PortStatus;
import com.intellij.plugins.serialmonitor.service.SerialPortService;
import com.intellij.plugins.serialmonitor.ui.SerialMonitor;
import com.intellij.plugins.serialmonitor.ui.actions.ConnectDisconnectAction;
import com.intellij.plugins.serialmonitor.ui.actions.SaveHistoryToFileAction;
import consulo.application.AllIcons;
import consulo.application.Application;
import consulo.application.ApplicationPropertiesComponent;
import consulo.application.dumb.DumbAware;
import consulo.codeEditor.Editor;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.execution.internal.action.ScrollToTheEndToolbarAction;
import consulo.execution.localize.ExecutionLocalize;
import consulo.execution.ui.console.ConsoleView;
import consulo.execution.ui.console.DuplexConsoleView;
import consulo.language.editor.CommonDataKeys;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.serial.monitor.icon.SerialMonitorIconGroup;
import consulo.serialMonitor.localize.SerialMonitorLocalize;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.JBLoadingPanel;
import jakarta.annotation.Nonnull;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;


/**
 * @author Dmitry_Cherkas, Ilia Motornyi
 */
public class JeditermSerialMonitorDuplexConsoleView extends DuplexConsoleView<JeditermConsoleView, ConsoleView>
    implements Disposable {

    private static final String STATE_STORAGE_KEY = "SerialMonitorDuplexConsoleViewState";

    private final @Nonnull SerialPortService.SerialConnection myConnection;
    private final @Nonnull SerialPortProfile myPortProfile;
    private final @Nonnull ToggleAction mySwitchConsoleAction;
    private final @Nonnull JBLoadingPanel myLoadingPanel;
    @Nonnull
    private final HexConsoleView myHexConsoleView;
    private final Project myProject;
    private Charset myCharset = StandardCharsets.US_ASCII;

    public SerialPortService.SerialConnection getConnection() {
        return myConnection;
    }

    //todo auto reconnect while build
    //todo interoperability with other plugins

    public static @Nonnull JeditermSerialMonitorDuplexConsoleView create(@Nonnull Project project,
                                                                         @Nonnull SerialPortProfile portProfile,
                                                                         @Nonnull JBLoadingPanel loadingPanel) {
        SerialPortService.SerialConnection connection =
            Application.get().getService(SerialPortService.class)
                .newConnection(portProfile.getPortName());
        JeditermConsoleView textConsoleView = new JeditermConsoleView(project, connection);
        HexConsoleView hexConsoleView = new HexConsoleView(project, true);
        Disposer.register(textConsoleView, hexConsoleView);

        // Set primary console as default
        if (!ApplicationPropertiesComponent.getInstance().isValueSet(STATE_STORAGE_KEY)) {
            ApplicationPropertiesComponent.getInstance().setValue(STATE_STORAGE_KEY, true);
        }

        JeditermSerialMonitorDuplexConsoleView consoleView =
            new JeditermSerialMonitorDuplexConsoleView(connection,
                textConsoleView,
                hexConsoleView,
                portProfile,
                loadingPanel,
                project
            );
        connection.setDataListener(consoleView::append);
        return consoleView;
    }

    private JeditermSerialMonitorDuplexConsoleView(
        SerialPortService.SerialConnection connection,
        JeditermConsoleView textConsoleView,
        HexConsoleView hexConsoleView,
        @Nonnull SerialPortProfile portProfile,
        @Nonnull JBLoadingPanel loadingPanel,
        Project project) {
        super(textConsoleView, hexConsoleView.getConsoleView(), STATE_STORAGE_KEY);
        myHexConsoleView = hexConsoleView;
        myProject = project;
        mySwitchConsoleAction = new SwitchConsoleViewAction();
        myLoadingPanel = loadingPanel;
        myPortProfile = portProfile;
        myConnection = connection;
    }

    @Override
    public @Nonnull Presentation getSwitchConsoleActionPresentation() {
        return mySwitchConsoleAction.getTemplatePresentation();
    }

    @Override
    public boolean isOutputPaused() {
        return getPrimaryConsoleView().isOutputPaused();
    }

    @Override
    public boolean canPause() {
        return true;
    }

    /**
     * Allows filtering out inappropriate actions from toolbar.
     */
    @Override
    public @Nonnull AnAction[] createConsoleActions() {

        return new AnAction[]{
            new ConnectDisconnectAction(this),
            mySwitchConsoleAction,
            getPrimaryConsoleView().getScrollToTheEndToolbarAction(),
            new MyScrollToTheEndToolbarAction(getSecondaryConsoleView().getEditor()),
            getPrimaryConsoleView().getPrintTimestampsToggleAction(),
            new SerialPauseAction(),
            new SaveHistoryToFileAction(getPrimaryConsoleView().getTerminalTextBuffer(), myPortProfile),
            new ClearAllAction()};
    }

    public @Nonnull PortStatus getStatus() {
        return myConnection.getStatus();
    }

    public boolean isTimestamped() {
        return getPrimaryConsoleView().isTimestamped();
    }

    public synchronized void connect(boolean doConnect) {
        myLoadingPanel.startLoading();
        Application.get().executeOnPooledThread(
            () -> {
                performConnect(doConnect);
                Application.get().invokeLater(myLoadingPanel::stopLoading);
            }
        );
    }

    private void performConnect(boolean doConnect) {
        try {
            if (doConnect) {
                myConnection.closeSilently(true);
                myCharset = Charset.availableCharsets().getOrDefault(myPortProfile.getEncoding(), StandardCharsets.US_ASCII);
                if (myConnection.getStatus() == PortStatus.DISCONNECTED || myConnection.getStatus() == PortStatus.READY) {
                    // try to connect only when settings are known to be valid
                    getPrimaryConsoleView().reconnect(getCharset(), myPortProfile.getNewLine(), myPortProfile.getLocalEcho());
                    myConnection.connect(myPortProfile);
                }
                else {
                    throw new SerialMonitorException(SerialMonitorLocalize.serialPortNotFound(myPortProfile.getPortName()).get());
                }
            }
            else {
                myConnection.close(true);
            }
        }
        catch (SerialMonitorException sme) {
            SerialMonitor.Companion.errorNotification(sme.getMessage(), this);
        }
    }

    public void reconnect() {
        myLoadingPanel.startLoading();
        Application.get().executeOnPooledThread(
            () -> {
                try {
                    performConnect(false);
                    performConnect(true);
                }
                finally {
                    Application.get().invokeLater(myLoadingPanel::stopLoading);
                }
            }
        );
    }

    public @Nonnull Charset getCharset() {
        return myCharset;
    }

    public boolean isLoading() {
        return myLoadingPanel.isLoading();
    }

    private class SwitchConsoleViewAction extends ToggleAction implements DumbAware {

        private SwitchConsoleViewAction() {
            super(SerialMonitorLocalize.switchConsoleViewToHexTitle(), LocalizeValue.empty(), SerialMonitorIconGroup.hexserial());
        }

        @Override
        public @Nonnull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }

        @Override
        public void update(@Nonnull AnActionEvent e) {
            super.update(e);
            LocalizeValue text = isPrimaryConsoleEnabled() ?
                SerialMonitorLocalize.switchConsoleViewToHexTitle() :
                SerialMonitorLocalize.switchConsoleViewOffHexTitle();
            e.getPresentation().setTextValue(text);
        }

        @Override
        public boolean isSelected(final @Nonnull AnActionEvent event) {
            return !isPrimaryConsoleEnabled();
        }

        @Override
        public void setSelected(final @Nonnull AnActionEvent event, final boolean flag) {
            enableConsole(!flag);
            ApplicationPropertiesComponent.getInstance().setValue(STATE_STORAGE_KEY, Boolean.toString(!flag));
        }
    }

    private class ClearAllAction extends DumbAwareAction {

        private ClearAllAction() {
            super(ExecutionLocalize.clearAllFromConsoleActionName(), SerialMonitorLocalize.actionClearContentsConsoleDescription(), AllIcons.Actions.GC);
        }

        @Override
        public @Nonnull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }


        @Override
        public void update(@Nonnull AnActionEvent e) {
            boolean enabled = getContentSize() > 0;
            if (!enabled) {
                enabled = e.getData(ConsoleView.KEY) != null;
                Editor editor = e.getData(CommonDataKeys.EDITOR);
                if (editor != null && editor.getDocument().getTextLength() == 0) {
                    enabled = false;
                }
            }
            e.getPresentation().setEnabled(enabled);
        }

        @Override
        public void actionPerformed(final @Nonnull AnActionEvent e) {
            clear();
        }
    }

    public void append(byte[] dataChunk) {
        getPrimaryConsoleView().output(dataChunk);
        myHexConsoleView.output(dataChunk);
    }

    private class SerialPauseAction extends ToggleAction {

        @Override
        public @Nonnull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }

        private SerialPauseAction() {
            super(SerialMonitorLocalize.actionPauseText(), SerialMonitorLocalize.actionPauseDescription(),
                AllIcons.Actions.Pause);
        }

        @Override
        public boolean isSelected(@Nonnull AnActionEvent e) {
            return isOutputPaused();
        }

        @Override
        public void setSelected(@Nonnull AnActionEvent e, boolean state) {
            setOutputPaused(state);
        }
    }

    private static class MyScrollToTheEndToolbarAction extends ScrollToTheEndToolbarAction {
        private final Editor myEditor;

        private MyScrollToTheEndToolbarAction(Editor editor) {
            super(editor);
            myEditor = editor;
        }

        @Override
        public void update(@Nonnull AnActionEvent e) {
            if (myEditor.getComponent().isShowing()) {
                super.update(e);
            }
            else {
                e.getPresentation().setVisible(false);
            }
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        Application application = Application.get();
        application.executeOnPooledThread(() -> {
            myConnection.closeSilently(true);
        });
    }
}
