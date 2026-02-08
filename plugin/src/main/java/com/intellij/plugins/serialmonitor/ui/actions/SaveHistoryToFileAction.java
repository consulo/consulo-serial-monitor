package com.intellij.plugins.serialmonitor.ui.actions;

import com.intellij.plugins.serialmonitor.SerialPortProfile;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;
import consulo.application.AllIcons;
import consulo.application.Application;
import consulo.fileChooser.FileChooserFactory;
import consulo.fileChooser.FileSaverDescriptor;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.serialMonitor.localize.SerialMonitorLocalize;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.virtualFileSystem.VirtualFileWrapper;
import jakarta.annotation.Nonnull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Jan Papesch
 */
public class SaveHistoryToFileAction extends DumbAwareAction {

    private final TerminalTextBuffer terminalTextBuffer;
    public SaveHistoryToFileAction(@Nonnull TerminalTextBuffer terminalTextBuffer, @Nonnull SerialPortProfile serialPortProfile) {
        super(SerialMonitorLocalize.actionSaveText(),
            SerialMonitorLocalize.actionSaveDescription(),
            PlatformIconGroup.actionsMenu_saveall()
        );
        this.terminalTextBuffer = terminalTextBuffer;
        this.serialPortProfile = serialPortProfile;
    }

    private final SerialPortProfile serialPortProfile;

    private @Nonnull String defaultLogFilename() {
        return Paths.get(serialPortProfile.defaultName()).getFileName().toString() + ".log";
    }

    @Override
    public void actionPerformed(@Nonnull AnActionEvent e) {
        FileSaverDescriptor descriptor = new FileSaverDescriptor(
                SerialMonitorLocalize.dialogSaveTitle().get(),
                SerialMonitorLocalize.dialogSaveDesc().get()
        );

        VirtualFileWrapper wrapper = FileChooserFactory.getInstance()
                .createSaveFileDialog(descriptor, e.getData(Project.KEY))
                .save(null, defaultLogFilename());

        if (wrapper == null) return;
        File file = wrapper.getFile();

        Application.get().executeOnPooledThread(() -> {
            List<TerminalLine> lines;
            try {
                terminalTextBuffer.lock();
                List<TerminalLine> historyLines = terminalTextBuffer.getHistoryLinesStorage();
                List<TerminalLine> screenLines = terminalTextBuffer.getScreenLinesStorage();
                lines = new ArrayList<>(historyLines.size() + screenLines.size());
                lines.addAll(historyLines);
                lines.addAll(screenLines);
            } finally {
                terminalTextBuffer.unlock();
            }

            StringBuilder text = new StringBuilder();
            String lineSeparator = System.lineSeparator();
            for (TerminalLine line : lines) {
                text.append(line.getText());
                text.append(lineSeparator);
            }

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(text.toString());
            } catch (IOException ex) {
                // Log error silently
            }
        });
    }
}
