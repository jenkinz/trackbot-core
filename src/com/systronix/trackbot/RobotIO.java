/*
 * Date: Oct 20, 2007
 * Time: 5:23:16 PM
 *
 * (c) 2007 Systronix Inc.  All Rights reserved.
 * 939 Edison Street, Salt Lake City, UT, USA  84111
 * http://www.systronix.com/
 */
package com.systronix.trackbot;

import com.systronix.io.Debug;
import com.qindesign.util.logging.Level;
import com.qindesign.util.PseudoThread;
import com.qindesign.util.Latch;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;

/**
 * Provides communication with the TrackBot system.
 * <p>
 * Features:</p>
 * <ul>
 * <li>The output can be sent on a separate thread, if desired.</li>
 * <li>Input and output buffer sizes can be specified.</li>
 * <li>Timeouts can be detected.</li>
 * </ul>
 *
 * @author Shawn Silverman
 * @version 0.4
 */
public class RobotIO {
    /* Debug flag.  This causes certain debugging messages to be printed. *
    private static final boolean DEBUG = true;*/

    /**
     * The system property containing the timeout poll interval in ms.
     * Setting this overrides the default.
     */
    public static final String PROP_ROBOT_IO_TIMEOUT_POLL_INTERVAL = "com.systronix.trackbot.RobotIO.timeoutPollInterval";

    /** The default timeout poll interval, in ms. */
    public static final int DEFAULT_TIMEOUT_POLL_INTERVAL = 100;

    // I/O streams

    private InputStream  in;
    private OutputStream out;

    // Messages

    private int[] queueInfo;
    private int queueEnd;  // Exclusive
    private int queueStart;
    private boolean queueEmpty;

    private Object queueLock = new Object();
    private Latch ackLatch = new Latch();

    // Buffers

    private byte[] inBuf;
    private byte[] outBuf;
    private int inBufPos;
    private int outBufLen;

    // Timeouts

    private volatile long txMsgTimestamp;
    private long rxMsgTimestamp;
    private PseudoThread timeoutWatcher;
    private int timeout;
    private int timeoutPollInterval;
    private boolean timeoutStatus;

    // Data statistics

    private int ackCount;
    private int nakCount;
    private int inputOverflowCount;  // Counts input overflow
    private int msgsSent;

    // Threads and thread control

    private Thread inputThread;
    private Thread outputThread;
    private volatile boolean stopped;

    // Listener

    private volatile Listener listener;

    /**
     * Listens to events from this I/O system.  All methods should be executed
     * as quickly as possible since most are run on the I/O threads.
     *
     * @author Shawn Silverman
     */
    public interface Listener {
        /**
         * Indicates that a message was received.  The message does not
         * contain the terminating CR.
         * <p>
         * Note that this message is executed on the input thread, so
         * implementors should exit as quickly as possible.</p>
         *
         * @param b the byte array containing the message
         * @param off offset into the array
         * @param len the message length, excluding the final CR
         */
        public void messageReceived(byte[] b, int off, int len);

        /**
         * Indicates that an EOF was received from the input stream.  This
         * may indicate a timeout.  For example, many implementations of
         * serial streams indicate an EOF on timeout.
         */
        public void inputEOF();

        /**
         * Indicates that an I/O error occurred while reading input.  If this
         * event is received, then the system I/O threads will have been
         * terminated.
         *
         * @param ex the associated {@link IOException} object
         */
        public void inputError(IOException ex);

        /**
         * Indicates that an I/O error occurred while writing output.  If this
         * event is received, then the system I/O threads will have been
         * terminated.
         *
         * @param ex the associated {@link IOException} object
         */
        public void outputError(IOException ex);

        /**
         * Indicates that a communication timeout occurred.  A timeout is
         * considered to have happened if a '+' (ACK) or '-' (NAK) has not
         * been received since sending a message to the robot.
         * <p>
         * This also indicates that the timeout condition has ceased if the
         * robot has resumed communication.</p>
         * <p>
         * If a listener is added sometime after a timeout condition and
         * before the condition has cleared, then it will not receive this
         * event until the next timeout condition occurs.
         * </p>
         *
         * @param state <code>true</code> if a timeout occurred, and
         *              <code>false</code> if the robot has resumed
         *              communication
         * @param ms the timeout used to determine this condition, in ms
         */
        public void timeoutStatus(boolean state, int ms);
    }

