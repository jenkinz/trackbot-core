/*
 * Date: Oct 30, 2007
 * Time: 10:30:22 PM
 *
 * (c) 2007-2008 Systronix Inc.  All Rights reserved.
 * 939 Edison Street, Salt Lake City, UT, USA  84111
 * http://www.systronix.com/
 */
package com.systronix.trackbot;

import com.systronix.io.Debug;

/**
 * The base class for a factory that creates {@link RobotIO} objects.  These
 * use custom or standard input and output streams created by factory
 * implementations.
 * <p>
 * For example, the {@code com.systronix.trackbot.io.SunSpotRobotIOFactory
 * SunSpotRobotIOFactory}
 * factory implementation creates streams that use the Sun SPOT's odd eDemo
 * UART interface.  The {@link com.systronix.trackbot.io.ConnectionRobotIOFactory
 * ConnectionRobotIOFactory} implementation creates streams that use the GCF,
 * and the {@link com.systronix.trackbot.io.SerialPortRobotIOFactory
 * SerialPortRobotIOFactory} implementation utilizes the
 * <a href="http://java.sun.com/products/javacomm/"><code>javax.comm</code></a>
 * package on a JStamp or PC.</p>
 * <p>
 * Factory implementations may require special setup.  For example, the
 * {@link com.systronix.trackbot.io.SerialPortRobotIOFactory serial port}
 * factory implementation requires that the
 * {@link #PROP_ROBOT_IO_SERIAL_PORT} system property be set to the name of a
 * valid serial port.</p>
 * <p>
 * This base class has a {@link #createFactory()} method that uses some simple
 * heuristics to determine which subclass to instantiate.  You can use your
 * own default factory implementation by setting the {@link #PROP_ROBOT_IO_FACTORY}
 * system property to a valid factory class name.</p>
 *
 * @author Shawn Silverman
 * @version 0.1
 */
public abstract class RobotIOFactory {
    /**
     * This system property can specify the {@link RobotIO} factory for the
     * device.
     */
    public static final String PROP_ROBOT_IO_FACTORY = "com.systronix.trackbot.RobotIOFactory";

    /** The system property containing the baud rate. */
    public static final String PROP_ROBOT_IO_BAUD_RATE = "com.systronix.trackbot.RobotIO.baudRate";

    /**
     * The system property containing the serial port name, for example,
     * <code>"com2"</code>.
     */
    public static final String PROP_ROBOT_IO_SERIAL_PORT = "com.systronix.trackbot.RobotIO.serialPort";

    /**
     * The system property containing the I/O timeout.  If this is not set,
     * then the {@linkplain #DEFAULT_TIMEOUT default timeout} will be used.
     * Note that this property is not intended to specify the serial port
     * receive timeout.
     */
    public static final String PROP_ROBOT_IO_TIMEOUT = "com.systronix.trackbot.RobotIO.timeout";

    /** The default baud rate. */
    public static final int DEFAULT_BAUD_RATE = 19200;

    /** The default I/O timeout, in ms. */
    public static final int DEFAULT_TIMEOUT = 1000;

    /** The suggested size for the input and output buffers. */
    public static final int DEFAULT_BUFFER_SIZE = 64;

    /**
     * Creates a new <code>RobotIO</code> factory.
     */
    protected RobotIOFactory() {
    }

    /**
     * Tries to create a {@link RobotIOFactory} object using the given
     * <code>RobotIOFactory</code> class name.  This returns <code>null</code>
     * if the specified class name is <code>null</code> or if the class could
     * not be instantiated for any reason.
     *
     * @throws ExceptionInInitializerError if this code is not running on a
     *         CLDC platform and if an exception is thrown in the static
     *         initializer of the class.
     */
    private static RobotIOFactory tryCreateFactory(String className) {
        if (className == null) return null;

        try {
            Class clazz = Class.forName(className);

            // Check if the class is a subclass of RobotIOFactory

            if (RobotIOFactory.class.isAssignableFrom(clazz)) {
                RobotIOFactory factory = (RobotIOFactory)clazz.newInstance();
                Debug.info("Using RobotIOFactory: " + factory.getClass().getName());
                return factory;
            }
        } catch (ClassNotFoundException ex) {
            Debug.fine("RobotIOFactory: Class not found: " + className);
        } catch (InstantiationException ex) {
            Debug.fine("RobotIOFactory: Constructor access: "
                    + ((ex.getMessage() != null) ? ex.getMessage() : className));
        } catch (IllegalAccessException ex) {
            Debug.fine("RobotIOFactory: Inaccessible nullary constructor: " + className);
        }// NOTE There's no ExceptionInInitializerError in CLDC

        return null;
    }

    /**
     * Creates a new factory appropriate to the device.  This never returns
     * <code>null</code>.
     *
     * @return a new factory appropriate to the device.
     * @throws Error if a suitable factory could not be created.
     */
    public static RobotIOFactory createFactory() {
        // First look up the factory from the system property

    	// typically this won't be set
        RobotIOFactory factory = tryCreateFactory(System.getProperty(PROP_ROBOT_IO_FACTORY));
        if (factory != null) return factory;

        // SunSPOT custom

        if (System.getProperty("spot.hardware.rev") != null) {
            factory = tryCreateFactory("com.systronix.trackbot.io.SunSpotRobotIOFactory");
            if (factory != null) return factory;
        }

        // JStamp or JStik
        // to use this also set JemBuilder System property
        // com.systronix.trackbot.RobotIO.serialPort=com2

        String systronix = System.getProperty("systronix.module");
        if (systronix != null) {
            if ("jstamp".equals(systronix) || "jstik".equals(systronix)) {
                Debug.finer("RobotIOFactory: Trying JStamp/JStik factory");
                factory = tryCreateFactory("com.systronix.trackbot.io.SerialPortRobotIOFactory");
                if (factory != null) return factory;
            }
        }

        // Next, try a GCF-based one

        String config = System.getProperty("microedition.configuration");
        if (config != null) {
            config = config.toLowerCase();
            if (config.indexOf("cldc") >= 0 || config.indexOf("cdc") >= 0) {
                // The GCF should be available

                Debug.finer("RobotIOFactory: Trying JavaME factory");
                factory = tryCreateFactory("com.systronix.trackbot.io.ConnectionRobotIOFactory");
                if (factory != null) return factory;
            }
        }

        // Try the javax.comm or gnu.io versions

        try {
            Class.forName("gnu.io.SerialPort");
            Debug.finer("RobotIOFactory: Trying RXTX factory");
            factory = tryCreateFactory("com.systronix.trackbot.io.RXTXSerialPortRobotIOFactory");
        } catch (ClassNotFoundException ex) {
            try {
                Class.forName("javax.comm.SerialPort");
                Debug.finer("RobotIOFactory: Trying javax.comm factory");
                factory = tryCreateFactory("com.systronix.trackbot.io.SerialPortRobotIOFactory");
            } catch (ClassNotFoundException ex2) {
            }
        }
        if (factory != null) return factory;

        Debug.severe("No suitable robot I/O factory was found!");
        throw new Error("No suitable robot I/O factory was found");
    }

    /**
     * Creates a new I/O connection to the robot.  This may return
     * <code>null</code> without throwing an exception.
     *
     * @return a new connection to the robot I/O or <code>null</code>.
     * @throws Exception if there was an error while creating the I/O
     *         connection.
     */
    public abstract RobotIO createRobotIO() throws Exception;
}
