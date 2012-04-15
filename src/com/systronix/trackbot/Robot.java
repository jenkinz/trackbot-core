/*
 * Date: Nov 18, 2007
 * Time: 12:49:16 AM
 *
 * (c) 2007 Systronix Inc.  All Rights reserved.
 * 939 Edison Street, Salt Lake City, UT, USA  84111
 * http://www.systronix.com/
 */
package com.systronix.trackbot;

import com.systronix.io.Debug;

/**
 * A general interface to the TrackBot.
 * <p>
 * This borders on being a convenience class because it wraps the details of
 * the TrackBot communications protocol with a higher level interface.  It is
 * not necessary to use this class to implement robot behaviour.  In fact,
 * this can be used as an example of more advanced usage of the robot.</p>
 * <p>
 * Having stated that, subclasses can use this to do most of the heavy robot
 * protocol lifting.  Simply extend this class and implement the
 * {@link Events.Listener} methods.  Most modern IDE's should be able to do
 * this for you automatically because <code>Robot</code> is an abstract class.
 * </p>
 *
 * @author Shawn Silverman
 * @version 0.3
 */
public abstract class Robot implements Events.Listener {
    /** The I/O connection to the robot. */
    protected RobotIO robotIO;

    /** The events object. */
    protected Events events;

    /** The motors object. */
    protected Motors motors;

    private int sensorNodeEnableState;
    private int sensorNodeRangeState = 0x33;  // Side and cliff sensors as long range

    private byte[] msg = new byte[16];  // TODO This max. count may change in the future

    /*
     * The robot version string, may be <code>null</code> if the version
     * string has not yet been received from the robot.
     *
    protected String version;*/

    /**
     * Creates a new robot object.  This first tries to make an I/O
     * connection to the robot.  Next, the event queue is prepared and
     * started.  Finally, the version string is requested.
     * <p>
     * The application will eventually receive the version string in the
     * {@link #robotVersion(VersionInfo, boolean)} callback method.</p>
     * <p>
     * The sensors will be polled according to the value in the
     * {@linkplain Events#PROP_SENSOR_POLL_INTERVAL sensor poll interval}
     * system property.  If this is set to an invalid integer, then a default
     * of 200 ms is used.</p>
     *
     * @throws Exception if there was an error while trying to connect to the
     *         robot.
     * @throws Error if the no connection could be made to the robot.
     */
    protected Robot() throws Exception {
        // Connect to the robot

        this(RobotIOFactory.createFactory().createRobotIO());
    }

    /**
     * Creates a new robot object using the given I/O connection.
     *
     * @param robotIO the robot I/O connection
     * @see #Robot()
     */
    protected Robot(RobotIO robotIO) {
        if (robotIO == null) {
            Debug.severe("Could not connect to robot!");
            throw new Error("Could not connect to robot");
        }
        this.robotIO = robotIO;

        // Initialize the sensors
        // Poll them every 200ms by default

        int interval;
        try {
            interval = Integer.parseInt(System.getProperty(Events.PROP_SENSOR_POLL_INTERVAL));
        } catch (NumberFormatException ex) {
            interval = Events.DEFAULT_SENSOR_POLL_INTERVAL;
        }

        // for current TrackBot runtime, poll sensors and simulate events from them
        // in future (Apr/May 2008), TrackBot will generate its own events
        events = new Events(robotIO, interval);

        // Add the event listener

        events.addListener(this);

        // Initialize the motors

        motors = new Motors(robotIO);
        motors.brake(Motors.MOTOR_ALL);

        // Query the version string

        sendVersionQuery();
    }

    /**
     * Provides access to the internal {@link Events} object.
     *
     * @return the internal {@link Events} object.
     */
    public Events getEvents() {
        return events;
    }

    /**
     * Provides access to the internal {@link Motors} object.
     *
     * @return the internal {@link Motors} object.
     */
    public Motors getMotors() {
        return motors;
    }

    /**
     * Gets the internal {@link RobotIO} object.
     *
     * @return the internal {@link RobotIO} object.
     */
    public RobotIO getRobotIO() {
        return robotIO;
    }

    // Robot Info