    /**
     * A thread that watches for robot communication timeout.
     *
     * @author Shawn Silverman
     */
    private final class TimeoutWatcher extends PseudoThread {
        /**
         * Creates a new timeout watcher with the given sampling interval, in
         * ms.
         *
         * @param interval the checking interval
         */
        TimeoutWatcher(int interval) {
            super(interval);
        }

        /**
         * Checks the timestamps.
         */
        public void doWork() {
            // Don't do anything if we haven't yet sent anything

            if (txMsgTimestamp == 0) return;

            // Check if the last message was not received in time

            Listener listener = RobotIO.this.listener;  // For threading reasons
            if (listener == null) return;

            // Take into account the condition when the RX timestamp has never
            // been set
            // Initialize it to the first TX timestamp

            long rxTimestamp;
            long txTimestamp;

            synchronized (RobotIO.this) {
                if (rxMsgTimestamp == 0) {
                    rxMsgTimestamp = txMsgTimestamp;
                }
                rxTimestamp = rxMsgTimestamp;
                txTimestamp = txMsgTimestamp;
            }

            //Debug.finest("rxTimestamp=" + rxTimestamp + ", txTimestamp=" + txTimestamp);
            if ((rxTimestamp <= txTimestamp)
                  && ((txTimestamp - rxTimestamp >= timeout)
                      || (System.currentTimeMillis() - txTimestamp >= timeout))) {
                // A message was not received in time, so set the timeout status

                boolean flag = false;
                synchronized (RobotIO.this) {
                    if (!timeoutStatus) {
                        timeoutStatus = true;
                        flag = true;
                    }
                }

                if (flag) {
                    //Debug.finest("rxTimestamp=" + rxTimestamp + ", txTimestamp=" + txTimestamp);
                    listener.timeoutStatus(true, timeout);
                    ackReceived(-1);
                }
            }
        }
    }

    /**
     * Input behavior.
     *
     * @author Shawn Silverman
     */
    private final class InputTask extends Thread {
        /**
         * This implements the asynchronous input portion of the I/O
         * subsystem.
         */
        public void run() {
            // Keep track of bytes that are inside or ouside a message
            // This is necessary to know when to accumulate data before a CR

            boolean inMessage = false;
            boolean inOverflow = false;

            while (!stopped) {
                // Read a byte

                int b;
                try {
                    b = in.read();
                } catch (IOException ex) {
                    // Error!
                    // DONETODO Is there a better way to handle this than just return?

                    if (ex instanceof InterruptedIOException) {
                        // Some input streams throw this when interrupted
                        // PipedInputStream does this, for example
                        // Connections created through the GCF in JavaME may also
                        // throw this
                        // Sockets can throw this

                        // Assume that being interrupted means to quit

                        Debug.fine("Interrupted read!");
                    } else {
                        Debug.fine("I/O error during read!");
                    }

                    RobotIO.this.stopIO();

                    Listener listener = RobotIO.this.listener;  // For threading reasons
                    if (listener != null) {
                        listener.inputError(ex);
                    }
                    return;
                }

                // The thread may have been stopped while waiting for input,
                // so check this here before doing more work

                if (stopped) break;

                // Append to the input buffer if we're currently in a message

                if (inMessage) {
                    if (b >= 0) {
                        // A valid byte was received

                        if (b == '\r') {
                            // End-of-message

                            inMessage = false;
                            messageReceived(inOverflow);
                        } else {
                            // The byte is part of the message

                            // Check for overflow

                            if (inBufPos >= inBuf.length) {
                                if (!inOverflow) {
                                    inOverflow = true;
                                    inputOverflowCount++;
                                }
                            } else {
                                inBuf[inBufPos++] = (byte)b;
                            }
                        }
                    }
                    // Ignore the EOF condition because it's a possible timeout
                } else {
                    switch (b) {
                        case '+':
                        case '-':
                            // Track the acknowledge type before notification
                            // so that any debug messages will print in an
                            // appropriate order

                            if ('+' == b) {
                                // Message ACK

                                ackCount++;
                                if (Debug.isLoggable(Level.FINER)) {
                                    Debug.finer("System RCV: + (ACK for "
                                            + peekQueue() + ')');
                                }
                            } if ('-' == b) {
                                // Message NAK

                                nakCount++;
                                if (Debug.isLoggable(Level.FINER)) {
                                    Debug.finer("System RCV: - (NAK for "
                                            + peekQueue() + ')');
                                }
                            }

                            // Notify the output thread that an ACK or NAK
                            // was received

                            ackReceived(b);

                            // Manage the timeout state

                            commReceived();

                            break;

                        case '?':
                        case '!':
                            // It's possible for the robot to send messages
                            // asynchronously, without a '+' or '-', so manage
                            // the timeout state here too

                            commReceived();

                            // Expect a new message
                            // Also reset the overflow condition

                            inMessage = true;
                            inOverflow = false;
                            inBufPos = 1;
                            inBuf[0] = (byte)b;

                            break;

                        case -1:
                            // Possible timeout, if using serial I/O
                            // TODO Can we handle this better?

                            // Notify the output thread that a timeout was
                            // received

                            ackReceived(-1);

                            Debug.finer("System TIMEOUT or EOF");
                            /*Listener listener = RobotIO.this.listener;  // For threading reasons
                            if (listener != null) {
                                listener.inputEOF();
                            }*/
                            break;

                        default:
                            // Ignore because we're not inside a message
                    }
                }//Not inside a message
            }//Loop until stopped
        }//run() method
    }//InputTask class

