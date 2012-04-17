/*
 * Date: Mar 22, 2008
 * Time: 5:47:38 PM
 * (c) 2008 Shawn Silverman and Systronix Inc
 */
package com.systronix.trackbot.test;

import com.systronix.trackbot.RobotIO;
import com.systronix.trackbot.RobotIOFactory;
import com.systronix.io.Debug;
import com.qindesign.util.PseudoThread;

import java.io.IOException;
import java.util.Vector;

/**
 * A basic test application for the Systronix TrackBot.
 * The {@link #runTests()} method is where all the tests should be started or run.
 * <h1>Purpose & Intent:</h1>
 * <ol>
 * <li>This app is intended to be useful in manufacturing, servicing, and verifying
 * that all sensors and other functions are working as intended.</li>
 * <li>We wish to re-use as much of the actual TrackBot reference application
 * runtime library and code as possible, both to save time, and as additional
 * verification that the reference application code is indeed correct. Here
 * we have a PC screen to report malformed messages, overrun serial buffers, and
 * similar problems which are awkward to display in a live swarm app, for example.</li>
 * <li>We need a simple-as-possible application which can run on a PC, connected
 * by a cable or RF modem to TrackBot, so that any Application Brain issues
 * (e.g., vendor specific UART or Thread implementation) are minimized.</li>
 * <li>We prefer to not require the hardware expense and cross-compilation
 * issues of a physical Application Brain while testing, esp in manufacturing.</li>
 * <li>We also require a reliable test when developing new TrackBot runtime releases.</li>
 * <li>We intend to wrap a simple GUI, perhaps written in NetBeans with Matisse,
 * to make it easy for any technician or service person to diagnose any TrackBot
 * issues using a series of simple buttons and prompts.</li>
 * <li>We'd like the status of all tests and recommendations for any problems found,
 * to be displayed in fields on the screen so that they can't be easily missed
 * (as can endless screens of System.out text to the stderr port).</li>
 * </ol>
 * <h1>What the GUI should display and control</h1>
 * <ol>
 * <li>Sensor enable bits and gain settings</li>
 * <li>Sensor obstacle detect status</li>
 * <li>Most recent beacon code detected by each sensor</li>
 * </ol>
 * <p>
 * To use this app, ensure that reflection and/or {@link Class#forName(String)}
 * information is available for a suitable {@link RobotIOFactory}.  Also, set
 * any required configuration system properties.</p>
 * <p>
 * For example, on the JStamp, add <code>Class.forName</code> information for
 * <code>com.systronix.trackbot.io.SerialPortRobotIOFactory</code>, and also
 * set the
 * {@link com.systronix.trackbot.RobotIOFactory#PROP_ROBOT_IO_SERIAL_PORT
 * PROP_ROBOT_IO_SERIAL_PORT} system property to an appropriate serial port
 * name, <code>"com2"</code>, for example.</p>
 * <p>
 * Additionally, don't forget to set this class as the "main" class.</p>
 * <h1>Related Code</h1>
 * {@link com.systronix.trackbot.demo} has classes such as Avoider and RobotMonitor
 * which show other uses of the underlying TrackBot Java APIs. These classes
 * typically extend {@link com.systronix.trackbot.Robot} which is where event listeners
 * are implemented.
 * <h1>REVISIONS</h1>
 * 0.1 20080327 bboyes class comments about purpose and intent, and adding GUI.
 * Working on code to actually get the first tests running on a TrackBot.
 * Written at Squaw Flat campground, Needles district, Canyonlands National Park.
 * Have along a fully charged TrackBot HW rev 2.2, USB serial adapter, etc.
 * Using an IBM T43 notebook and Targus DC70U 12V adapter. It's not comfortable sitting
 * in the passenger seat of a pickup for very long... outside there's a gusty wind and
 * blowing sand -- not to mention ambient light rather too bright for notebook use.
 *
 * @author Shawn Silverman
 * @author Bruce Boyes
 * @version 0.1
 */
