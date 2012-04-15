/*
 * Date: 2008 Mar 21
 * Time: 21:53 UTC
 *
 * (c) 2008 Systronix Inc.  All Rights reserved.
 * 939 Edison Street, Salt Lake City, UT, USA  84111
 * http://www.systronix.com/
 */
package com.systronix.trackbot.test;

import com.systronix.trackbot.Robot;
import com.systronix.trackbot.RobotIO;
import com.systronix.trackbot.VersionInfo;
import com.systronix.io.Debug;

/**
 * A test application that stresses the host serial interface to
 *
 * @author bboyes
 * @version 0.1
 */
public class HostStressTester extends Robot {
    /**
     * The main entry point.
     *
     * @param args the program arguments
     * @throws Exception if there was an error while attempting to connect to
     *         the robot.
     * @throws Error if the no connection could be made to the robot.
     */
    public static void main(String[] args) throws Exception {
        Debug.info("HostStressTester application starting.");

        // Start this tester
        // a lot of stuff happens as a result
        // version gets queried
        HostStressTester tester = new HostStressTester();

        // This thread now terminates
        for (;;)
        {
        	Debug.info("Acks = " + tester.getRobotIO().getAckCount());
        	Debug.info("Naks = " + tester.getRobotIO().getNakCount());
        	Debug.info("Inp Overflow = " + tester.getRobotIO().getInputOverflowCount());

        	Thread.sleep(2000);
        }
    }

    private boolean sensorsConfigured;
    private VersionInfo version;

    private int powerNodeState = -1;
    private int sensorNodeState = -1;

    /**
     * Creates the RobotMonitor.
     *
     * @throws Exception if there was an error while trying to connect to the
     *         robot.
     */
    public HostStressTester() throws Exception {
        super();
    }

    /**
     * Creates the HostStressTester using the given I/O connection.
     * Used in Greenfoot simulator and elsewhere
     *
     * @param robotIO the robot I/O connection
     */
    public HostStressTester(RobotIO robotIO) {
        super(robotIO);
    }

    /**
     * Converts a bit to a representative character.
     *
     * @param state the state value
     * @param bit the bit to measure
     * @return a character representing the specified bit.
     */
    private static char toChar(int state, int bit) {
        if ((state & (1 << bit)) == 0) {
            return '-';
        } else {
            return '*';
        }
    }

    public void allStates(int powerNodeState, int sensorNodeState) {
        powerNodeState(powerNodeState);
        sensorNodeState(sensorNodeState);
    }

    public void powerNodeState(int state) {
        // Ensure the sensors are configured

        if (version != null) {
            configureSensors(version);
        }

        // Only monitor changes

        if (state == powerNodeState) return;
        int oldState = powerNodeState;
        powerNodeState = state;

        // Display only changed information

        StringBuffer buf = new StringBuffer();

        // Corner sensors and gain

        if ((oldState & 0xf000) != (state & 0xf000)) {
            buf.append("Fwd P/S: ")
                    .append(toChar(state, 15))  // Port
                    .append('/')
                    .append(toChar(state, 14))  // Starboard
                    .append(" Aft P/S: ")
                    .append(toChar(state, 13))  // Port
                    .append('/')
                    .append(toChar(state, 12))  // Starboard
                    .append(" Gain: ")
                    .append((state >> 8) & 0x03);
        } else {
            buf.append("Gain: ")
                    .append((state >> 8) & 0x03);
        }

        Debug.info(buf.toString());
    }

    public void sensorNodeState(int state) {
        // Ensure the sensors are configured

        if (version != null) {
            configureSensors(version);
        }

        // Only monitor changes

        if (state == sensorNodeState) return;
        int oldState = sensorNodeState;
        sensorNodeState = state;

        // Display only changed information

        StringBuffer buf = new StringBuffer();

        // Only display the cliff reading if there is something
        // Nah... display all changes

        if ((oldState & 0xf000) != (state & 0xf000)) {
            // Invert the cliff values

            buf.append("CLIFF: Fwd P/S: ")
                    .append(toChar(~state, 15))  // Port
                    .append('/')
                    .append(toChar(~state, 14))  // Starboard
                    .append(" Aft P/S: ")
                    .append(toChar(~state, 13))  // Port
                    .append('/')
                    .append(toChar(~state, 12));  // Starboard

            Debug.info(buf.toString());

            buf.setLength(0);
        }

        // Side sensors and ambient light

        if ((oldState & 0x0f00) != (state & 0x0f00)) {
            buf.append("P/S fwd: ")
                    .append(toChar(state, 11))  // Port
                    .append('/')
                    .append(toChar(state, 10))  // Starboard
                    .append(" P/S aft: ")
                    .append(toChar(state, 9))  // Port
                    .append('/')
                    .append(toChar(state, 8))  // Starboard
                    .append(" Ambient: ")
                    .append(state & 0xff);
        } else {
            buf.append("Ambient: ")
                    .append(state & 0xff);
        }

        Debug.info(buf.toString());
    }

    public void testPointValue(int testPoint, boolean state) {
        Debug.info("Test point " + testPoint + ": " + (state ? "HIGH" : "LOW"));
    }

    public void transducerStation(byte site, int left, int right, boolean pir) {
    }

    public void taggingMemory(byte[] data) {
    }

    public void robotSerialNumber(String serialNo) {
        Debug.info("Robot serial no. = " + serialNo);
    }

    /**
     * Ensures the sensors are enabled and properly configured.
     */
    private void configureSensors(VersionInfo version) {
        // Only configure the sensors once

        if (sensorsConfigured) return;

        if (version.isIREnableSupported()) {
            // Turn on the sensors

            if (!enableCornerSensors(true, true, true, true)) return;
            if (!enableSideSensors(true, true, true, true)) return;
            if (!enableCliffSensors(true, true, true, true)) return;
        }

        if (version.isRangingSupported()) {
            // Set all the sensors to short range

            if (!setCornerSensorRange(1)) return;
            if (!setSideSensorRange(1)) return;
            if (!setCliffSensorRange(1)) return;
        }

        sensorsConfigured = true;
    }

    public void robotVersion(VersionInfo version, boolean supported) {
        Debug.info("Robot version = " + version);
        if (!supported) {
            Debug.severe("Robot version is UNSUPPPORTED!");
            return;
        }

        // Query the serial number

        if (version.isSerialNumberSupported()) {
            sendSerialNumberQuery();
        }

        // Set up the sensors, if we have to

        configureSensors(version);
        this.version = version;
    }

    int timeoutErrors = 0;
    int timeoutResumes = 0;

    public void robotTimeout(boolean state, int ms) {
        if (state) {
            // Force sensor reconfiguration

            sensorsConfigured = false;
            timeoutErrors++;
            Debug.info("TIMEOUT after " + ms + "ms!");

        } else {
        	timeoutResumes++;
            Debug.info("RESUMED communication.");
            sendVersionQuery();
        }
    }

    /**
     * Returns the name of this application.
     *
     * @return the name of this application.
     */
    public String toString() {
        return getClass().getName();
    }
}
