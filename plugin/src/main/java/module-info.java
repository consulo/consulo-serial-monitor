/**
 * @author VISTALL
 * @since 2026-01-31
 */
module consulo.serial.monitor {
    requires consulo.ide.api;

    requires com.fazecast.jSerialComm;
    requires jediterm;
    requires org.apache.commons.io;
    requires forms.rt;
}