public class TestApp implements RobotIO.Listener {
    /**
     * Main application entry point.
     *
     * @param args the program arguments.
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        TestApp tester = new TestApp();
        tester.runTests();
    }

    // Robot messages, convenience byte arrays

    private static final byte[] POWER_NODE_QUERY = {
            '?', 'P', '\r' };
    private static final byte[] SENSOR_NODE_QUERY = {
            '?', 'S', '\r' };
    private static final byte[] VERSION_QUERY = {
            '?', 'V', '\r' };
    private static final byte[] SERIAL_NUMBER_QUERY = {
            '?', 'C', 'S', '\r' };
    private static final byte[] ENABLE_CORNERS_CMD = {
            '!', 'P', 'P', 'E', '2', '4', '0', '\r' };
    private static final byte[] ENABLE_SIDES_CMD = {
            '!', 'S', 'P', 'E', '1', '5', '\r' };
    private static final byte[] SET_CORNER_RANGE_CMD = {
            '!', 'P', 'P', 'R', '3', '\r' };
    private static final byte[] SET_SIDE_RANGE_CMD = {
            '!', 'S', 'P', 'R', '4', '8', '\r' };  // Range * 16

    private static final byte[] MOTORS_FORWARD_CMD = {
            '!', 'M', 'A', '+', '0', '9' };
    private static final byte[] MOTORS_BACK_CMD = {
            '!', 'M', 'A', '-', '0', '9' };
    private static final byte[] MOTORS_STOP_CMD = {
            '!', 'M', 'A', '*', '0', '0' };
    private static final byte[] MOTOR_PORT_FORWARD_CMD = {
            '!', 'M', 'P', '+', '0', '6' };
    private static final byte[] MOTOR_PORT_BACK_CMD = {
            '!', 'M', 'P', '-', '0', '6' };
    private static final byte[] MOTOR_STARBOARD_FORWARD_CMD = {
            '!', 'M', 'S', '+', '0', '6' };
    private static final byte[] MOTOR_STARBOARD_BACK_CMD = {
            '!', 'M', 'S', '-', '0', '6' };

    private int powerNodeState = -1;
    private int sensorNodeState = -1;

    // Instance fields

    private RobotIO robotIO;

    /** Indicates whether the tests should be stopped. */
    private volatile boolean stopped;

    /**
     * This holds any threads used to do tests.  This is used to make stopping
     * all the threads easy.  Any new threads or periodic code should be added
     * to this list.
     */
    private Vector threads = new Vector();

    /**
     * Creates a new test application.  This connects to the robot.
     *
     * @throws Exception if there was an error connecting to the robot.
     */
    private TestApp() throws Exception {
        // Set the debug streams to something appropriate
        // The default is System.out

        //Debug.setOutputStream(my-UART-output-stream);

        // Connect to the robot

        robotIO = RobotIOFactory.createFactory().createRobotIO();
        robotIO.setListener(this);
    }

    /**
     * Stops the tests.  This also stops any registered threads.
     */
    private void stopTests() {
        stopped = true;

        // Stop all the threads

        for (int i = threads.size(); --i >= 0; ) {
            Object t = threads.elementAt(i);
            if (t instanceof PseudoThread) {
                ((PseudoThread)t).stop();
            } else if (t instanceof Thread) {
                ((Thread)t).interrupt();
            }
        }
    }

    /**
     * Runs the tests.  This starts a polling thread and a thread that prints
     * statistics every 2 seconds.
     */
    private void runTests() {
        // Send a version query

        queueMessage(VERSION_QUERY);
        // OK now get the version response and use it -- how?
        // message will be received by RobotIO class?



        // Enable the sensors

        queueMessage(ENABLE_CORNERS_CMD);
        queueMessage(ENABLE_SIDES_CMD);

        // Send power and sensor node queries at an interval of 200ms

        PseudoThread t = new PseudoThread(200L) {
                public void doWork() {
                    // Send the two queries

                    queueMessage(POWER_NODE_QUERY);
                    queueMessage(SENSOR_NODE_QUERY);
                }
            };
        threads.addElement(t);
        t.start();

        // Check the data statistics every 2 seconds

        t = new PseudoThread(2000L) {
                public void doWork() {
                    // Check the statistics and then print them

                    int ackCount = robotIO.getAckCount();
                    int nakCount = robotIO.getNakCount();
                    int inputOverflowCount = robotIO.getInputOverflowCount();
                    int sentCount = robotIO.getSentCount();

                    Debug.info("SENT count=" + sentCount);
                    Debug.info("ACK count =" + ackCount);
                    if (nakCount > 0) {
                        Debug.info("NAK count =" + nakCount);
                    }
                    if (inputOverflowCount > 0) {
                        Debug.info("INO count =" + inputOverflowCount);
                    }
                }
            };
        threads.addElement(t);
        t.start();
    }

    /**
     * Queues a message, and if that fails, {@link #outputOverflow(byte[])} is
     * called.
     *
     * @param msg the message to queue
     */
    private void queueMessage(byte[] msg) {
        if (!robotIO.queueMessage(msg)) {
            outputOverflow(msg);
        }
    }

    /**
     * Indicates that the output queue cannot hold a new message.  The
     * attempted message is given as an argument.
     *
     * @param msg the message that was to be sent
     */
    private void outputOverflow(byte[] msg) {
        Debug.warning("OUTPUT overflow: " + new String(msg, 0, msg.length));
    }