    /**
     * Sends a version query to the robot.  The response will appear some time
     * in the future in the {@link #robotVersion(VersionInfo, boolean)}
     * callback.
     *
     * @return whether the message was successfully queued.
     */
    /* @return whether there was enough space in the output buffer to
     *         accomodate the message, or <code>false</code> if the I/O
     *         subsystem is not running.*/
    public final synchronized boolean sendVersionQuery() {
        msg[0] = '?';
        msg[1] = 'V';
        msg[2] = '\r';
        return robotIO.queueMessage(msg, 0, 3);
    }

    /**
     * Sends a serial number query to the robot.  The response will appear
     * some time in the future in the {@link #robotSerialNumber(String)}
     * callback.
     *
     * @return whether the message was successfully queued.
     * @see VersionInfo#isSerialNumberSupported()
     * @since FW 00.05
     */
    public final synchronized boolean sendSerialNumberQuery() {
        msg[0] = '?';
        msg[1] = 'C';
        msg[2] = 'S';
        msg[3] = '\r';
        return robotIO.queueMessage(msg, 0, 4);
    }

    // Other Hardware Support

    /**
     * Checks for a valid test point.
     */
    private static void checkTestPoint(int testPoint) {
        if (testPoint != 1 && testPoint != 2
              && !(9 <= testPoint && testPoint <= 20)) {
            throw new IllegalArgumentException("Invalid test point: " + testPoint);
        }
    }

    /**
     * Sends a query to read the value of the specified test point.  The test
     * point can be 1, 2, or in the range 9&ndash;20.
     *
     * @param testPoint the test point to read
     * @return whether the message was successfully queued.
     * @throws IllegalArgumentException if the test point is out of range.
     * @see VersionInfo#isTestPointsSupported()
     * @since FW 00.04
     */
    public final boolean sendTestPointQuery(int testPoint) {
        checkTestPoint(testPoint);

        synchronized (this) {
            msg[0] = '?';
            msg[1] = 'C';
            msg[2] = 'T';

            int len;
            if (testPoint < 10) {
                msg[3] = (byte)(testPoint + '0');
                msg[4] = '\r';

                len = 5;
            } else {
                msg[3] = (byte)(testPoint/10 + '0');
                msg[4] = (byte)(testPoint%10 + '0');
                msg[5] = '\r';

                len = 6;
            }

            return robotIO.queueMessage(msg, 0, len);
        }
    }

    /**
     * Sets a test point to the given state.  The test point can be 1, 2, or
     * in the range 9&ndash;20.
     * <p>
     * Note that setting a test point will cause the robot to reply with the
     * new value.</p>
     *
     * @param testPoint the test point to set
     * @param state 
     * @return whether the message was successfully queued.
     * @throws IllegalArgumentException if the test point is out of range.
     * @see VersionInfo#isTestPointsSupported()
     * @since FW 00.04
     */
    public final boolean setTestPoint(int testPoint, boolean state) {
        checkTestPoint(testPoint);

        synchronized (this) {
            msg[0] = '!';
            msg[1] = 'C';
            msg[2] = 'T';
            msg[3] = (byte)(state ? 'H' : 'L');

            int len;
            if (testPoint < 10) {
                msg[4] = (byte)(testPoint + '0');
                msg[5] = '\r';

                len = 6;
            } else {
                msg[4] = (byte)(testPoint/10 + '0');
                msg[5] = (byte)(testPoint%10 + '0');
                msg[6] = '\r';

                len = 7;
            }

            return robotIO.queueMessage(msg, 0, len);
        }
    }

    /**
     * Sends a transducer station query.  The place can be one of 'F', 'A',
     * 'P', or 'S'.
     *
     * @param site the site at which the transducer station is sitting
     * @return whether the message was successfully queued.
     * @throws IllegalArgumentException if the test point is out of range.
     */
    public final boolean sendTransducerStationQuery(int site) {
        switch (site) {
            case 'F':
            case 'A':
            case 'P':
            case 'S':
                break;
            default:
                throw new IllegalArgumentException("Bad transducer station place: 0x"
                        + Integer.toHexString(site & 0xff));
        }

        synchronized (this) {
            msg[0] = '?';
            msg[1] = 'T';
            msg[2] = (byte)site;
            msg[3] = '\r';

            return robotIO.queueMessage(msg, 0, 4);
        }
    }

