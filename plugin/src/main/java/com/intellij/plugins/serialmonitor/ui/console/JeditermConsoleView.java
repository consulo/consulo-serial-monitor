package com.intellij.plugins.serialmonitor.ui.console;

import com.intellij.plugins.serialmonitor.SerialProfileService;
import com.intellij.plugins.serialmonitor.service.SerialPortService;
import com.jediterm.terminal.*;
import com.jediterm.terminal.emulator.JediEmulator;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.TerminalTextBuffer;
import consulo.application.AllIcons;
import consulo.disposer.Disposer;
import consulo.execution.ui.console.ConsoleView;
import consulo.execution.ui.console.ConsoleViewContentType;
import consulo.execution.ui.console.Filter;
import consulo.execution.ui.console.HyperlinkInfo;
import consulo.process.ProcessHandler;
import consulo.project.Project;
import consulo.serialMonitor.localize.SerialMonitorLocalize;
import consulo.ui.ex.action.*;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.io.input.buffer.CircularByteBuffer;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

public class JeditermConsoleView implements ConsoleView {

    private static final int BUFFER_SIZE = 100000;

    private final JBTerminalWidget widget;
    private final SerialTtyConnector serialConnector;
    private CustomJeditermEmulator emulator;
    private final CircularByteBuffer bytesBuffer = new CircularByteBuffer(BUFFER_SIZE);
    private final Object lock = new Object();
    private volatile Reader bufferReader;
    private volatile boolean paused = false;

    private final InputStream bytesStream = new InputStream() {
        @Override
        public int read() {
            synchronized (lock) {
                while (!Thread.interrupted() && !bytesBuffer.hasBytes()) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                return bytesBuffer.hasBytes() ? bytesBuffer.read() & 0xFF : -1;
            }
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            synchronized (lock) {
                while (!Thread.interrupted() && !bytesBuffer.hasBytes()) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                int toRead = Math.min(length, bytesBuffer.getCurrentNumberOfBytes());
                bytesBuffer.read(buffer, offset, toRead);
                return toRead;
            }
        }

        @Override
        public int available() {
            synchronized (lock) {
                return bytesBuffer.getCurrentNumberOfBytes();
            }
        }
    };

    public JeditermConsoleView(@Nonnull Project project, @Nonnull SerialPortService.SerialConnection connection) {
        this.serialConnector = new SerialTtyConnector(this, connection);

        widget = new JBTerminalWidget(project, new JBTerminalSystemSettingsProviderBase(), this) {
            @Override
            protected @Nonnull TerminalStarter createTerminalStarter(@Nonnull JediTerminal terminal, @Nonnull TtyConnector connector) {
                return new TerminalStarter(terminal, connector,
                        new TtyBasedArrayDataStream(connector, () -> getTypeAheadManager().onTerminalStateChanged()),
                        getTypeAheadManager(), getExecutorServiceManager()) {
                    @Override
                    protected @Nonnull JediEmulator createEmulator(@Nonnull TerminalDataStream dataStream, @Nonnull Terminal terminal) {
                        emulator = new CustomJeditermEmulator(dataStream, terminal);
                        return emulator;
                    }
                };
            }
        };
        widget.start(serialConnector);
        Disposer.register(this, connection);
    }

    @Override
    public void dispose() {
    }

    @Override
    public @Nonnull JComponent getComponent() {
        return widget;
    }

    @Override
    public @Nonnull JComponent getPreferredFocusableComponent() {
        return widget;
    }

    @Override
    public void print(@Nonnull String text, @Nonnull ConsoleViewContentType contentType) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void clear() {
        widget.getTerminalTextBuffer().clearScreenAndHistoryBuffers();
        widget.getTerminal().clearScreen();
        widget.getTerminal().cursorPosition(0, 1);
    }

    @Override
    public void scrollTo(int offset) {
        widget.getTerminal().setScrollingRegion(offset, Integer.MAX_VALUE);
    }

    @Override
    public void attachToProcess(@Nonnull ProcessHandler processHandler) {
        throw new IllegalArgumentException("Should not be called");
    }

    @Override
    public void setOutputPaused(boolean value) {
        paused = value;
    }

