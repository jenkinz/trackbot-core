/* Date: Mar 12, 2009
 *
 * (c) 2008 Systronix Inc.  All Rights reserved.
 * 939 Edison Street, Salt Lake City, UT, USA  84111
 * http://www.systronix.com/
 */

package com.systronix.trackbot.demo;

import com.systronix.io.Debug;
import com.systronix.trackbot.RobotIO;

/**
 * An abstract class to provide methods and variables necessary for implementing
 * a collaborative behavior amongst multiple TrackBots. In other words, extend
 * and utilize this class to make a TrackBot behavior that is aware of other
 * TrackBots and their position relative to itself.
 *
 * Beaconer extends {@link Wanderer} so that the implementing TrackBot behavior
 * can default or go to a basic wander and/or avoid behavior whenever it is
 * necessary or desirable.
 *
 * @author Brian Jenkins
 */
public abstract class Beaconer extends Wanderer {

    /**
     * Holds the ID of the TrackBot that this behavior is running on.
     */
    protected int trackBotID;

    /**
     * Holds the current beacon states for each TrackBot sensor.
     * Each TrackBot has an instance of this array.
     * Call the TrackBot which holds an instance of this array "this" TrackBot,
     * "us" or "our TrackBot". Our TrackBot is the TrackBot whose sensors we
     * can read; all the others emit and this TrackBot receives them (or not).<br /><br />
     *
     * In Greenfoot, overlap of sensor regions between bots defines sensors
     * "seeing" each other.<br /><br />
     *
     * The first index is the ID of the other TrackBot which our TrackBot might
     * see. Assume our robot has ID 5. Therefore robot 0 is not us.<br /><br />
     *
     * So [0][X] holds data for robot 0's emitters. Are they seen by us?
     * X is the index of the {@link SENSOR_NAMES} array, in which
     * "Aft Starboard" is the 0th element. So element [0][0] is an int,
     * referring to our AFT STARBOARD sensor. What value does that element hold?
     * If 0, we don't see any other emitters of Robot 0: {@link SENSORS_NONE}.<br /><br />
     *
     * If nonzero, the value is the logical OR of all other emitters
     * of Robot 0 which we do see. So if the value is 3, that's the OR of
     * {@link SENSOR_AFT_STARBOARD} and {@link SENSOR_AFT_PORT}, so that
     * means that our Aft Starboard sensor saw these two emitters of Robot 0.<br /><br />
     *
     * In this example, if we are Robot 5, then element [5][X] holds data
     * for our own emitters. It's not clear how we would use this "self"
     * array right now. For example [5][0] = 0 means our aft starboard receiver
     * didn't see any of our own emitters. (Note that for the Greenfoot,
     * this "self" array would never contain nonzero values, because the
     * simulator never checks to see if a sensor region is intersecting itself.
     * The concept of "emitter" and "receiver" in the simulation is not defined;
     * right now, each sensor is represented by a circle sector or "pie" in the
     * Greenfoot world, and beaconing is defined as the intersection of two or
     * more of these "pie" regions by at least two <i>differing</i> TrackBots.)<br /><br />
     *
     * The first index can be [0..63] to support 64 . The second index has 8 int
     * elements; one to hold the beacon state for each of our robot's sensors.<br /><br />
     *
     */
    protected int[][] beaconState;

    /**
     * The maximum number of supported TrackBots for beaconing.
     */
    protected static final int NUM_OTHER_TRACKBOTS = 64;

    /**
     * Holds the IDs of other TrackBots that can potentially be followed.
     */
    protected int[] otherTrackBotIDs = new int[NUM_OTHER_TRACKBOTS];

    /**
     * The maximum state value for the superclass.  Additional states in subclasses
     * or other places should start at <code>MAX_STATE&nbsp;+&nbsp;1</code>.
     */
    protected static final int MAX_STATE = STATE_WANDER;

    /**
     * Names for each TrackBot sensor in the order of which they are processed.
     */
    protected static final String[] SENSOR_NAMES = {
        "Aft Starboard",
        "Aft Port",
        "Fore Starboard",
        "Fore Port",
        "Starboard Aft",
        "Port Aft",
        "Starboard Fore",
        "Port Fore"
    };