    /**
     * Sends a query to read from the tagging memory.  The address can range
     * from zero to <code>0x1fff</code>.  The count indicates how many bytes
     * to read, and can range from 1 to 17.
     * <p>
     * Note that reads can cross a page boundary.</p>
     *
     * @param address the address from which to read
     * @param count 
     * @return whether the message was successfully queued.
     * @throws IllegalArgumentException if the address or count is out of
     *         range.
     * @see VersionInfo#isTaggingMemorySupported()
     * @since FW 00.05
     */
    public final boolean sendTaggingMemoryQuery(int address, int count) {
        if (address < 0 || 8191 < address) {
            throw new IllegalArgumentException("Address out of range: " + address);
        }
        if (count < 1 || 17 < count) {
            throw new IllegalArgumentException("Count out of range: " + count);
        }

        // Create the message

        synchronized (this) {
            msg[0] = '!';
            msg[1] = 'C';
            msg[2] = 'M';
            int index = 3 + setInt(address, msg, 3);
            msg[index++] = 'R';

            if (count > 1) {
                index += setInt(count, msg, index);
            }
            msg[index++] = '\r';

            return robotIO.queueMessage(msg, 0, index);
        }
    }

    /**
     * Writes to the tagging memory.  The address can range from zero to
     * <code>0x1fff</code>.
     * <p>
     * All writes occur within a 32-byte page.  The data will not cross a page
     * boundary, and instead wrap around to the start of the page.</p>
     * <p>
     * Note that an {@link IndexOutOfBoundsException} is thrown if the
     * message would overflow the RAN.</p>
     *
     * @param address write to this address
     * @param b 
     * @param off 
     * @param len 
     * @return whether the message was successfully queued.
     * @throws IllegalArgumentException if the address or data length is out
     *         of range.
     * @throws IndexOutOfBoundsException if the robot message is too large for
     *         the given address; the maximum length is given in the exception
     *         text.
     * @see VersionInfo#isTaggingMemorySupported()
     * @since FW 00.05
     */
    public final boolean writeTaggingMemory(int address,
                                            byte[] b, int off, int len) {
        if (address < 0 || 8191 < address) {
            throw new IllegalArgumentException("Address out of range: " + address);
        }
        if (len == 0) {
            return true;
        }
        if (len > 32 || len < 0) {
            throw new IllegalArgumentException("Length out of range: " + len);
        }

        // Check that the message would not overflow the RAN

        int msgLen = 5 + countAbsDigits(address) + len;
        if (msgLen > 16) {
            throw new IndexOutOfBoundsException("RAN message too large; max. length for this address is "
                    + (16 - (msgLen - len)));
        }

        // Create the message

        synchronized (this) {
            msg[0] = '!';
            msg[1] = 'C';
            msg[2] = 'M';
            int index = 3 + setInt(address, msg, 3);
            msg[index++] = 'W';

            System.arraycopy(b, off, msg, index, len);
            index += len;
            msg[index++] = '\r';

            return robotIO.queueMessage(msg, 0, index);
        }
    }

    // Beeper Support

    /**
     * Turns the beeper on for the specified number of milliseconds.  The time
     * can range from zero to 65535 ms.
     * <p>
     * According to the TrackBot documentation, to turn the buzzer off after
     * it has been turned on for a period, send <code>1</code> ms.</p>
     * <p>
     * The default is for the beeper to be off.</p>
     *
     * @param ms the time, in ms, to turn the beeper on
     * @return whether the message was successfully queued.
     * @throws IllegalArgumentException if the time is out of range.
     * @see VersionInfo#isBeeperSupported()
     * @since FW 00.04
     */
    public final boolean beepOnce(int ms) {
        if (ms < 0 || 65535 < ms) {
            throw new IllegalArgumentException("Beeper time out of range: " + ms);
        }

        synchronized (this) {
            msg[0] = '!';
            msg[1] = 'C';
            msg[2] = 'B';

            int index = 3 + setInt(ms, msg, 3);
            msg[index++] = '\r';

            return robotIO.queueMessage(msg, 0, index);
        }
    }

