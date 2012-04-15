/*
 * Date: Nov 15, 2005
 * Time: 11:38:25 PM
 * (c) 2005 Shawn Silverman
 */
package com.qindesign.util.logging;

/**
 * Represents a logging level.
 *
 * @author Shawn Silverman
 * @version 1.0
 */
public class Level {
    /**
     * OFF turns off logging.
     * Its value is <code>Integer.MAX_VALUE</code>.
     */
    public static final int OFF = Integer.MAX_VALUE;

    /**
     * SEVERE indicates a serious failure.
     * Its value is <code>1000</code>.
     */
    public static final int SEVERE = 1000;

    /**
     * WARNING indicates a potential problem.
     * Its value is <code>900</code>.
     */
    public static final int WARNING = 900;

    /**
     * INFO is for informational messages.
     * Its value is <code>800</code>.
     */
    public static final int INFO = 800;

    /**
     * CONFIG is for static configuration messages.
     * Its value is <code>700</code>.
     */
    public static final int CONFIG = 700;

    /**
     * FINE provides tracing information.
     * Its value is <code>500</code>.
     */
    public static final int FINE = 500;

    /**
     * FINER indicates a fairly detailed tracing message.
     * Its value is <code>400</code>.
     */
    public static final int FINER = 400;

    /**
     * FINEST indicates a highly detailed tracing message.
     * Its value is <code>300</code>.
     */
    public static final int FINEST = 300;

    /**
     * ALL indicates that all messages should be logged.
     * Its value is <code>Integer.MIN_VALUE</code>.
     */
    public static final int ALL = Integer.MIN_VALUE;

    /** This constructor is private for now. */
    private Level() { }

    /**
     * Returns the level name corresponding to the given level value.
     *
     * @param level the level for which to find the name
     * @return the level name of the given level value.
     */
    public static String toString(int level) {
        switch (level) {
            case OFF    : return "OFF";
            case SEVERE : return "SEVERE";
            case WARNING: return "WARNING";
            case INFO   : return "INFO";
            case CONFIG : return "CONFIG";
            case FINE   : return "FINE";
            case FINER  : return "FINER";
            case FINEST : return "FINEST";
            case ALL    : return "ALL";
            default:
                return Integer.toString(level);
        }
    }

    /**
     * Parses a level name into a level value.  The name may either be an
     * actual name or an integer value.  The names currently recognized are:
     * { "OFF", "SEVERE", "WARNING", "INFO", "CONFIG", "FINE", "FINER",
     * "FINEST", "ALL" }.
     *
     * @param name the level name to parse
     * @return the level value corresponding to the given name.
     */
    public static int parse(String name) {
        // First check one of the known names

        if ("OFF".equals(name)) {
            return OFF;
        } else if ("SEVERE".equals(name)) {
            return SEVERE;
        } else if ("WARNING".equals(name)) {
            return WARNING;
        } else if ("INFO".equals(name)) {
            return INFO;
        } else if ("CONFIG".equals(name)) {
            return CONFIG;
        } else if ("FINE".equals(name)) {
            return FINE;
        } else if ("FINER".equals(name)) {
            return FINER;
        } else if ("FINEST".equals(name)) {
            return FINEST;
        } else if ("ALL".equals(name)) {
            return ALL;
        }

        // Try a number

        try {
            return Integer.parseInt(name);
        } catch (NumberFormatException ex) {
            // Fall through
        }

        throw new IllegalArgumentException("Bad level name: \"" + name + "\"");
    }
}
