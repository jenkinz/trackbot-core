/*
 * Date: Oct 21, 2007
 * Time: 12:42:08 PM
 *
 * (c) 2007 Systronix Inc.  All Rights reserved.
 * 939 Edison Street, Salt Lake City, UT, USA  84111
 * http://www.systronix.com/
 */
package com.systronix.trackbot;

import java.io.IOException;
import java.util.Vector;

import com.qindesign.util.PseudoThread;
import com.qindesign.util.logging.Level;
import com.systronix.io.Debug;

/**
 * Dispatches TrackBot events to interested parties.  Events can include
 * sensor information, and also general state of the robot.  In other words,
 * this class deals with any information originating from the robot, whether
 * requested or not.
 * <p>
 * This class also provides ways to get the current value of the node states,
 * without having to rely on dispatched callback implementations.  In fact,
 * there are a total of three ways to get events:</p>
 * <ol>
 * <li>Get the latest snapshot of an event by calling one of the
 *     <code>get<em>XXX</em>State()</code> methods.</li>
 * <li>Use a {@link Listener} implementation to provide callback functions.</li>
 * <li>Wait for an event to be received by calling one of the
 *     <code>waitFor<em>XXX</em>Event()</code> methods.</li>
 * </ol>
 * <p>
 * This class also adjusts for whether cliff sensors are attached or not.  If
 * it is determined that no cliff sensors are attached, then the corresponding
 * event bits are set to '1'.  The problem is that there is no way to tell the
 * difference between cliff sensors not being present and all the cliff
 * sensors seeing a cliff.  If a cliff is detected, the corresponding bits
 * will be set to '0', and this is what will happen with cliff sensors being
 * absent.</p>
 * <p>
 * To determine whether cliff sensors are present, the first sensor node
 * reading is examined.  If all the cliff sensor bits are set to '0', then it
 * is assumed that there are no cliff sensors attached.  All future sensor
 * node readings are then modified to detect the floor.  If, at some point in
 * the future, any cliff readings contain '1', then cliff sensors must be
 * attached, and all future readings are not modified.</p>
 *
 * @author Shawn Silverman
 * @version 0.6
 */
public class Events implements RobotIO.Listener {
    /** This system property can specify the poll interval for the sensors. */
    public static final String PROP_SENSOR_POLL_INTERVAL = "com.systronix.trackbot.Events.pollInterval";

    /** The default sensor poll interval. */
    public static final int DEFAULT_SENSOR_POLL_INTERVAL = 100;

    /**
     * A polling thread that polls the sensors.
     *
     * @author Shawn Silverman
     */
    private static final class SensorPoller extends PseudoThread {
        private static final byte[] MSG_P = { '?', 'P', '\r' };
        private static final byte[] MSG_S = { '?', 'S', '\r' };

        private RobotIO robotIO;

        /**
         * Create a new sensor poller using the given communications object
         * and poll interval.
         *
         * @param robotIO
         * @param interval the poll interval
         */
        SensorPoller(RobotIO robotIO, long interval) {
            super(interval);
            this.robotIO = robotIO;
        }

        /**
         * Polls the sensors.
         */
        public void doWork() {
            robotIO.queueMessage(MSG_P, 0, 3);
            robotIO.queueMessage(MSG_S, 0, 3);
        }
    }
//TESTING
int prev[] = new int[10];

    /**
     * Listens to events.
     *
     * @author Shawn Silverman
     */
    public interface Listener {
        /**
         * Power node state was received.
         *
         * @param state the state value
         */
        public void powerNodeState(int state);

        /**
         * Sensor node state was received.
         * <p>
         * Note for FW 00.05: Bits 8&ndash;11 are swapped with bits
         * 12&ndash;15.  Other firmware versions, however, do not have this
         * issue.  Specifically, bits 8&dash;11 are the side sensors and bits
         * 12&ndash;15 are the cliff sensors in firmware versions other than
         * 00.05.</p>
         *
         * @param state the state value
         */
        public void sensorNodeState(int state);

        /**
         * A reading from all the nodes was received.
         * <p>
         * This is used in Greenfoot.</p>
         *
         * @param powerNodeState the power node state
         * @param sensorNodeState the sensor node state
         */
        public void allStates(int powerNodeState, int sensorNodeState, int[][] beaconState, int trackBotID);

        /**
         * A test point reading was received.
         *
         * @param testPoint the test point number
         * @param state the state of the test point, <code>true</code> for
         *              high, and <code>false</code> for low
         * @since FW 00.04
         */
        public void testPointValue(int testPoint, boolean state);