    /**
     * Turns the beeper on for the specified number of milliseconds, every
     * 250 ms (4 Hz).  This creates a recurring tone.  The time can range from
     * zero to 255 ms, but valid values are in the range zero to 249 ms.
     * <p>
     * A value of zero turns the alarm off.</p>
     * <p>
     * The default is for the alarm to be off.</p>
     *
     * @param ms the time, in ms, to turn the alarm on
     * @return whether the message was successfully queued.
     * @throws IllegalArgumentException if the time is out of range.
     * @see VersionInfo#isAlarmSupported()
     * @since FW 00.05
     */
    public final boolean beepAlarm(int ms) {
        if (ms < 0 || 250 <= ms) {
            throw new IllegalArgumentException("Alarm time out of range: " + ms);
        }

        synchronized (this) {
            msg[0] = '!';
            msg[1] = 'C';
            msg[2] = 'A';

            int index = 3 + setInt(ms, msg, 3);
            msg[index++] = '\r';

            return robotIO.queueMessage(msg, 0, index);
        }
    }

    /**
     * Stores the digits of the given integer in base 10 into the given byte
     * array.  Note that the array must be large enough to store the digits.
     * If the number is negative, the minus sign will not be stored.
     *
     * @param i store the digits of this integer
     * @param b store the digits into this array
     * @param off offset into the array
     * @return the digit count of the number's absolute value
     * @throws IndexOutOfBoundsException if the digits will not fit into the
     *         array.
     */
    private static int setInt(int i, byte[] b, int off) {
        if (i < 0) i = -i;
        int count = countAbsDigits(i);

        off += count;
        while (--count >= 0) {
            b[--off] = (byte)((i % 10) + '0');
            i /= 10;
        }

        return count;
    }

    /**
     * Counts the digits of the absolute value of the given integer.
     *
     * @param i count the digits of this integer
     * @return the digit count.
     */
    private static int countAbsDigits(int i) {
        if (i < 0) {
            i = -i;
        }

        int count = 1;
        while (i >= 10) {
            count++;
            i /= 10;
        }

        return count;
    }

    // Navigation Lights

    /**
     * Sets the blink (on) time for the navigation lights, in milliseconds.
     * The time can range from 0 to 255 ms.
     * <p>
     * The default is a blink time of 20 ms.</p>
     *
     * @param ms the new blink time, in ms
     * @return whether the message was successfully queued.
     * @throws IllegalArgumentException if the time is out of range.
     * @see VersionInfo#isNavLightsSupported()
     * @since FW 00.04
     */
    public final boolean setNavLightsBlinkTime(int ms) {
        if (ms < 0 || 255 < ms) {
            throw new IllegalArgumentException("Blink time out of range: " + ms);
        }

        synchronized (this) {
            msg[0] = '!';
            msg[1] = 'C';
            msg[2] = 'N';
            msg[3] = 'B';

            int index = 4 + setInt(ms, msg, 4);
            msg[index++] = '\r';

            return robotIO.queueMessage(msg, 0, index);
        }
    }

    /**
     * Sets the navigation lights repetition period, in milliseconds.  The
     * time can range from 0 to 65535 ms.
     * <p>
     * The default is a blink period of 1000 ms.</p>
     *
     * @param ms the new period, in ms
     * @return whether the message was successfully queued.
     * @throws IllegalArgumentException if the time is out of range.
     * @see VersionInfo#isNavLightsSupported()
     * @since FW 00.04
     */
    public final boolean setNavLightsPeriod(int ms) {
        if (ms < 0 || 65535 < ms) {
            throw new IllegalArgumentException("Period out of range: " + ms);
        }

        synchronized (this) {
            msg[0] = '!';
            msg[1] = 'C';
            msg[2] = 'N';
            msg[3] = 'P';

            int index = 4 + setInt(ms, msg, 4);
            msg[index++] = '\r';

            return robotIO.queueMessage(msg, 0, index);
        }
    }