    @Override
    public boolean isOutputPaused() {
        return paused;
    }

    @Override
    public boolean hasDeferredOutput() {
        return false;
    }

    @Override
    public void performWhenNoDeferredOutput(@Nonnull Runnable runnable) {
        runnable.run();
    }

    @Override
    public void setHelpId(@Nonnull String helpId) {
    }

    @Override
    public void addMessageFilter(@Nonnull Filter filter) {
        throw new UnsupportedOperationException("Operation not supported");
    }

    @Override
    public void printHyperlink(@Nonnull String hyperlinkText, @Nullable HyperlinkInfo info) {
        print(hyperlinkText, ConsoleViewContentType.NORMAL_OUTPUT);
    }

    @Override
    public int getContentSize() {
        TerminalTextBuffer buffer = widget.getTerminalTextBuffer();
        return buffer.getScreenLinesCount() + buffer.getHistoryLinesCount();
    }

    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public AnAction[] createConsoleActions() {
        return AnAction.EMPTY_ARRAY;
    }

    @Override
    public void allowHeavyFilters() {
    }

    public boolean isTimestamped() {
        return emulator != null && emulator.isTimestamped();
    }

    public void output(byte[] dataChunk) {
        if (!paused) {
            synchronized (lock) {
                int length = Math.min(dataChunk.length, bytesBuffer.getSpace());
                if (length > 0) {
                    bytesBuffer.add(dataChunk, 0, length);
                    lock.notify();
                }
            }
        }
    }

    public void reconnect(@Nonnull Charset charset, @Nonnull SerialProfileService.NewLine newLine, boolean localEcho) {
        if (emulator != null) {
            emulator.setNewLine(newLine);
        }
        widget.getTerminal().setAutoNewLine(newLine == SerialProfileService.NewLine.CRLF);
        serialConnector.setCharset(charset);
        serialConnector.setLocalEcho(localEcho);
        synchronized (lock) {
            bytesBuffer.clear();
            bufferReader = new InputStreamReader(bytesStream, charset);
        }
    }

    public int readChars(char[] buf, int offset, int length) throws IOException {
        synchronized (lock) {
            while (true) {
                Reader currentReader = bufferReader;
                if (currentReader != null && currentReader.ready()) {
                    return currentReader.read(buf, offset, length);
                }
                try {
                    lock.wait();
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    public @Nonnull TerminalTextBuffer getTerminalTextBuffer() {
        return widget.getTerminalTextBuffer();
    }

    public @Nonnull ToggleAction getScrollToTheEndToolbarAction() {
        return new ToggleAction(
                ActionsBundle.messagePointer("action.EditorConsoleScrollToTheEnd.text"),
                ActionsBundle.messagePointer("action.EditorConsoleScrollToTheEnd.text"),
                AllIcons.RunConfigurations.Scroll_down) {
            @Override
            public @Nonnull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }

            @Override
            public boolean isSelected(@Nonnull AnActionEvent e) {
                return widget.getTerminalPanel().getVerticalScrollModel().getValue() == 0;
            }

            @Override
            public void update(@Nonnull AnActionEvent e) {
                e.getPresentation().setEnabledAndVisible(widget.isShowing());
            }

            @Override
            public void setSelected(@Nonnull AnActionEvent e, boolean state) {
                if (state) {
                    widget.getTerminalPanel().getVerticalScrollModel().setValue(0);
                }
            }
        };
    }

    public @Nonnull ToggleAction getPrintTimestampsToggleAction() {
        return new ToggleAction(
                SerialMonitorLocalize.actionPrintTimestampsText(),
                SerialMonitorLocalize.actionPrintTimestampsDescription(),
                AllIcons.Scope.Scratches) {
            @Override
            public void update(@Nonnull AnActionEvent e) {
                e.getPresentation().setEnabled(widget.isShowing());
            }

            @Override
            public boolean isSelected(@Nonnull AnActionEvent e) {
                return isTimestamped();
            }

            @Override
            public void setSelected(@Nonnull AnActionEvent e, boolean isSelected) {
                if (emulator != null) {
                    emulator.setTimestamped(isSelected);
                }
            }

            @Override
            public @Nonnull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
    }
}