    /**
     * Output behavior.
     *
     * @author Shawn Silverman
     */
    private final class OutputTask extends Thread {
        /**
         * This implements the asynchronous output portion of the I/O
         * subsystem.
         */
        public void run() {
            while (!stopped) {
                // Wait until there's data to send

                synchronized (queueLock) {
                    while (queueEmpty) {
                        try {
                            queueLock.wait();
                        } catch (InterruptedException ex) {
                            // We're done

                            return;
                        }
                    }

                    // Data has become available

                    // Write a message and wait for a response or timeout

                    int msgLen = queueInfo[queueStart];
                    ackLatch.resetLatch();
                    try {
                        out.write(outBuf, 0, msgLen);
                        out.flush();

                        msgsSent++;
                    } catch (IOException ex) {
                        // Error!
                        // DONETODO Is there a better way to handle this than just return?

                        RobotIO.this.stopIO();

                        Listener listener = RobotIO.this.listener;  // For threading reasons
                        if (listener != null) {
                            listener.outputError(ex);
                        }
                        return;
                    }

                    // Timeout watching

                    synchronized (RobotIO.this) {
                        txMsgTimestamp = System.currentTimeMillis();
                    }

                    // Don't create the debug string if we don't have to

                    if (Debug.isLoggable(Level.FINER)) {
                        Debug.finer("System SND: " + peekQueue()
                                + " [" + msgLen + ']');
                    }
                }//Queue lock

                // Wait for some sort of ack
                // Only wait, however, if we're not in a timeout state

                try {
                    if (!timeoutStatus) {
                        ackLatch.waitLatch();
                    } else {
                        // Give other threads a chance to execute
                        // Use the timeout poll interval because it's a good
                        // value for avoiding continuously bombarding the
                        // robot with unacknowledged messages

                        Thread.sleep(timeoutPollInterval);
                    }
                } catch (InterruptedException ex) {
                    // We're done

                    return;
                }
            }//While not stopped
        }
    }