    /**
     * Names for each possible state for objects of this class.
     */
    protected static final String[] STATE_NAMES = {
        "Stopped",
        "Runnaway",
        "Wander",
        "Follow",
        "Catch Up",
        "Avoid and Catch Up"
    };


    // Convenience constants

    // For avoidance etc: sensors of the TrackBot

    /** No sensors detecting. */
    protected static final int SENSORS_NONE = 0;

    /** All sensors detecting. */
    protected static final int SENSORS_ALL = 0x00ff;

    /** The Aft Starboard sensor. */
    protected static final int SENSOR_AFT_STARBOARD = 1;

    /** The Aft Port sensor. */
    protected static final int SENSOR_AFT_PORT = 2;

    /** The Fore Starboard sensor. */
    protected static final int SENSOR_FORE_STARBOARD = 4;

    /** The Fore Port sensor. */
    protected static final int SENSOR_FORE_PORT = 8;

    /** The Starboard Aft sensor. */
    protected static final int SENSOR_STARBOARD_AFT = 16;

    /** The Starboard Fore sensor. */
    protected static final int SENSOR_STARBOARD_FORE = 64;

    /** The Port Aft sensor. */
    protected static final int SENSOR_PORT_AFT = 32;

    /** The Port Fore sensor. */
    protected static final int SENSOR_PORT_FORE = 128;


    // For beaconing: sensors of the TrackBot that this Follower instance is running on

    /** This TrackBot's AFT STARBOARD sensor. */
    protected static final int MY_AFT_STAR  = 0;

    /** This TrackBot's AFT PORT sensor. */
    protected static final int MY_AFT_PORT  = 1;

    /** This TrackBot's FORE STARBOARD sensor. */
    protected static final int MY_FORE_STAR = 2;

    /** This TrackBot's FORE PORT sensor. */
    protected static final int MY_FORE_PORT = 3;

    /** This TrackBot's STARBOARD AFT sensor. */
    protected static final int MY_STAR_AFT  = 4;

    /** This TrackBot's PORT AFT sensor. */
    protected static final int MY_PORT_AFT  = 5;

    /** This TrackBot's STARBOARD FORE sensor. */
    protected static final int MY_STAR_FORE = 6;

    /** This TrackBot's PORT FORE sensor. */
    protected static final int MY_PORT_FORE = 7;


    // For beaconing: sensors of the other TrackBot of interest - represents corresponding bit in beaconState

    /** The other TrackBot's AFT STARBOARD sensor. */
    protected static final int OTHER_AFT_STAR = 0x01;

    /** The other TrackBot's AFT PORT sensor. */
    protected static final int OTHER_AFT_PORT = 0x02;

    /** The other TrackBot's FORE STARBOARD sensor. */
    protected static final int OTHER_FORE_STAR= 0x04;

    /** The other TrackBot's FORE PORT sensor. */
    protected static final int OTHER_FORE_PORT = 0x08;

    /** The other TrackBot's STARBOARD AFT sensor. */
    protected static final int OTHER_STAR_AFT = 0x10;

    /** The other TrackBot's PORT AFT sensor. */
    protected static final int OTHER_PORT_AFT = 0x20;

    /** The other TrackBot's STARBOARD FORE sensor */
    protected static final int OTHER_STAR_FORE = 0x40;

    /** The other TrackBot's PORT FORE sensor. */
    protected static final int OTHER_PORT_FORE = 0x80;

    
    // State Machine

    /**
      * Runs the state machine as defined in the subclass-implemented methods
      * {@link chooseNextState} and {@link runState}.
      */
    protected final void stateMachine() {
        chooseNextState();
        runState();
    }


    // Some helpful Methods for Beaconing