    /**
     * Sets the color of a specific navigation light.
     * <p>
     * FW 00.04 does not do color mixing, so if more than one color is
     * specified to be on, then the command will not be sent.
     * <p>
     * The defaults are: fore-port-red, fore-starboard-green, aft-port-blue,
     * and aft-starboard-blue.</p>
     *
     * @param aftNotFore indicates the aft or fore lights
     * @param starboardNotPort indicates starboard or port lights
     * @param red whether to turn the red component on
     * @param green whether to turn the green component on
     * @param blue whether to turn the blue component on
     * @return whether the message was successfully queued.
     * @see VersionInfo#isNavLightsSupported()
     * @since FW 00.04
     */
    public final boolean setNavLightsColor(boolean aftNotFore,
                                           boolean starboardNotPort,
                                           boolean red,
                                           boolean green,
                                           boolean blue) {
        boolean off;

        if (!red && !green && !blue) {
            off = true;
        } else {
            int colorCount = 0;
            off = false;

            if (red) {
                colorCount++;
            }
            if (green) {
                colorCount++;
            }
            if (blue) {
                colorCount++;
            }

            // Special case FW 00.04

            VersionInfo ver = events.getVersionInfo();
            if (ver != null && ver.getFirmwareVersion() == 4) {
                if (colorCount > 1) {
                    return true;
                }
            }
        }

        // Create the message

        synchronized (this) {
            msg[0] = '!';
            msg[1] = 'C';
            msg[2] = 'N';
            msg[3] = (byte)(aftNotFore ? 'A' : 'F');
            msg[4] = (byte)(starboardNotPort ? 'S' : 'P');

            int index = 5;
            if (off) {
                msg[index++] = 'O';
            } else {
                if (red) {
                    msg[index++] = 'R';
                }
                if (green) {
                    msg[index++] = 'G';
                }
                if (blue) {
                    msg[index++] = 'B';
                }
            }
            msg[index++] = '\r';

            return robotIO.queueMessage(msg, 0, index);
        }
    }

    // Sensor ping intervals

    /**
     * Sets the corner sensor ping interval, in milliseconds.  The interval
     * can range from zero to 65535 ms.
     * <p>
     * The default is 50 ms.</p>
     *
     * @param ms the new corner sensor ping interval, in ms
     * @return whether the message was successfully queued.
     * @throws IllegalArgumentException if the interval is out of range.
     * @see VersionInfo#isIRPingIntervalSupported()
     * @since FW 00.05
     */
    public final boolean setCornerSensorPingInterval(int ms) {
        if (ms < 0 || 65535 < ms) {
            throw new IllegalArgumentException("Interval out of range: " + ms);
        }

        synchronized (this) {
            msg[0] = '!';
            msg[1] = 'P';
            msg[2] = 'P';
            msg[3] = 'O';

            int index = 4 + setInt(ms, msg, 4);
            msg[index++] = '\r';

            return robotIO.queueMessage(msg, 0, index);
        }
    }

    /**
     * Sets the side and cliff sensor ping interval, in milliseconds.  The
     * interval can range from zero to 65535 ms.
     * <p>
     * The default is 50 ms.</p>
     *
     * @param ms the new side and cliff sensor ping interval, in ms
     * @return whether the message was successfully queued.
     * @throws IllegalArgumentException if the interval is out of range.
     * @see VersionInfo#isIRPingIntervalSupported()
     * @since FW 00.05
     */
    public final boolean setSideAndCliffSensorPingInterval(int ms) {
        if (ms < 0 || 65535 < ms) {
            throw new IllegalArgumentException("Interval out of range: " + ms);
        }

        synchronized (this) {
            msg[0] = '!';
            msg[1] = 'S';
            msg[2] = 'P';
            msg[3] = 'O';

            int index = 4 + setInt(ms, msg, 4);
            msg[index++] = '\r';

            return robotIO.queueMessage(msg, 0, index);
        }
    }

    // Sensor Range

    /**
     * Sets the range for the corner sensors.  The range value can range from
     * 1 to 3, where 1 is short range, 2 is medium range, and 3 is long range.
     * <p>
     * The default is a range of 3, or long range.</p>
     *
     * @param range the new corner sensor range, a value from 1 to 3
     * @return whether the message was successfully queued.
     * @throws IllegalArgumentException if the given range value is not in the
     *         range 1 to 3.
     * @see VersionInfo#isRangingSupported()
     * @since FW 00.04
     */
    public final boolean setCornerSensorRange(int range) {
        if (range < 1 || 3 < range) {
            throw new IllegalArgumentException("Range value out of range: " + range);
        }

        // Fix the FW 00.04 bug
        // Swap 1 & 2

        VersionInfo ver = events.getVersionInfo();
        if (ver != null && ver.getFirmwareVersion() == 4) {
            if (range == 2) {
                range = 1;
            }
            else if (range == 1) {
                range = 2;
            }
        }

        synchronized (this) {
            msg[0] = '!';
            msg[1] = 'P';
            msg[2] = 'P';
            msg[3] = 'R';
            msg[4] = (byte)('0' + range);
            msg[5] = '\r';

            return robotIO.queueMessage(msg, 0, 6);
        }
    }

