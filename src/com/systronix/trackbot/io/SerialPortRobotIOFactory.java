/*
 * Date: Nov 10, 2007
 * Time: 5:51:41 PM
 *
 * (c) 2007 Systronix Inc.  All Rights reserved.
 * 939 Edison Street, Salt Lake City, UT, USA  84111
 * http://www.systronix.com/
 */
package com.systronix.trackbot.io;

import com.systronix.trackbot.RobotIO;
import com.systronix.trackbot.RobotIOFactory;
import com.systronix.io.Debug;

import javax.comm.CommPortIdentifier;
import javax.comm.SerialPort;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A {@link RobotIO} factory for the
 * <a href="http://java.sun.com/products/javacomm/"><code>javax.comm</code></a>
 * framework.  This uses system properties to supply the port name and baud
 * rate.
 *
 * @see javax.comm.SerialPort
 *
 * @author Shawn Silverman
 * @version 0.1
 */
public class SerialPortRobotIOFactory extends RobotIOFactory {
    /**
     * Creates a new {@link RobotIOFactory} for
     * <a href="http://java.sun.com/products/javacomm/"><code>javax.comm</code></a>.
     */
    public SerialPortRobotIOFactory() {
        super();
    }

    /**
     * Tries to create a connection to the robot I/O using the serial port and
     * baud rate specified in the {@linkplain #PROP_ROBOT_IO_SERIAL_PORT serial
     * port} and {@linkplain #PROP_ROBOT_IO_BAUD_RATE baud rate} system
     * properties.  If the baud rate is not specified, or if it is an invalid
     * integer, then a default of {@link #DEFAULT_BAUD_RATE} baud is used.
     * <p>
     * This returns <code>null</code> if the {@linkplain #PROP_ROBOT_IO_SERIAL_PORT
     * serial port} system property is not set.</p>
     *
     * @return a connection to the robot I/O or <code>null</code> if the
     *         {@linkplain #PROP_ROBOT_IO_SERIAL_PORT serial port} system
     *         property is not set.
     * @throws Exception if there while attempting to access the serial port.
     */
    public RobotIO createRobotIO() throws Exception {
        // Get the parameters

        String portName = System.getProperty(PROP_ROBOT_IO_SERIAL_PORT);
        if (portName == null) return null;

        int baudRate;
        try {
            baudRate = Integer.parseInt(System.getProperty(PROP_ROBOT_IO_BAUD_RATE));
        } catch (NumberFormatException ex) {
            Debug.warning("SerialPortRobotIOFactory: Bad baud rate in property "
                    + "\"" + PROP_ROBOT_IO_BAUD_RATE + "\": "
                    + System.getProperty(PROP_ROBOT_IO_BAUD_RATE));
            baudRate = DEFAULT_BAUD_RATE;
        }

        // Open the serial port

        CommPortIdentifier cpi = CommPortIdentifier.getPortIdentifier(portName);
        SerialPort port = (SerialPort)cpi.open(getClass().getName(), 5000);
        port.setSerialPortParams(baudRate,
                SerialPort.DATABITS_8,
                SerialPort.STOPBITS_1,
                SerialPort.PARITY_NONE);

        // Get the timeout

        int timeout;
        try {
            timeout = Integer.parseInt(System.getProperty(PROP_ROBOT_IO_TIMEOUT));
            if (timeout < 0) {
                Debug.warning("RXTXSerialPortRobotIOFactory: "
                        + "Negative timeout, using default");
                timeout = DEFAULT_TIMEOUT;
            }
        } catch (NumberFormatException ex) {
            Debug.warning("RXTXSerialPortRobotIOFactory: Bad timeout in property "
                    + "\"" + PROP_ROBOT_IO_TIMEOUT + "\": "
                    + System.getProperty(PROP_ROBOT_IO_TIMEOUT)
                    + "; using default");
            timeout = DEFAULT_TIMEOUT;
        }

        // Open the streams

        InputStream in = null;
        OutputStream out = null;
        try {
            in = port.getInputStream();
            out = port.getOutputStream();
            return new RobotIO(in, DEFAULT_BUFFER_SIZE,
                    out, DEFAULT_BUFFER_SIZE,
                    timeout);
        } catch (IOException ex) {
            // Proper cleanup in case of an error

            if (in != null) {
                try { in.close(); } catch (IOException ex2) { }
            }
            if (out != null) {
                try { out.close(); } catch (IOException ex2) { }
            }
            port.close();
            throw ex;
        }
    }
}