        /**
         * A transducer station reading was received.  The place value can be
         * one of 'F', 'A', 'P', or 'S', meaning "fore", "aft", "port", and
         * "starboard".  The left and right values range from zero to 15, and
         * the <code>pir</code> value will be <code>true</code> if the PIR
         * sensor detected a warm body, and <code>false</code> otherwise
         *
         * @param site the site at which the transducer station is sitting
         * @param left the left sonar value, zero to 15
         * @param right the right sonar value, zero to 15
         * @param pir indicates whether the PIR sensor detected a warm body
         */
        public void transducerStation(byte site,
                                      int left, int right, boolean pir);

        /**
         * Tagging memory data was received.
         *
         * @param data the received data
         * @since FW 00.05
         */
        public void taggingMemory(byte[] data);

        /**
         * The robot version was received.  If this API does not support the
         * given version, then <code>supported</code> will be set to
         * <code>false</code>.  In an unsupported case, received sensor values
         * or robot commands may not work as expected.  FW 00.05 is one such
         * case.
         *
         * @param version the robot version information
         * @param supported indicates whether this API supports the robot
         */
        public void robotVersion(VersionInfo version, boolean supported);

        /**
         * The robot serial number was received.  This is new in FW 00.05.
         *
         * @param serialNo an 8-character serial number
         * @since FW 00.05
         */
        public void robotSerialNumber(String serialNo);

        /**
         * Indicates that a timeout occurred when communicating with the
         * robot.  This also indicates that the timeout condition has ceased
         * if the robot has resumed communication.
         *
         * @param state <code>true</code> if a timeout occurred, and
         *              <code>false</code> if the robot has resumed
         *              communication
         * @param ms the timeout used to determine this condition, in ms
         */
        public void robotTimeout(boolean state, int ms);

        /*
         * Indicates that the robot version returned by the TrackBot is
         * unsupported by this API.
         *
         * @param version the version information
         *
        public void robotVersionNotSupported(VersionInfo version);*/
    }

    private SensorPoller sensorPoller;

    // Event listeners and lock objects

    private Vector listeners = new Vector();
    private Object powerNodeEventLock  = new Object();
    private Object sensorNodeEventLock = new Object();

    // Current states

    private int powerNodeState = -1;
    private int sensorNodeState = -1;

    private VersionInfo versionInfo = new VersionInfo();

    // Cliff sensor processing

    /** Start off by assuming there are no cliff sensors. */
    private int cliffSensorMask = 0xf000;

    /**
     * Creates a new <code>Events</code> object and starts the sensor
     * polling thread.  This sets itself as the listener for the
     * communications object.
     * <p>
     * A negative poll interval indicates that the sensors are not polled.</p>
     *
     * @param robotIO the TrackBot communications object
     * @param pollInterval the sensor poll interval
     */
    public Events(RobotIO robotIO, int pollInterval) {
        robotIO.setListener(this);

        // Start the sensor polling thread

        if (pollInterval >= 0) {
            sensorPoller = new SensorPoller(robotIO, pollInterval);
            sensorPoller.start();
        }
    }

