/**
 * @author VISTALL
 * @since 2026-01-31
 */
module consulo.serial.monitor {
    requires consulo.language.editor.api;
    requires consulo.execution.api;
    requires consulo.execution.debug.api;
    requires consulo.file.chooser.api;
    requires consulo.ui.ex.api;
    requires consulo.project.ui.api;
    requires consulo.process.api;

    requires com.fazecast.jSerialComm;
    requires jediterm.core;
    requires org.apache.commons.io;
    requires forms.rt;

    opens com.intellij.plugins.serialmonitor to consulo.util.xml.serializer;
}