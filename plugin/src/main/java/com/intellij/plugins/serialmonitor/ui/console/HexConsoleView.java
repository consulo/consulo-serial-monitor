package com.intellij.plugins.serialmonitor.ui.console;

import consulo.disposer.Disposable;
import consulo.execution.ui.console.*;
import consulo.project.Project;

/**
 * @author Dmitry_Cherkas
 */
public class HexConsoleView implements Disposable {

    public static final int LINE_LENGTH = 32;
    public static final int SEQUENCE_LENGTH = 8;
    private final char[] hexChar = {'0', '1', '2', '3', '4', '5', '6',
        '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    private final StringBuffer lineBuffer = new StringBuffer();

    private ConsoleView myConsoleView;

    public HexConsoleView(Project project, boolean viewer) {
        TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
        builder.setViewer(viewer);
        myConsoleView = builder.getConsole();
        if (myConsoleView instanceof ObservableConsoleView observableConsoleView) {
            observableConsoleView.addChangeListener(new ObservableConsoleView.ChangeListener() {
                @Override
                public void textCleared() {
                    lineBuffer.setLength(0);
                }
            }, this);
        }
    }

    public ConsoleView getConsoleView() {
        return myConsoleView;
    }

    public void output(byte[] dataChunk) {
        StringBuilder buffer = new StringBuilder();
        for (byte b : dataChunk) {
            char c = (char) (b & 0xff);
            buffer.append(hexChar[c >> 4])
                .append(hexChar[c & 0xf])
                .append(' ');
            lineBuffer.append(c);

            if (lineBuffer.length() == LINE_LENGTH) {
                wrapLine(buffer);
            }
            else if (lineBuffer.length() % SEQUENCE_LENGTH == 0) {
                buffer.append(' ');
            }
        }
        myConsoleView.print(buffer.toString(), ConsoleViewContentType.NORMAL_OUTPUT);
    }

    private void wrapLine(StringBuilder buffer) {
        buffer.append(" |  ");
        for (int i = 0; i < LINE_LENGTH; i++) {
            char c = lineBuffer.charAt(i);
            if (c >= 0x20 && c <= 0x7f) {
                buffer.append(c);
            }
            else {
                // replace non-printable chars with dots
                buffer.append('.');
            }
        }
        buffer.append('\n');
        lineBuffer.setLength(0);
    }

    @Override
    public void dispose() {
    }
}