    /**
     * Creates a new TrackBot communications object.  This also starts the
     * asynchronous I/O threads.
     * <p>
     * The timeout condition is polled, and this poll interval can be specified
     * in the {@linkplain #PROP_ROBOT_IO_TIMEOUT_POLL_INTERVAL timeout poll}
     * system property.  If this is not set, or if it contains an invalid
     * integer, then a default of {@link #DEFAULT_TIMEOUT_POLL_INTERVAL} is
     * used.  This poll interval should be less than or equal to the timeout
     * argument value.</p>
     * <p>
     * A negative timeout value indicates that robot timeouts are not
     * monitored.</p>
     *
     * @param in the input stream from the TrackBot
     * @param inBufSize size to use for the internal input buffer
     * @param out the output stream to the TrackBot
     * @param outBufSize size to use for the internal output buffer
     * @param timeout an ACK or NAK response is expected from the robot within
     *                this time
     * @throws NullPointerException if the input or output streams are
     *         <code>null</code>.
     * @throws IllegalArgumentException if the buffer sizes are not positive
     *         or if the timeout is negative.
     */
    public RobotIO(InputStream in, int inBufSize,
                   OutputStream out, int outBufSize,
                   int timeout) {
        // Check the arguments

        if (in == null || out == null) {
            throw new NullPointerException();
        }

        if (inBufSize <= 0 || outBufSize <= 0) {
            throw new IllegalArgumentException(
                    "Input and output buffer sizes must be positive");
        }

        this.in = in;
        this.out = out;

        // Allocate the buffers

        inBuf  = new byte[inBufSize];
        outBuf = new byte[outBufSize];

        // There can be at most outBufSize messages

        queueInfo = new int[outBufSize];
        queueEmpty = true;

        startIO();

        // Timeout monitoring
        // Set the timeout watcher at a slightly lower priority

        this.timeout = timeout;
        this.timeoutPollInterval = getIntProperty(PROP_ROBOT_IO_TIMEOUT_POLL_INTERVAL, DEFAULT_TIMEOUT_POLL_INTERVAL);

        if (timeout >= 0) {
            timeoutWatcher = new TimeoutWatcher(timeoutPollInterval);
            timeoutWatcher.setPriority(
                    Math.max(Thread.currentThread().getPriority() - 1,
                            Thread.MIN_PRIORITY));
            timeoutWatcher.suspend();
            timeoutWatcher.start();
        }
    }

    /**
     * Peeks at the first message in the queue and returns it in string form.
     * This strips any trailing CR and replaces it with the string
     * <code>"\r"</code>.  This returns <code>null</code> if the queue is
     * empty.
     *
     * @return the top of the queue as a string, or <code>null</code> if the
     *         queue is empty.
     */
    private String peekQueue() {
        if (queueEmpty) return null;

        int msgLen = queueInfo[queueStart];

        String s;
        if (outBuf[msgLen - 1] == '\r') {
            // Don't return the trailing CR

            return new String(outBuf, 0, msgLen - 1).concat("\\r");
        } else {
            return new String(outBuf, 0, msgLen);
        }
    }

    /**
     * Gets an integer system property.  This method is for CLDC systems.
     *
     * @param name the property name
     * @param defaultVal this value is used if the property does not exist or
     *                   if it contains an invalid integer
     * @return the value of the property.
     */
    private static int getIntProperty(String name, int defaultVal) {
        try {
            String s = System.getProperty(name);
            if (s != null) {
                return intDecode(s).intValue();
            }
        } catch (NumberFormatException ex) {
        }

        // Return the default

        return defaultVal;
    }

    /**
     * Decodes an integer, similar to how {@link Integer#decode(String)}
     * behaves.  This method is for CLDC systems.
     *
     * @param s the string to decode
     * @return a new {@link Integer} containing the decoded value.
     */
    private static Integer intDecode(String s) {
        int index;
        boolean neg;

        // Negative

        if (s.startsWith("-")) {
            neg = true;
            index = 1;
        } else {
            neg = false;
            index = 0;
        }

        int radix;

        // Radix

        if (s.startsWith("0x", index) || s.startsWith("0X")) {
            index += 2;
            radix = 16;
        } else if (s.startsWith("#")) {
            index++;
            radix = 16;
        } else if (s.startsWith("0")) {
            index++;
            radix = 8;
        } else {
            radix = 10;
        }

        // Parse

        s = s.substring(index);

        try {
            Integer i = Integer.valueOf(s, radix);
            return neg ? new Integer(-i.intValue()) : i;
        } catch (NumberFormatException ex) {
            // Integer.MIN_VALUE case

            if (neg) s = "-".concat(s);
            return Integer.valueOf(s, radix);
        }
    }

    /*
     * Gets an Boolean system property.  This method is for CLDC systems.
     *
     * @param name the property name
     * @param defaultVal this value is used if the property does not exist
     * @return the Boolean value of the property.
     *
    private static boolean getBooleanProperty(String name, boolean defaultVal) {
        String s = System.getProperty(name);

        // Possibly return the default value

        if (s == null) return defaultVal;

        return "true".equalsIgnoreCase(s);
    }*/

