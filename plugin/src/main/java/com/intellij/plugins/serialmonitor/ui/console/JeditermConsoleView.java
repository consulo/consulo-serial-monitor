package com.intellij.plugins.serialmonitor.ui.console;

import com.intellij.plugins.serialmonitor.SerialProfileService;
import com.intellij.plugins.serialmonitor.service.SerialPortService;
import com.jediterm.terminal.model.TerminalTextBuffer;
import consulo.application.AllIcons;
import consulo.disposer.Disposer;
import consulo.execution.ui.console.ConsoleView;
import consulo.execution.ui.console.ConsoleViewContentType;
import consulo.execution.ui.console.Filter;
import consulo.execution.ui.console.HyperlinkInfo;
import consulo.execution.ui.terminal.JediTerminalConsole;
import consulo.execution.ui.terminal.TerminalConsoleFactory;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.platform.base.localize.ActionLocalize;
import consulo.process.ProcessHandler;
import consulo.process.event.ProcessEvent;
import consulo.project.Project;
import consulo.serialMonitor.localize.SerialMonitorLocalize;
import consulo.ui.ex.action.ActionUpdateThread;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.ToggleAction;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.io.input.buffer.CircularByteBuffer;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.function.BiPredicate;

public class JeditermConsoleView implements ConsoleView {

    private static final int BUFFER_SIZE = 100000;

    private final JediTerminalConsole widget;
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
                    }
                    catch (InterruptedException e) {
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
                    }
                    catch (InterruptedException e) {
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

        widget = project.getInstance(TerminalConsoleFactory.class).createCustom(this, (terminalDataStream, terminal) -> {
            emulator = new CustomJeditermEmulator(terminalDataStream, terminal);
            return emulator;
        }, serialConnector);

        Disposer.register(this, connection);
    }

    @Override
    public void dispose() {
    }

    @Override
    public @Nonnull JComponent getComponent() {
        return (JComponent) TargetAWT.to(widget.getUIComponent());
    }

    @Override
    public @Nonnull JComponent getPreferredFocusableComponent() {
        return getComponent();
    }

    @Override
    public void print(@Nonnull String text, @Nonnull ConsoleViewContentType contentType) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void clear() {
        widget.getTerminalTextBuffer().clearAll();
        widget.getTerminalTextBuffer().clearHistory();
        //widget.getTerminalTextBuffer().clearScreenAndHistoryBuffers();
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
    public void setProcessTextFilter(@Nullable BiPredicate<ProcessEvent, Key> biPredicate) {
    }

    @Nullable
    @Override
    public BiPredicate<ProcessEvent, Key> getProcessTextFilter() {
        return null;
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
                }
                catch (InterruptedException ignored) {
                }
            }
        }
    }

    public @Nonnull TerminalTextBuffer getTerminalTextBuffer() {
        return widget.getTerminalTextBuffer();
    }

    public @Nonnull ToggleAction getScrollToTheEndToolbarAction() {
        return new ToggleAction(
            ActionLocalize.actionEditorconsolescrolltotheendText(),
            ActionLocalize.actionEditorconsolescrolltotheendText(),
            AllIcons.RunConfigurations.Scroll_down) {
            @Override
            public @Nonnull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }

            @Override
            public boolean isSelected(@Nonnull AnActionEvent e) {
                return widget.getTerminalVerticalScrollModel().getValue() == 0;
            }

            @Override
            public void update(@Nonnull AnActionEvent e) {
                e.getPresentation().setEnabledAndVisible(widget.isShowing());
            }

            @Override
            public void setSelected(@Nonnull AnActionEvent e, boolean state) {
                if (state) {
                    widget.getTerminalVerticalScrollModel().setValue(0);
                }
            }
        };
    }

    public @Nonnull ToggleAction getPrintTimestampsToggleAction() {
        return new ToggleAction(
            SerialMonitorLocalize.actionPrintTimestampsText(),
            SerialMonitorLocalize.actionPrintTimestampsDescription(),
            PlatformIconGroup.scopeScratches()) {
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
