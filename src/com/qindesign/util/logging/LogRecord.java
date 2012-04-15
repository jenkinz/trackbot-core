/*
 * Date: Nov 17, 2005
 * Time: 12:46:09 AM
 * (c) 2005 Shawn Silverman
 */
package com.qindesign.util.logging;

/**
 * This class encapsulates one log entry.  It is used as a reusable container
 * for logging information to avoid methods with a large number of parameters.
 *
 * @author Shawn Silverman
 * @version 1.0
 */
public class LogRecord {
    private int level;
    private String msg;

    private String loggerName;
    private long millis;

    private String sourceClassName;
    private String sourceMethodName;

    private Throwable thrown;

    /**
     * Creates a new log record.
     */
    LogRecord() {
    }

    /**
     * Creates a new log record with the given logging level and message.
     *
     * @param level the logging level
     * @param msg the raw message
     */
    public LogRecord(int level, String msg) {
        setLevel(level);
        setMessage(msg);
    }

    /**
     * Clears all the values inside this record, except for the logger name.
     */
    void clear() {
        level = 0;
        msg = null;
        loggerName = null;
        millis = 0;

        sourceClassName = null;
        sourceMethodName = null;

        thrown = null;
    }

    /**
     * Gets the logging level.
     *
     * @return the logging level.
     * @see Level
     */
    public int getLevel() {
        return level;
    }

    /**
     * Sets the logging level.
     *
     * @param level the logging level
     * @see Level
     */
    public void setLevel(int level) {
        this.level = level;
    }

    /**
     * Gets the source logger's name.
     *
     * @return the source logger's name.
     */
    public String getLoggerName() {
        return loggerName;
    }

    /**
     * Sets the source logger's name.  May be <code>null</code>.
     *
     * @param name the source logger's name
     */
    public void setLoggerName(String name) {
        this.loggerName = name;
    }

    /**
     * Gets the raw message.
     *
     * @return the raw message.
     */
    public String getMessage() {
        return msg;
    }

    /**
     * Sets the raw message.  May be <code>null</code>.
     *
     * @param msg the raw message.
     */
    public void setMessage(String msg) {
        this.msg = msg;
    }

    /**
     * Gets the event time, in milliseconds.
     *
     * @return the event time.
     */
    public long getMillis() {
        return millis;
    }

    /**
     * Sets the event time.
     *
     * @param millis the event time, in milliseconds.
     */
    public void setMillis(long millis) {
        this.millis = millis;
    }

    /**
     * Gets the name of the class that claims to have issued the logging
     * request.
     *
     * @return the source method name.
     */
    public String getSourceClassName() {
        return sourceClassName;
    }

    /**
     * Sets the name of the class that claims to have issued the logging
     * request.  May be <code>null</code>.
     *
     * @param sourceClassName the source class name
     */
    public void setSourceClassName(String sourceClassName) {
        this.sourceClassName = sourceClassName;
    }

    /**
     * Gets the name of the method that claims to have issued the logging
     * request.
     *
     * @return the source method name.
     */
    public String getSourceMethodName() {
        return sourceMethodName;
    }

    /**
     * Sets the name of the method that claims to have issued the logging
     * request.  May be <code>null</code>.
     *
     * @param sourceMethodName the source method name
     */
    public void setSourceMethodName(String sourceMethodName) {
        this.sourceMethodName = sourceMethodName;
    }

    /**
     * Gets the exception associated with the log event.
     *
     * @return the associated exception.
     */
    public Throwable getThrown() {
        return thrown;
    }

    /**
     * Sets the exception associated with the log event.  May be
     * <code>null</code>.
     *
     * @param thrown the exception associated with the log event
     */
    public void setThrown(Throwable thrown) {
        this.thrown = thrown;
    }
}