    /**
     * Check if a beacon was detected from another TrackBot on all sensors and
     * print out live beacon status to Debug console .
     */
    protected void checkBeacons() {
        for(int i = 64; --i >= 0; ) {
            boolean enabled = false;
            for (int j = 8; --j >= 0; ) {
                // other's Aft Starboard
                if ((beaconState[i][j] & 0x01) == 0x01) {
                    enabled = true;
                    Debug.fine("ALERT: TrackBot ID " +
                                trackBotID +
                                " sensor " +
                                SENSOR_NAMES[j] +
                                " intersected with TrackBot ID " +
                                i + " Aft Starboard sensor");
                }

                // other's Aft Port
                if ((beaconState[i][j] & 0x02) == 0x02) {
                    enabled = true;
                    Debug.fine("ALERT: TrackBot ID " +
                                trackBotID +
                                " sensor " +
                                SENSOR_NAMES[j] +
                                " intersected with TrackBot ID " +
                                i + " Aft Port sensor");
                }

                // other's Fore Starboard
                if ((beaconState[i][j] & 0x04) == 0x04) {
                    enabled = true;
                    Debug.fine("ALERT: TrackBot ID " +
                                trackBotID +
                                " sensor " +
                                SENSOR_NAMES[j] +
                                " intersected with TrackBot ID " +
                                i + " Fore Starboard sensor");
                }

                // other's Fore Port
                if ((beaconState[i][j] & 0x08) == 0x08) {
                    enabled = true;
                    Debug.fine("ALERT: TrackBot ID " +
                                trackBotID +
                                " sensor " +
                                SENSOR_NAMES[j] +
                                " intersected with TrackBot ID " +
                                i + " Fore Port sensor");
                }

                // other's Starboard Aft
                if ((beaconState[i][j] & 0x10) == 0x10) {
                    enabled = true;
                    Debug.fine("ALERT: TrackBot ID " +
                                trackBotID +
                                " sensor " +
                                SENSOR_NAMES[j] +
                                " intersected with TrackBot ID " +
                                i + " Starboard Aft sensor");
                }

                // other's Port Aft
                if ((beaconState[i][j] & 0x20) == 0x20) {
                    enabled = true;
                    Debug.fine("ALERT: TrackBot ID " +
                                trackBotID +
                                " sensor " +
                                SENSOR_NAMES[j] +
                                " intersected with TrackBot ID " +
                                i + " Port Aft sensor");
                }

                // other's Starboard Fore
                if ((beaconState[i][j] & 0x40) == 0x40) {
                    enabled = true;
                    Debug.fine("ALERT: TrackBot ID " +
                                trackBotID +
                                " sensor " +
                                SENSOR_NAMES[j] +
                                " intersected with TrackBot ID " +
                                i + " Starboard Fore sensor");
                }

                // other's Port Fore
                if ((beaconState[i][j] & 0x80) == 0x80) {
                    enabled = true;
                    Debug.fine("ALERT: TrackBot ID " +
                                trackBotID +
                                " sensor " +
                                SENSOR_NAMES[j] +
                                " intersected with TrackBot ID " +
                                i + " Port Fore sensor");
                }
            }//for(j)

            // Mark that we have current data on this TrackBot
            if (enabled) {
                otherTrackBotIDs[i] = 1;
                Debug.fine("!");
            }
            else {
                otherTrackBotIDs[i] = 0;
            }
        }//for(i)
    }
    

    /**
      * Checks if any of this TrackBot's IR sensors are beaconing with any of another's.
      *
      * @return true if one or more of this TrackBot's sensors are beaconing with one or more of another's
      */
     protected boolean otherTrackBotInRange() {
         for (int i = 0; i < NUM_OTHER_TRACKBOTS; i++) {
             if (otherTrackBotIDs[i] == 0) { // No beacon from this TrackBot
                 continue;
             }
             if (
                    beaconState[i][MY_FORE_STAR] != SENSORS_NONE
                 || beaconState[i][MY_FORE_PORT] != SENSORS_NONE
                 || beaconState[i][MY_PORT_FORE] != SENSORS_NONE
                 || beaconState[i][MY_PORT_AFT]  != SENSORS_NONE
                 || beaconState[i][MY_AFT_STAR]  != SENSORS_NONE
                 || beaconState[i][MY_AFT_PORT]  != SENSORS_NONE
                 || beaconState[i][MY_STAR_AFT]  != SENSORS_NONE
                 || beaconState[i][MY_STAR_FORE] != SENSORS_NONE
                )
             {
                return true;
             }
         }
         return false;
     }