    /**
     * Stops the asynchronous I/O system.  This also releases any internal
     * buffers.
     * <p>
     * Note that once the I/O system is stopped, it cannot be restarted.  A
     * new object must be created.</p>
     */
    public synchronized void stopIO() {
        if (inputThread == null) {
            return;
        }

        // Interrupt any interruptible things happening,
        // eg. I/O read() and wait()

        stopped = true;

        Thread t = inputThread;
        inputThread = null;
        t.interrupt();

        if (outputThread != null) {
            t = outputThread;
            outputThread = null;
            t.interrupt();
        }

        // Release the buffers

        inBuf = null;
        outBuf = null;

        // Release other resources

        listener = null;
        if (timeoutWatcher != null) {
            timeoutWatcher.stop();
        }
    }

    /**
     * Starts the asynchronous I/O system.
     *
     * @throws IllegalThreadStateException if the asynchronous threads are
     *         already running or if the I/O was stopped.
     */
    private synchronized void startIO() {
        if (inputThread != null || stopped) {
            throw new IllegalThreadStateException();
        }

        // Start the I/O threads

        inputThread = new InputTask();
        inputThread.start();

        outputThread = new OutputTask();
        outputThread.start();
    }

    // Statistics

    /**
     * Gets the total number of ACKs received (the <code>'+'</code> response).
     *
     * @return the total number of ACKs received.
     */
    public int getAckCount() {
        return ackCount;
    }

    /**
     * Gets the total number of NAKs received (the <code>'-'</code> response).
     *
     * @return the total number of NAKs received.
     */
    public int getNakCount() {
        return nakCount;
    }

    /**
     * Gets the total number of times a received message overflowed the input
     * buffer.
     *
     * @return the total number of input overflows.
     */
    public int getInputOverflowCount() {
        return inputOverflowCount;
    }

    /**
     * Gets the total number of messages sent to the output.  Note that this
     * may be less than the number of message queue attempts since the output
     * buffer may have been full during some of those attempts.
     *
     * @return the total number of sent messages.
     */
    public int getSentCount() {
        return msgsSent;
    }

    /**
     * Communication was received from the robot, so manage the timeout
     * state.
     */
    private void commReceived() {
        if (timeout < 0) return;

        boolean flag = false;

        synchronized (this) {
            rxMsgTimestamp = System.currentTimeMillis();
            if (timeoutStatus) {
                timeoutStatus = false;
                flag = true;
            }
        }

        Listener listener = this.listener;  // For threading reasons
        if (flag && listener != null) {
            listener.timeoutStatus(false, timeout);
        }
    }

    /**
     * Some sort of acknowledgement was received, either an ACK, NAK, or
     * timeout.
     *
     * @param b the ack byte, or <code>-1</code> if the acknowledge was a
     *          timeout
     */
    private void ackReceived(int b) {
        synchronized (queueLock) {
            if (queueEmpty) return;

            // Remove the message from the queue
            // But only if it's not a timeout

            if (b != -1) {
                int msgLen = queueInfo[queueStart];
                System.arraycopy(outBuf, msgLen, outBuf, 0, outBufLen - msgLen);
                outBufLen -= msgLen;

                queueStart++;
                if (queueStart >= queueInfo.length) {
                    queueStart = 0;
                }
                if (queueStart == queueEnd) {
                    queueEmpty = true;
                }

                //Debug.finest("- QUEUE INFO: " + queueInfoToString());
                //Debug.finest("- QUEUE DATA: " + queueToString());
            } else {
                // Remove all but the first message from the queue

                queueEnd = queueStart + 1;
                if (queueEnd >= queueInfo.length) {
                    queueEnd = 0;
                }
                outBufLen = queueInfo[queueStart];
            }

            // Notify those waiting for an acknowledge

            ackLatch.setLatch();
        }
    }

    /**
     * A message was received and is waiting to be processed.  The
     * <code>overflow</code> parameter indicates whether the message
     * overflowed the input buffer.  If this is the case, then the message is
     * incomplete.
     *
     * @param overflow indicates whether the message overflowed the buffer, and
     *                 if so, that the message is incomplete
     */
    private void messageReceived(boolean overflow) {
        if (stopped || overflow) {
            // Ignore the overflow messages, currently

            return;
        }

        // Don't create the string if we don't have to

        if (Debug.isLoggable(Level.FINER)) {
            Debug.finer("System RCV: " + new String(inBuf, 0, inBufPos) + " [" + inBufPos + "]");
        }

        // Indicate a message to the listener

        Listener listener = this.listener;  // For threading reasons
        if (listener != null) {
            listener.messageReceived(inBuf, 0, inBufPos);
        }
    }

