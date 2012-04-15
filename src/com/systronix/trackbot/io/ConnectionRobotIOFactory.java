/*
 * Date: Nov 10, 2007
 * Time: 5:11:19 PM
 *
 * (c) 2007 Systronix Inc.  All Rights reserved.
 * 939 Edison Street, Salt Lake City, UT, USA  84111
 * http://www.systronix.com/
 */
package com.systronix.trackbot.io;

import com.systronix.trackbot.RobotIO;
import com.systronix.trackbot.RobotIOFactory;

import javax.microedition.io.StreamConnection;
import javax.microedition.io.Connector;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

/**
 * A {@link RobotIO} factory for the GCF.  This uses a system property to
 * supply the connection string.
 *
 * @see Connector
 *
 * @author Shawn Silverman
 * @version 0.0
 */
public class ConnectionRobotIOFactory extends RobotIOFactory {
    // Some common connection strings

    /**
     * A convenience connection URI for the SunSPOT.  It specifies the "usart"
     * connection and the following parameters.
     * <ul>
     * <li>baudrate={@link #DEFAULT_BAUD_RATE}</li>
     * </ul>
     */
    //public static final String SUNSPOT_CONNECTION_URI = "serial://usart?baudrate=" + DEFAULT_BAUD_RATE + "&databits=8&parity=none&stopbits=1";
    public static final String SUNSPOT_CONNECTION_URI = "edemoserial://usart?baudrate=" + DEFAULT_BAUD_RATE;

    /**
     * A convenience connection URI for the JStamp.  It specified the following
     * parameters.
     * <ul>
     * <li>baudrate={@link #DEFAULT_BAUD_RATE}</li>
     * <li>bitsperchar=8</li>
     * <li>stopbits=1</li>
     * <li>parity=none</li>
     * <li>blocking=off</li>
     * <li>autocts=off</li>
     * <li>autorts=off</li>
     * </ul>
     * <p>
     * Note that the aJile firmware may not support connecting to a serial
     * port using the GCF.</p>
     */
    public static final String JSTAMP_CONNECTION_URI = "comm:com2;baudrate=" + DEFAULT_BAUD_RATE + ";bitsperchar=8;stopbits=1;parity=none;blocking=off;autocts=off;autorts=off";

    /** The system property containing the connection URI for the RobotIO. */
    public static final String PROP_ROBOT_IO_CONNECTION_URI = "com.systronix.trackbot.RobotIO.connectionURL";

    /**
     * Creates a new {@link RobotIOFactory} for the GCF.
     */
    public ConnectionRobotIOFactory() {
        super();
    }

    /**
     * Tries to create a connection to the robot I/O using the connection
     * uri stored in the {@linkplain #PROP_ROBOT_IO_CONNECTION_URI connection
     * URI} system property.  This attempts to open the connection with
     * read/write access and with timeout exceptions requested.
     * <p>
     * This returns <code>null</code> if this system property is not set.</p>
     *
     * @return a connection to the robot I/O or <code>null</code> if the
     *         {@linkplain #PROP_ROBOT_IO_CONNECTION_URI connection URI}
     *         system property is not set.
     * @throws Exception if there was an error opening the connection or the
     *         connection streams.
     * @see Connector#open(String, int, boolean)
     */
    public RobotIO createRobotIO() throws Exception {
        String uri = System.getProperty(PROP_ROBOT_IO_CONNECTION_URI);
        if (uri == null) {
            // Try various platforms

            String s;
            if (System.getProperty("spot.hardware.rev") != null) {
                // SunSPOT

                uri = SUNSPOT_CONNECTION_URI;
            } else if ((s = System.getProperty("systronix.module")) != null
                       && s.equalsIgnoreCase("jstamp")) {
                uri = JSTAMP_CONNECTION_URI;
            } else {
                return null;
            }
        }

        // Open the connection

        StreamConnection conn = (StreamConnection)Connector.open(
                uri, Connector.READ_WRITE, true);

        // Get the timeout

        int timeout;
        try {
            timeout = Integer.parseInt(System.getProperty(PROP_ROBOT_IO_TIMEOUT));
            if (timeout < 0) {
                timeout = DEFAULT_TIMEOUT;
            }
        } catch (NumberFormatException ex) {
            timeout = DEFAULT_TIMEOUT;
        }

        // Open the streams

        InputStream in = null;
        OutputStream out = null;
        try {
            in = conn.openInputStream();
            out = conn.openOutputStream();
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
            try { conn.close(); } catch (IOException ex2) { }
            throw ex;
        }
    }
}
