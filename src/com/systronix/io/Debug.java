/*
 * Date: Nov 14, 2007
 * Time: 12:45:44 PM
 *
 * (c) 2007 Systronix Inc.  All Rights reserved.
 * 939 Edison Street, Salt Lake City, UT, USA  84111
 * http://www.systronix.com/
 */
package com.systronix.io;

import java.io.OutputStream;
import java.io.PrintStream;

import com.qindesign.util.logging.Handler;
import com.qindesign.util.logging.Level;
import com.qindesign.util.logging.LogRecord;
import com.qindesign.util.logging.Logger;

import java.util.Map;
import java.util.HashMap;

/**
 * A utility class that manages debugging output.  The debugging output stream
 * is set to <code>System.out</code> in the class initializer.
 *
 * @author Shawn Silverman
 * @version 0.0
 */
public final class Debug extends Handler {
    /** Debug flag.  This causes debugging messages to be printed. */
    private static final boolean DEBUG = true;
    
    private static final Map testMap = new HashMap();

    /** The internal {@link Logger} object, has a name of "Debug". */
    private static Logger logger;

    /** The handler that receives log messages. */
    private static Handler logHandler;

    // Set up the debuging output stream
    static {
        // Set the debugging output stream to stdout

        setOutputStream(System.out);
    }

    /**
     * Sets the debugger's output stream.  This also sets the logging level to
     * {@link Level#FINE FINE}.
     *
     * @param out the debug output stream
     */
    public static void setOutputStream(OutputStream out) {
        if (DEBUG && out != null) {
            synchronized (Debug.class) {
                logger = Logger.getLogger("Debug");
                logger.setLevel(Level.ALL);

                // Ensure the log handler isn't added more than once

                if (logHandler == null) {
                    logHandler = new Debug(out);
                    logHandler.setLevel(Level.FINE);
                    logger.addHandler(logHandler);
                }
            }
        } else {
            logger = null;
        }
    }

    /**
     * Sets the debug display level.  The default is {@link Level#FINE FINE}.
     *
     * @param level the new debug logging level
     */
    public static void setLoggingLevel(int level) {
        if (!DEBUG || logHandler == null) {
            return;
        }

        logHandler.setLevel(level);
    }

    /**
     * Checks if messages of the given level would be logged.  This returns
     * <code>false</code> if the {@linkplain #setOutputStream(OutputStream)
     * output stream} has not yet been set.
     *
     * @param level the level to check
     * @return whether messages of the given level would be logged.
     */
    public static synchronized boolean isLoggable(int level) {
        if (!DEBUG || logHandler == null) {
            return false;
        }

        return level >= logHandler.getLevel()
               && logHandler.getLevel() != Level.OFF;
    }

    /**
     * Prints a {@link Level#FINEST FINEST}-level debugging message.
     *
     * @param msg the message to print
     * @see Level#FINE
     */
    public static void finest(String msg) {
        log(Level.FINEST, msg);
    }

    /**
     * Prints a {@link Level#FINER FINER}-level debugging message.
     *
     * @param msg the message to print
     * @see Level#FINE
     */
    public static void finer(String msg) {
        log(Level.FINER, msg);
    }

    /**
     * Prints a {@link Level#FINE FINE}-level debugging message.
     *
     * @param msg the message to print
     * @see Level#FINE
     */
    public static void fine(String msg) {
        log(Level.FINE, msg);
    }

    /**
     * Prints a {@link Level#INFO INFO}-level debugging message.
     *
     * @param msg the message to print
     * @see Level#INFO
     */
    public static void info(String msg) {
        log(Level.INFO, msg);
    }

    /**
     * Prints a {@link Level#WARNING WARNING}-level debugging message.
     *
     * @param msg the message to print
     * @see Level#WARNING
     */
    public static void warning(String msg) {
        log(Level.WARNING, msg);
    }

    /**
     * Prints a {@link Level#SEVERE SEVERE}-level debugging message.
     *
     * @param msg the message to print
     * @see Level#SEVERE
     */
    public static void severe(String msg) {
        log(Level.SEVERE, msg);
    }

    /**
     * Logs a message with the given message level.
     *
     * @param level the message level
     * @param msg the message
     */
    public static void log(int level, String msg) {
        log(level, null, null, msg, null);
    }

    /**
     * Logs a message with the given message level and contents.
     *
     * @param level the message level
     * @param sourceClass the class that claims to have issued the logging
     *                    request
     * @param sourceMethod the method that claims to have issued the logging
     *                     request
     * @param msg the message
     * @param t the associated exception
     */
    public static void log(int level,
                           String sourceClass, String sourceMethod,
                           String msg, Throwable t) {
        if (DEBUG) {
            synchronized (Debug.class) {
                if (logger != null) {
                    logger.log(level, sourceClass, sourceMethod, msg, t);
                }
            }
        }
    }

    /** The debug output stream. */
    private PrintStream out;

    /** Shared {@link Date Date} object. */
    //private Date date = new Date();

    /**
     * Creates a new log handler using the given output stream.
     *
     * @param out the debug output
     * @throws NullPointerException if the output stream is <code>null</code>.
     */
    private Debug(OutputStream out) {
        if (out == null) {
            throw new NullPointerException();
        }

        if (!(out instanceof PrintStream)) {
            out = new PrintStream(out);
        }
        this.out = (PrintStream)out;
    }

    /*
     * Handler methods.
     */

    public synchronized void publish(LogRecord record) {
        if (out == null) return;
        if (!isLoggable(record)) return;

        // Date and time

        /*synchronized (this) {
            date.setTime(System.currentTimeMillis());
            out.print(date);
        }*/

        // Class and method names

        String className = record.getSourceClassName();
        String methodName = record.getSourceMethodName();

        //out.write(' ');
        if (className != null) {
            out.print(className);
        } else {
            out.print(record.getLoggerName());
        }

        if (methodName != null) {
            out.write(className == null ? ' ' : '.');
            out.print(methodName);
        }

        // Level name

        out.write(' ');
        out.print(Level.toString(record.getLevel()));
        out.print(": ");
        out.println(record.getMessage());

        // Possible stack trace
        // TODO Make the stack trace compatible with CLDC

        if (record.getThrown() != null) {
            //record.getThrown().printStackTrace(out);
            out.println(record.getThrown());
        }
    }

    public synchronized void flush() {
        if (out != null) {
            out.flush();
        }
    }

    public synchronized void close() {
        if (out != null) {
            out.close();  // This also flushes

            out = null;
        }
    }

    /**
     * Returns a string representation of this object.
     *
     * @return a string representation of this object.
     */
    public String toString() {
        return "Debug";
    }
}