     /**
      * Checks if any of this TrackBot's IR sensors are beaconing with any of another's.
      *
      * @param id the other TrackBot's ID
      * @return true if one or more of this TrackBot's sensors are beaconing with one or more of another's
      */
     protected boolean otherTrackBotInRange(int id) {
         if (
                    beaconState[id][MY_FORE_STAR] != SENSORS_NONE
                 || beaconState[id][MY_FORE_PORT] != SENSORS_NONE
                 || beaconState[id][MY_PORT_FORE] != SENSORS_NONE
                 || beaconState[id][MY_PORT_AFT]  != SENSORS_NONE
                 || beaconState[id][MY_AFT_STAR]  != SENSORS_NONE
                 || beaconState[id][MY_AFT_PORT]  != SENSORS_NONE
                 || beaconState[id][MY_STAR_AFT]  != SENSORS_NONE
                 || beaconState[id][MY_STAR_FORE] != SENSORS_NONE
                ) {
             //Debug.fine(id + "is in range");
             return true;
         }

         return false;
     }

     /**
      * Checks if this TrackBot's sensor(s) are beaconing with any of the Followee's side sensors
      *
      * @return true if any sensor is beaconing with a side sensor on the Followee
      */
     protected boolean onSide() {
         for (int i = 0; i < NUM_OTHER_TRACKBOTS; i++) {
             if (otherTrackBotIDs[i] == 0) {
                 continue;
             }
             if (   (beaconState[i][MY_AFT_STAR]  & 0x0f) != 0
                 || (beaconState[i][MY_AFT_PORT]  & 0x0f) != 0
                 || (beaconState[i][MY_FORE_STAR] & 0x0f) != 0
                 || (beaconState[i][MY_FORE_PORT] & 0x0f) != 0
                 || (beaconState[i][MY_STAR_AFT]  & 0x0f) != 0
                 || (beaconState[i][MY_PORT_AFT]  & 0x0f) != 0
                 || (beaconState[i][MY_STAR_FORE] & 0x0f) != 0
                 || (beaconState[i][MY_PORT_FORE] & 0x0f) != 0)
             {
                 return true;
             }
         }
         return false;
     }

     /**
      * @return true if no obstacles are in range of IR sensors
      */
     protected boolean noSensorObstacles() {
        return ((powerNodeState >> 12) & 0x000f | (sensorNodeState >> 4) & 0x00f0) == SENSORS_NONE;
     }

     
     /**
      * Callback for simulation sensor and beacon states.
      *
      * @param powerNodeState
      * @param sensorNodeState
      * @param beaconState
      * @param trackBotID
      */
     public void allStates(int powerNodeState, int sensorNodeState, int[][] beaconState, int trackBotID) {
         this.powerNodeState = powerNodeState;
         this.sensorNodeState = sensorNodeState;
         this.beaconState = beaconState;
         this.trackBotID = trackBotID;
         checkBeacons(); // for TESTING only 
         stateMachine();
     }//allStates

     

     // Maintainance and other necessary methods

     /**
      * Set any default instance variables or parameters here.
      */
      private void setDefaults() {

          // Init the array of other TrackBot IDs
          for (int i = 0; i < 64; i++) {
              otherTrackBotIDs[i] = 0;
          }
     }

    /**
     * Passes creation of the object up to the superclass. Also sets any defaults
     * necessary for the object.
     *
     * @throws java.lang.Exception
     */
    public Beaconer() throws Exception {
        super();
        setDefaults();
    }

    /**
     * Passes creation of the object up to the superclass using the given I/O
     * connection. Also sets any defaults necessary for the object.
     * 
     * @param robotIO
     * @throws java.lang.Exception
     */
    public Beaconer(RobotIO robotIO) throws Exception {
        super(robotIO);
        setDefaults();
    }
}
