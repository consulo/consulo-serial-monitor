package com.intellij.plugins.serialmonitor;

/**
 * @author Dmitry_Cherkas, Ilia Motornyi
 */
public class SerialMonitorException extends Exception {

  public SerialMonitorException(String message) {
    super(message);
  }

  public SerialMonitorException(String message, Throwable cause) {
    super(message + ": " + cause.getLocalizedMessage(), cause);
  }
}