    /**
     * Sets the range for the side sensors.  The range value can range from 1
     * to 3, where 1 is short range, 2 is medium range, and 3 is long range.
     * <p>
     * In FW 00.04, the cliff and side sensors are set to the same range.</p>
     * <p>
     * The default is a range of 3, or long range.</p>
     *
     * @param range the new side sensor range, a value from 1 to 3
     * @return whether the message was successfully queued.
     * @throws IllegalArgumentException if the given range value is not in the
     *         range 1 to 3.
     * @see VersionInfo#isRangingSupported()
     * @since FW 00.04
     */
    public final boolean setSideSensorRange(int range) {
        if (range < 1 || 3 < range) {
            throw new IllegalArgumentException("Range value out of range: " + range);
        }

        // Special case FW 00.04

        VersionInfo ver = events.getVersionInfo();
        if (ver != null && ver.getFirmwareVersion() == 4) {
            return setSensorNodeSensorRange(range, true);
        } else {
            sensorNodeRangeState = (range << 4) | (sensorNodeRangeState & 0x0f);
            return setSensorNodeSensorRange(sensorNodeRangeState, false);
        }
    }

    /**
     * Sets the range for the cliff sensors.  The range value can range from 1
     * to 3, where 1 is short range, 2 is medium range, and 3 is long range.
     * <p>
     * In FW 00.04, the cliff and side sensors are set to the same range.</p>
     * <p>
     * The default is a range of 3, or long range.</p>
     *
     * @param range the new cliff sensor range, a value from 1 to 3
     * @return whether the message was successfully queued.
     * @throws IllegalArgumentException if the given range value is not in the
     *         range 1 to 3.
     * @see VersionInfo#isRangingSupported()
     * @since FW 00.04
     */
    public final boolean setCliffSensorRange(int range) {
        if (range < 1 || 3 < range) {
            throw new IllegalArgumentException("Range value out of range: " + range);
        }

        // Special case FW 00.04

        VersionInfo ver = events.getVersionInfo();
        if (ver != null && ver.getFirmwareVersion() == 4) {
            return setSensorNodeSensorRange(range, true);
        } else {
            sensorNodeRangeState = (sensorNodeRangeState & 0xf0) | range;
            return setSensorNodeSensorRange(sensorNodeRangeState, false);
        }
    }

    /**
     * Sets the sensor node ranges.
     *
     * @param old indicates we should use a 1-digit value, for FW 00.04
     * @return whether the message was successfully queued.
     */
    private synchronized boolean setSensorNodeSensorRange(int range,
                                                          boolean old) {
        msg[0] = '!';
        msg[1] = 'S';
        msg[2] = 'P';
        msg[3] = 'R';

        int len;
        if (old) {
            msg[4] = (byte)('0' + range);
            msg[5] = '\r';

            len = 6;
        } else {
            msg[4] = (byte)('0' + (range / 10));
            msg[5] = (byte)('0' + (range % 10));
            msg[6] = '\r';

            len = 7;
        }

        return robotIO.queueMessage(msg, 0, len);
    }

    // Sensor Enable

    /**
     * Enables the corner sensors.
     * <p>
     * The default is for no corner sensors to be enabled.</p>
     *
     * @param aftStarboard the aft starboard sensor
     * @param aftPort the aft port sensor
     * @param foreStarboard the fore starboard sensor
     * @param forePort the aft starboard sensor
     * @return whether the message was successfully queued.
     * @see VersionInfo#isIREnableSupported()
     * @since FW 00.04
     */
    public final synchronized boolean enableCornerSensors(boolean aftStarboard,
                                                          boolean aftPort,
                                                          boolean foreStarboard,
                                                          boolean forePort)
    {
        msg[0] = '!';
        msg[1] = 'P';
        msg[2] = 'P';
        msg[3] = 'E';

        int val = (aftStarboard ? 16 : 0)
                  | (aftPort ? 32 : 0)
                  | (foreStarboard ? 64 : 0)
                  | (forePort ? 128 : 0);

        msg[4] = (byte)(val/100 + '0');
        msg[5] = (byte)((val / 10) % 10 + '0');
        msg[6] = (byte)(val%10 + '0');

        msg[7] = '\r';

        return robotIO.queueMessage(msg, 0, 8);
    }

