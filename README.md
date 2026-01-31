# Serial Monitor plugin #


This plugin provides the Serial Monitor Tool Window, which allows you to communicate to serial devices like Arduino via the tool window.

## Features ##

- Multiple serial port connections
- Duplex Console view (Regular view + HEX)

## Troubleshooting ##

On Linux you may not see you port in available ports dropdown due to lack or permissions.

- To check if serial port is really there use ```dmesg | grep tty```
- To set read and write permission to port, use ```sudo chmod a+rw <your_port_name>```, where **your_port_name** should
  be something similar to _/dev/ttyACM0_, use the output of the above command to check the exact name.