    /**
     * Sets the listener object for receiving I/O events.  Setting a
     * <code>null</code> listener is possible.
     *
     * @param l the listener, can be <code>null</code>
     */
    public void setListener(Listener l) {
        this.listener = l;

        if (timeoutWatcher != null) {
            if (l != null) {
                timeoutWatcher.resume();
            } else {
                timeoutWatcher.suspend();
            }
        }
    }

    /**
     * Convenience method that queues a message for output.  The message
     * should include a CR.
     *
     * @param msg the message to send
     * @return whether there was enough space in the output buffer to
     *         accomodate the message, or <code>false</code> if the I/O
     *         subsystem is not running.
     * @see #queueMessage(byte[], int, int)
     */
    public boolean queueMessage(byte[] msg) {
        return queueMessage(msg, 0, msg.length);
    }

    /**
     * Queues a message for output.  The message should include a CR.
     * <p>
     * This returns whether there was enough space in the output buffer to
     * accomodate the message.  This will return <code>false</code> if the
     * I/O subsystem is stopped.</p>
     *
     * @param msg a byte array containing the message to send
     * @param off location of the message start
     * @param len message length, including the CR
     * @return whether there was enough space in the output buffer to
     *         accomodate the message, or <code>false</code> if the I/O
     *         subsystem is not running.
     * @throws IndexOutOfBoundsException if the array parameters are out of
     *         range.
     */
    public boolean queueMessage(byte[] msg, int off, int len) {
        // NOTE The output must be on a separate thread in case the callbacks
        //      from the input thread queue another message.  For example, if
        //      a version was received, the robot behaviour may choose to
        //      reenable sensors

        if (stopped) return false;
        if (len <= 0) return true;

        synchronized (queueLock) {
            if (outBufLen + len <= outBuf.length) {
                // The output buffer is large enough to hold the message

                System.arraycopy(msg, off, outBuf, outBufLen, len);
                outBufLen += len;

                // Update the queue

                queueInfo[queueEnd++] = len;
                queueEmpty = false;
                if (queueEnd >= queueInfo.length) {
                    queueEnd = 0;
                }

                //Debug.finest("+ QUEUE INFO: " + queueInfoToString());
                //Debug.finest("+ QUEUE DATA: " + queueToString());

                // Notify the output thread that there's a message waiting

                queueLock.notify();  // Notify only the one thread
                return true;
            } else {
                if (Debug.isLoggable(Level.FINEST)) {
                    Debug.finest("System QUEUE FULL");

                    // Print the queue

                    Debug.finest(queueToString());
                }
                return false;
            }
        }
    }

    /**
     * Returns the queue as a string.
     */
    private String queueToString() {
        StringBuffer buf = new StringBuffer();
        buf.append('[');
        for (int i = 0; i < outBufLen; i++) {
            byte b = outBuf[i];
            switch (b) {
                case '\r': buf.append("\\r"); break;
                case '\b': buf.append("\\b"); break;
                case '\f': buf.append("\\f"); break;
                case '\n': buf.append("\\n"); break;
                case '\t': buf.append("\\t"); break;
                case '\\': buf.append("\\\\"); break;
                default:
                    if (b < 0x20 || 0x7f <= b) {
                        buf.append("\\x")
                                .append(HEX_DIGITS[(b >> 4) & 0x0f])
                                .append(HEX_DIGITS[ b       & 0x0f]);
                    } else {
                        buf.append((char)b);
                    }
            }
        }
        buf.append(']');

        return buf.toString();
    }

    /*private String queueInfoToString() {
        if (queueEmpty) return "[]";

        StringBuffer buf = new StringBuffer();
        buf.append('[');
        int start = queueStart;
        do {
            if (start != queueStart) buf.append(' ');
            buf.append(queueInfo[start++]);
            if (start >= queueInfo.length) start = 0;
        } while (start != queueEnd);
        buf.append(']');

        return buf.toString();
    }*/

    /**
     * Returns a string representation of this object.
     *
     * @return a string representation of this object.
     */
    public String toString() {
        return "Robot I/O";
    }

    private static final char[] HEX_DIGITS = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
}