    /**
     * Stops the sensor polling thread.  When this method exits, the thread
     * will be stopped.  This does nothing if no sensor polling thread was
     * created.
     *
     * @see #Events(RobotIO, int)
     */
    public void stopPolling() {
        if (sensorPoller != null) {
            sensorPoller.stop();

            // Wait for the thread to actually terminate
            // because PseudoThread.stop()'s semantics don't necessarily stop
            // the thread right away

            try {
                sensorPoller.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /*
     * Events.
     */

    // TODO Improve the efficiency by not dispatching everything all the time to all the listeners.
    // TODO There should be a separate listener for each event

    /**
     * Fires the all-avoidance-sensors event.
     *
     * @param powerNodeState the new power node state
     * @param sensorNodeState the new sensor node state
     */
    private void fireAllStates(int powerNodeState, int sensorNodeState, int[][] beaconState, int trackBotID) {
        sensorNodeState = adjustCliffSensors(sensorNodeState);

        for (int i = listeners.size(); --i >= 0; ) {
            ((Listener)listeners.elementAt(i)).allStates(
                    powerNodeState,
                    sensorNodeState,
                    beaconState,
                    trackBotID);
        }
    }

    /**
     * Fires the power node state event.
     *
     * @param state the new state
     */
    private void firePowerNodeState(int state) {
        // Notify any waiters

        synchronized (powerNodeEventLock) {
            powerNodeEventLock.notifyAll();
        }

        // Notify the listeners

        // NOTE Counting backwards is important for asynchronous adding of listeners
        for (int i = listeners.size(); --i >= 0; ) {
            ((Listener)listeners.elementAt(i)).powerNodeState(state);
        }
    }

    /***
     * Adjusts the sensor node state for the cliff sensors readings.
     */
    private int adjustCliffSensors(int state) {
        // Guess if there're cliff sensors
        // The heuristic is as follows:
        // 1) Start off assuming there are no cliff sensors.
        // 2) Assume that the cliff sensors that indicate 'no reading' do not
        //    exist.
        // 3) If, at some point in the future, any of the cliff sensors detect
        //    something, then stop setting them automatically to '1'.

        if (cliffSensorMask != 0) {
            // If any of the cliff sensors detected the floor, then
            // ensure those bits in the mask are set to zero

            int m = cliffSensorMask;
            cliffSensorMask &= ~state & 0xf000;

            // Log which bits have changed

            if (Debug.isLoggable(Level.FINE)) {
                // See which bits have changed

                m ^= cliffSensorMask;

                if (m != 0) {
                    StringBuffer buf = new StringBuffer();
                    buf.append("Events: Detected cliff sensor");
                    switch (m) {
                        case 0x8000: case 0x4000: case 0x2000: case 0x1000:
                            buf.append(':');
                            break;
                        default:
                            buf.append("s:");
                    }

                    boolean firstInList = true;
                    if ((m & 0x8000) != 0) {
                        buf.append(" fore port");
                        firstInList = false;
                    }
                    if ((m & 0x4000) != 0) {
                        if (!firstInList) buf.append(',');
                        else firstInList = false;
                        buf.append(" fore starboard");
                    }
                    if ((m & 0x2000) != 0) {
                        if (!firstInList) buf.append(',');
                        else firstInList = false;
                        buf.append(" aft port");
                    }
                    if ((m & 0x1000) != 0) {
                        if (!firstInList) buf.append(',');
                        buf.append(" aft starboard");
                    }

                    Debug.fine(buf.toString());
                }//There were cliff sensor changes
            }//Debug the detection of cliff sensors

            // Set the nonexistent cliff sensor bits to '1'

            state |= cliffSensorMask;
        }

        return state;
    }

    /**
     * Fires the sensor node state event.
     *
     * @param state the new state
     */
    private void fireSensorNodeState(int state) {
        state = adjustCliffSensors(state);

        // Notify any waiters

        synchronized (sensorNodeEventLock) {
            sensorNodeEventLock.notifyAll();
        }

        // Notify the listeners

        // NOTE Counting backwards is important for asynchronous adding of listeners
        for (int i = listeners.size(); --i >= 0; ) {
            ((Listener)listeners.elementAt(i)).sensorNodeState(state);
        }
    }

    /**
     * Fires a test point value to the listeners.
     *
     * @param testPoint the test point
     * @param state the test point state
     */
    private void fireTestPointValue(int testPoint, boolean state) {
        // Notify the listeners

        // NOTE Counting backwards is important for asynchronous adding of listeners
        for (int i = listeners.size(); --i >= 0; ) {
            ((Listener)listeners.elementAt(i)).testPointValue(testPoint, state);
        }
    }

    /**
     * Fires a transducer station event to the listeners.
     *
     * @param site the site at which the transducer station is sitting
     * @param left the left sonar value, zero to 15
     * @param right the right sonar value, zero to 15
     * @param pir indicates whether the PIR sensor detected a warm body
     */
    private void fireTransducerStation(byte site,
                                       int left, int right, boolean pir) {
        // Notify the listeners

        // NOTE Counting backwards is important for asynchronous adding of listeners
        for (int i = listeners.size(); --i >= 0; ) {
            ((Listener)listeners.elementAt(i)).transducerStation(site, left, right, pir);
        }
    }

    /**
     * Fires tagging memory data to the listeners.
     *
     * @param data the received data
     */
    private void fireTaggingMemory(byte[] data) {
        // Notify the listeners

        // NOTE Counting backwards is important for asynchronous adding of listeners
        for (int i = listeners.size(); --i >= 0; ) {
            ((Listener)listeners.elementAt(i)).taggingMemory(data);
        }
    }

    /**
     * Fires the robot version to the listeners.  This method also determines
     * if the supplied robot version is supported by this API.
     *
     * @param version the robot version string
     */
    private void fireVersion(String version) {
        versionInfo.setVersion(version);

        // Notify the listeners

        int fw = versionInfo.getFirmwareVersion();
        boolean supported = versionInfo.getHardwareVersion() >= 221;
        if (supported) {
            // Support all firmware versions up to 7, but not including 5

            if (fw == 5) {
                supported = false;
            }
        }

        // NOTE Counting backwards is important for asynchronous adding of listeners
        for (int i = listeners.size(); --i >= 0; ) {
            ((Listener)listeners.elementAt(i)).robotVersion(versionInfo, supported);
        }
    }

    /**
     * Fires the robot version to the listeners.  This is new in FW 00.05.
     *
     * @param serialNo the robot serial number
     */
    private void fireSerialNumber(String serialNo) {
        // Notify the listeners

        // NOTE Counting backwards is important for asynchronous adding of listeners
        for (int i = listeners.size(); --i >= 0; ) {
            ((Listener)listeners.elementAt(i)).robotSerialNumber(serialNo);
        }
    }

    /**
     * Indicates to the listeners that a timeout occurred.
     *
     * @param state <code>true</code> if a timeout occurred, and
     *              <code>false</code> if the robot has resumed
     *              communication
     * @param ms the timeout used to determine this condition, in ms
     */
    private void fireRobotTimeout(boolean state, int ms) {
        if (state) {
            // Reset the cliff sensor state

            cliffSensorMask = 0xf000;

            // Suspend the sensor poller

            if (sensorPoller != null) {
                sensorPoller.suspend();
            }
        } else {
            // Ensure the sensor poller is resumed

            if (sensorPoller != null) {
                sensorPoller.resume();
            }
        }

        // NOTE Counting backwards is important for asynchronous adding of listeners
        for (int i = listeners.size(); --i >= 0; ) {
            ((Listener)listeners.elementAt(i)).robotTimeout(state, ms);
        }
    }

    /**
     * Waits for a power node event.
     *
     * @return the value of the power node event.
     * @throws InterruptedException if the thread was interrupted while
     *         waiting.
     * @see #getPowerNodeState()
     */
    public int waitForPowerNodeEvent() throws InterruptedException {
        synchronized (powerNodeEventLock) {
            powerNodeEventLock.wait();

            return powerNodeState;
        }
    }

    /**
     * Waits for a sensor node event.
     *
     * @return the value of the sensor node event.
     * @throws InterruptedException if the thread was interrupted while
     *         waiting.
     * @see #getSensorNodeState()
     */
    public int waitForSensorNodeEvent() throws InterruptedException {
        synchronized (sensorNodeEventLock) {
            sensorNodeEventLock.wait();

            return sensorNodeState;
        }
    }

    // TODO The ability to remove listeners

    /**
     * Adds an event listener.  Note that currently, listeners added more than
     * once will receive events more than once.
     *
     * @param l the listener to add
     * @throws NullPointerException if the given listener is <code>null</code>.
     */
    public void addListener(Listener l) {
        if (l == null) throw new NullPointerException();

        listeners.addElement(l);
    }

    /*
     * Current states.
     */

    /**
     * Gets a snapshot of the current power node state.  This returns
     * <code>-1</code> if the state holds an invalid value.  This can occur if
     * the state has never been read, or if invalid information is received
     * from the TrackBot.
     *
     * @return the power node state, or <code>-1</code> for an invalid value.
     */
    public int getPowerNodeState() {
        return powerNodeState;
    }

    /**
     * Gets a snapshot of the current sensor node state.  This returns
     * <code>-1</code> if the state holds an invalid value.  This can occur if
     * the state has never been read, or if invalid information is received
     * from the TrackBot.
     *
     * @return the sensor node state, or <code>-1</code> for an invalid value.
     */
    public int getSensorNodeState() {
        return sensorNodeState;
    }

    /**
     * Gets the robot version info.  This may return <code>null</code> if the
     * version string has not yet been received or requested.
     *
     * @return the robot version info, or <code>null</code> if it has not yet
     *         been set.
     */
    public VersionInfo getVersionInfo() {
        if (versionInfo.getVersion() != null) {
            return versionInfo;
        } else {
            return null;
        }
    }

    /*
     * RobotIO.Listener methods.
     */

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
     * @return the hex digits as an integer.
     */
    private static int hexToInt(byte[] b, int off, int len) {
        int val = 0;

        for (int i = len; --i >= 0; ) {
            val = (val << 4) + Character.digit((char)(b[off++] & 0xff), 16);
        }

        return val;
    }

    /**
     * A message was received.
     */
    // TODO bboyes 20080330 - I wonder about breaking out the message parsing into its own class to make it more reusable?
    public void messageReceived(byte[] b, int off, int len) {
        // Parse the received message

        if (len < 2) return;
        int cmd = b[off++];

        if ('?' == cmd) {
            int state;

            switch (b[off++]) {
                case 'A':
                    // Special 'All sensors' message
                    // This was inspired by the need to send all the sensor data
                    // at once when simulating the robot

                    if (len != 523) return;
                    //Debug.finest("RCV: Msg Length: "+len);
                    
                    int pState = hexToInt(b, off, 4);
                    int sState = hexToInt(b, off + 4, 4);
                    int currTrackBotID = b[off + 8];
                    int[][] bState = new int[64][10];
                    //Debug.info("This TrackBotID: "+b[off+8]);
                    //Debug.info("Other TrackBotID: "+b[off+9]);
                    for (int i = 0, boff = 0; i < 64; i++, boff += 8) {
                        for (int j = 0; j < 8; j++) {
                            bState[i][j] = b[off + 9 + boff + j];
                        }
                    }

// TESTING
/*
if (currTrackBotID == 1) {
    if (Arrays.equals(prev, bState[0])) {
        Debug.fine("STILL EQUAL");
    }
    prev = bState[0];
}*/
        
                    
                    if (pState < 0) {
                        powerNodeState = -1;
                    } else {
                        powerNodeState = pState;
                    }
                    if (sState < 0) {
                        sensorNodeState = -1;
                    } else {
                        sensorNodeState = sState;
                    }

                    if (pState >= 0 && sState >= 0) {
                        fireAllStates(pState, sState, bState, currTrackBotID);
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
                        firePowerNodeState(state);
                    }

                    break;

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
                        fireSensorNodeState(state);
                    }

                    break;

                case 'V':
                    // Version message

                    if (len != 14) return;

                    fireVersion(new String(b, off, 12));  // TODO Watch out for obscure default encodings!

                    break;

                case 'T':  // Test point or transducer station
                    if (len == 6) {
                        int d1 = (b[off++] & 0xff) - '0';
                        int d2 = (b[off++] & 0xff) - '0';
                        int d3 = (b[off++] & 0xff) - '0';
                        if (d1 < 0 || 9 < d1 || d2 < 0 || 9 < d2
                              || d3 < 0 || 9 < d3) {
                            return;
                        }

                        int testPoint = d1*100 + d2*10 + d3;

                        // Test point state

                        switch (b[off]) {
                            case 'H':
                                fireTestPointValue(testPoint, true);
                                break;
                            case 'L':
                                fireTestPointValue(testPoint, false);
                                break;
                        }
                    } else if (len == 7) {
                        // Fore, Aft, Port, or Starboard

                        byte site = b[off++];
                        switch (site) {
                            case 'F':
                            case 'A':
                            case 'P':
                            case 'S':
                                break;
                            default:
                                return;
                        }

                        int right = Character.digit((char)b[off++], 16);
                        int left = Character.digit((char)b[off++], 16);
                        int pir = Character.digit((char)b[off++], 16);
                        if (Character.digit((char)b[off], 16) < 0
                              || right < 0 || left < 0 || pir < 0) {
                            return;
                        }

                        fireTransducerStation(site, left, right, (pir & 0x8) == 0);
                    }
                    break;

                case 'C':
                    // Cortex message

                    if (len < 3) return;

                    switch (b[off++]) {
                        case 'S':  // Serial number
                            if (len != 7) return;

                            fireSerialNumber(new String(b, off, 4));  // TODO Watch out for obscure default encodings!

                            break;
                    }

                    break;
            }//Switch '?' responses
        } else if ('!' == cmd) {
            switch (b[off++]) {
                case 'C':
                    // Cortex message

                    if (len < 3) return;

                    switch (b[off++]) {
                        case 'M':  // Tagging memory
                            byte[] data = new byte[len - 3];
                            System.arraycopy(b, off, data, 0, len - 3);
                            fireTaggingMemory(data);
                            break;
                    }

                    break;
            }//Switch '!' responses
        }
    }

    /**
     * A timeout status change has occurred.
     */
    public void timeoutStatus(boolean state, int ms) {
        fireRobotTimeout(state, ms);
    }

    /**
     * Does nothing.
     */
    public void inputEOF() {
    }

    /**
     * Does nothing.
     */
    public void inputError(IOException ex) {
    }

    /**
     * Does nothing.
     */
    public void outputError(IOException ex) {
    }

    /**
     * Returns a string representation of this object.
     *
     * @return a string representation of this object.
     */
    public String toString() {
        return "Robot events";
    }
}
