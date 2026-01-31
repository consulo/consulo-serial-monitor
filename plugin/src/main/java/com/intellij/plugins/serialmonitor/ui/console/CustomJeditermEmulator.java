package com.intellij.plugins.serialmonitor.ui.console;

import com.intellij.plugins.serialmonitor.SerialProfileService;
import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.TerminalDataStream;
import com.jediterm.terminal.emulator.JediEmulator;
import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CustomJeditermEmulator extends JediEmulator {

    public static final String TIMESTAMP_FORMAT = "[HH:mm:ss.SSS] ";

    private SerialProfileService.NewLine newLine = SerialProfileService.NewLine.CRLF;
    private boolean isTimestamped = false;
    private boolean lastCharCR = false;
    private final SimpleDateFormat myFormatter = new SimpleDateFormat(TIMESTAMP_FORMAT);

    public CustomJeditermEmulator(@Nonnull TerminalDataStream dataStream, @Nonnull Terminal terminal) {
        super(dataStream, terminal);
    }

    public @Nonnull SerialProfileService.NewLine getNewLine() {
        return newLine;
    }

    public void setNewLine(@Nonnull SerialProfileService.NewLine newLine) {
        this.newLine = newLine;
    }

    public boolean isTimestamped() {
        return isTimestamped;
    }

    public void setTimestamped(boolean timestamped) {
        isTimestamped = timestamped;
    }

    private @Nonnull String getTimestamp() {
        return myFormatter.format(new Date());
    }

    private boolean isOnLineStart(@Nonnull Terminal terminal) {
        return terminal.getCursorX() == 1; // cursorX starts at 1
    }

    private void maybeInsertTimestamp(char nextChar, @Nonnull Terminal terminal) {
        if (!isTimestamped) return; // Insert only when enabled
        if (!isOnLineStart(terminal)) return; // Insert only at the beginning of lines
        if (lastCharCR && nextChar == '\n' && newLine == SerialProfileService.NewLine.CRLF) return; // Don't insert in the middle of CRLF

        terminal.writeCharacters(getTimestamp());
    }

    @Override
    public void processChar(char ch, @Nonnull Terminal terminal) throws IOException {
        maybeInsertTimestamp(ch, terminal);
        switch (ch) {
            case '\r':
                terminal.carriageReturn();
                if (newLine == SerialProfileService.NewLine.CR) {
                    terminal.newLine();
                } else if (isTimestamped) {
                    // Move the cursor after the timestamp (and leave the timestamp unchanged)
                    terminal.cursorForward(TIMESTAMP_FORMAT.length());
                }
                break;
            case '\n':
                if (newLine == SerialProfileService.NewLine.LF || isTimestamped) {
                    // move the cursor to the start of the line, CR moves it after the timestamp.
                    terminal.carriageReturn();
                }
                terminal.newLine();
                break;
            default:
                super.processChar(ch, terminal);
                break;
        }
        lastCharCR = ch == '\r';
    }

    @Override
    protected void unsupported(char... sequenceChars) {
        // Ignore unsupported sequences silently
    }
}