    /**
     * Converts a hexadecimal stream of bytes into an integer.  The digits are
     * expected to be in MSB (big endian) order.
     * <p>
     * This does not do any special checking on the length or the validity of
     * the digits.</p>
     *
     * @param b the hex digits
     * @param off offset into the array
     * @param len hex digit count
     * @return the hex bytes as an integer.
     */
    private static int hexToInt(byte[] b, int off, int len) {
        int val = 0;

        for (int i = len; --i >= 0; ) {
            val = (val << 4) + Character.digit((char)(b[off++] & 0xff), 16);
        }

        return val;
    }

    /**
     * Converts a decmal stream of bytes into an integer.  The digits are
     * expected to be in MSB (big endian) order.
     * <p>
     * This does not do any special checking on the length or the validity of
     * the digits.</p>
     *
     * @param b the decimal digits
     * @param off offset into the array
     * @param len decimal digit count
     * @return the decimal digits as an integer.
     */
    private static int decToInt(byte[] b, int off, int len) {
        int val = 0;

        for (int i = len; --i >= 0; ) {
            val = val*10 + Character.digit((char)(b[off++] & 0xff), 10);
        }

        return val;
    }

    /**
     * A message was received from the robot.
     * <p>
     * bboyes 20080327 OK, but we also want to know:</p>
     * <ol>
     * <li>When there's a new message, since we might be expecting/waiting for one,
     * such as a query response</li>
     * <li>We want the message copied to a working buffer, not as a String, so
     * we can do further parsing etc on it.</li>
     * </ol>
     * <p>
     * The com.systronix.trackbot.Events class is where Shawn does this sort of thing but
     * this class also is an alternative to TestApp, so it's not simply a case where I can
     * include the Events class and use its methods here in TestApp.</p>
     * <p>
     * In particular, {@link com.systronix.trackbot.Events#messageReceived(byte[], int, int)}
     * does exactly the sort of parsing I want to do here.</p>
     *
     * @param b the message data
     * @param off offset where the message starts
     * @param len message length, excluding the final CR
     */
    public void messageReceived(byte[] b, int off, int len) {
        Debug.info("MESSAGE RCV: " + new String(b, off, len));

        if (len < 2) return;
        int cmd = b[off++];
        int state;

        if ('?' == cmd) {
        	switch (b[off++]) {
                case 'S':
                    // Sensor status message
                    if (len != 6) return;

                    // Convert the hex value into an integer
                    state = hexToInt(b, off, 4);

                    // Check for an invalid value
                    if (state < 0) {
                        sensorNodeState = -1;
                    } else {
                        sensorNodeState = state;
                    }

                    break;

	            case 'P':
	                // Power status message (even though it returns sensor data)
	                if (len != 6) return;

	                // Convert the hex value into an integer
	                state = hexToInt(b, off, 4);

	                // Check for an invalid value
	                if (state < 0) {
	                    powerNodeState = -1;
	                } else {
	                    powerNodeState = state;

	                }

	                break;

                case 'V':
                    // Version message of the form ?VH02.22F00.07
                    if (len != 14) return;
                    // TODO (Shawn) Better version value checking needs to be done
                    if ((b[2] != 'H') || (b[8] != 'F'))
                    {
                    	// not the version response?
                    }
                    // offsets 5 & 11 should be periods

                    // parse into hardware major and minor and firmware major and minor, 2 digits each, base 10
                    int versionHardMajor = decToInt(b, off, 2);

                    break;
        	}


        }
        else if ('!' == cmd) {
        	switch (b[off++]) {
	            case 'C':
	                // Cortex message

	                if (len < 3) return;

	                switch (b[off++]) {
	                    case 'M':  // Tagging memory
	                    break;
	                }

                break;
        	} //Switch '!' responses
        }
    }

    /**
     * An EOF was received on the input thread.
     */
    public void inputEOF() {
        Debug.warning("ERROR: Input EOF!");
    }

    /**
     * Input error.  The robot I/O has been stopped.
     */
    public void inputError(IOException ex) {
        Debug.severe("ERROR: Input error (I/O stopped): " + ex);
    }

    /**
     * Output error.  The robot I/O has been stopped.
     */
    public void outputError(IOException ex) {
        Debug.severe("ERROR: Output error (I/O stopped): " + ex);
    }

    /**
     * A timeout or communications resumption has occurred.
     */
    public void timeoutStatus(boolean state, int ms) {
        Debug.warning(state ? "TIMEOUT!" : "RESUMED communication");
    }
}
