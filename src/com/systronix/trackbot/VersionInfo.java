/*
 * Date: Jan 20, 2008
 * Time: 12:32:28 AM
 *
 * (c) 2008 Systronix Inc.  All Rights reserved.
 * 939 Edison Street, Salt Lake City, UT, USA  84111
 * http://www.systronix.com/
 */
package com.systronix.trackbot;

/**
 * This class keeps track of the robot hardware and firmware versions and the
 * related capabilities.
 *
 * @author Shawn Silverman
 * @version 0.1
 */
public final class VersionInfo {
    private String version;

    /** The hardware revision, in the range 0000&ndash;9999. */
    private int hardwareVer = -1;

    /** The firmware revision, in the range 0000&ndash;9999. */
    private int firmwareVer = -1;

    /**
     * Creates a new version information object.  The hardware and firmware
     * versions will initially be set to zero.
     */
    VersionInfo() {
    }

    /**
     * Gets the version string.  This returns <code>null</code> if the version
     * string has not yet been set.
     *
     * @return the version string, or <code>null</code> if it has not been
     *         set.
     */
    public String getVersion() {
        return version;
    }

    /**
     * Gets the firmware version number, a value in the range 0 to 9999.  For
     * example, if the firmware version is 00.05, then this will return
     * <code>5</code>.
     *
     * @return the firmware version number, or <code>-1</code> if the version
     *         string has not been set.
     */
    public int getFirmwareVersion() {
        return firmwareVer;
    }

    /**
     * Gets the hardware version number, a value in the range 0 to 9999.  For
     * example, if the hardware version is 02.21, then this will return
     * <code>221</code>.
     *
     * @return the hardware version number, or <code>-1</code> if the version
     *         string has not been set.
     */
    public int getHardwareVersion() {
        return hardwareVer;
    }

    /**
     * Checks if beeper control is supported on this version.
     */
    public boolean isBeeperSupported() {
        return hardwareVer >= 221 && firmwareVer >= 4;
    }

    /**
     * Checks if beeper alarm control is supported on this version.
     */
    public boolean isAlarmSupported() {
        return hardwareVer >= 221 && firmwareVer >= 5;
    }

    /**
     * Checks if navigation light control is supported on this version.
     */
    public boolean isNavLightsSupported() {
        return hardwareVer >= 221 && firmwareVer >= 4;
    }

    /**
     * Checks if test points are supported on this version.
     */
    public boolean isTestPointsSupported() {
        return hardwareVer >= 221 && firmwareVer >= 4;
    }

    /**
     * Checks if tagging memory access is supported.
     */
    public boolean isTaggingMemorySupported() {
        return hardwareVer >= 221 && firmwareVer >= 5;
    }

    /**
     * Checks if the robot serial number is available.
     */
    public boolean isSerialNumberSupported() {
        return hardwareVer >= 221 && firmwareVer >= 5;
    }

    /**
     * Checks if ranging is supported on this version.
     */
    public boolean isRangingSupported() {
        return hardwareVer >= 221 && firmwareVer >= 4;
    }

    /**
     * Checks if the ability to enable or disable the IR sensors is supported
     * on this version.  Having this feature available implies that the
     * various IR sensors must be enabled before the robot will report
     * detection.
     */
    public boolean isIREnableSupported() {
        return hardwareVer >= 221 && firmwareVer >= 4;
    }

    /**
     * Checks if setting the IR sensor ping interval is supported.
     */
    public boolean isIRPingIntervalSupported() {
        return hardwareVer >= 221 && firmwareVer >= 5;
    }

    /**
     * Checks if abrupt speed change in the motors is supported.  An abrupt
     * speed change avoids the smooth trapezoidal ramp.
     */
    public boolean isMotorJumpBellSupported() {
        return hardwareVer >= 221 && firmwareVer >= 5;
    }

    // Events listener methods

    /**
     * Parses the robot version string.
     *
     * @param version the robot version string
     */
    synchronized void setVersion(String version) {
        this.version = version;

        // Check the basic format

        if (version != null && version.length() == 12) {
            // Check the format

            if (version.charAt(0) == 'H'
                  && version.charAt(3) == '.'
                  && version.charAt(6) == 'F'
                  && version.charAt(9) == '.') {
                // Get the firmware information

                try {
                    hardwareVer = Integer.parseInt(version.substring(1, 3).concat(version.substring(4, 6)));
                    firmwareVer = Integer.parseInt(version.substring(7, 9).concat(version.substring(10, 12)));
                } catch (NumberFormatException ex) {
                    hardwareVer = 0;
                    firmwareVer = 0;
                }
            }
        }
    }

    /**
     * Returns the version string, or <code>null</code> if it has not yet been
     * set.
     *
     * @return the version string, or <code>null</code> if it has not been
     *         set.
     */
    public String toString() {
        return version;
    }
}