    /**
     * Enables the side sensors.
     * <p>
     * The default is for no side sensors to be enabled.</p>
     *
     * @param starboardAft the starboard aft sensor
     * @param portAft the port aft sensor
     * @param starboardFore the starboard fore sensor
     * @param portFore the port fore sensor
     * @return whether the message was successfully queued.
     * @see VersionInfo#isIREnableSupported()
     * @since FW 00.04
     */
    public final boolean enableSideSensors(boolean starboardAft,
                                           boolean portAft,
                                           boolean starboardFore,
                                           boolean portFore)
    {
        int val = (starboardAft ? 16 : 0)
                  | (portAft ? 32 : 0)
                  | (starboardFore ? 64 : 0)
                  | (portFore ? 128 : 0);
        sensorNodeEnableState = val | (sensorNodeEnableState & 0x0f);

        return enableSensorNodeSensors();
    }

    /**
     * Enables the cliff sensors.
     * <p>
     * The default is for no cliff sensors to be enabled.</p>
     *
     * @param aftStarboard the aft starboard cliff sensor
     * @param aftPort the aft port cliff sensor
     * @param foreStarboard the fore starboard cliff sensor
     * @param forePort the aft starboard cliff sensor
     * @return whether the message was successfully queued.
     * @see VersionInfo#isIREnableSupported()
     * @since FW 00.04
     */
    public final boolean enableCliffSensors(boolean aftStarboard,
                                            boolean aftPort,
                                            boolean foreStarboard,
                                            boolean forePort)
    {
        int val = (aftStarboard ? 1 : 0)
                  | (aftPort ? 2 : 0)
                  | (foreStarboard ? 4 : 0)
                  | (forePort ? 8 : 0);
        sensorNodeEnableState = (sensorNodeEnableState & 0xf0) | val;

        return enableSensorNodeSensors();
    }

    /**
     * Enables the selected sensor node sensors.
     *
     * @return whether the message was successfully queued.
     */
    private synchronized boolean enableSensorNodeSensors() {
        msg[0] = '!';
        msg[1] = 'S';
        msg[2] = 'P';
        msg[3] = 'E';

        msg[4] = (byte)(sensorNodeEnableState/100 + '0');
        msg[5] = (byte)((sensorNodeEnableState / 10) % 10 + '0');
        msg[6] = (byte)(sensorNodeEnableState % 10 + '0');

        msg[7] = '\r';

        return robotIO.queueMessage(msg, 0, 8);
    }

    /**
     * Destroys any resources used by this object.  This also stops the events
     * and I/O threads.  Subclasses should call <code>super.destroy()</code>
     * if this method is overridden.
     * <p>
     * Note that use of any robot I/O after this method is called will not
     * work.</p>
     */
    public void destroy() {
        events.stopPolling();
        robotIO.stopIO();
    }

    /*
     * Events.Listener methods.
     */

    /*
     * Receives the robot version string.  This implementation sets the
     * internal {@link #version} field.  This also performs some version-
     * specific initialization.
     *
     * @param version the version string
     *
    public final void robotVersion(String version) {
        this.version = version;

        this.versionInfo.setVersion(version);

        if (versionInfo.isIREnableSupported()) {
            // Turn on the sensors

            enableCornerSensors(true, true, true, true);
            enableSideSensors(true, true, true, true);
        }

        if (versionInfo.isRangingSupported()) {
            // Set all the sensors to short range

            setCornerSensorRange(3);
            setSideSensorRange(3);
        }
    }*/

    /**
     * Returns a string representation of this object.
     *
     * @return a string representation of this object.
     */
    public String toString() {
        return "Robot";
    }
}
