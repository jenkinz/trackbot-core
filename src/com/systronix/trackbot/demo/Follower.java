/**
 * Date: Feb 10, 2009
 *
 * (c) 2008 Systronix Inc.  All Rights reserved.
 * 939 Edison Street, Salt Lake City, UT, USA  84111
 * http://www.systronix.com/
 */

package com.systronix.trackbot.demo;

import com.systronix.trackbot.RobotIO;
import com.systronix.io.Debug;
import com.systronix.trackbot.Motors;

/**
 * This file contains methods to implement a Follower behavior for TrackBot based
 * on IR sensor beaconing. Using this behavior, TrackBot stores "beacon data," 
 * information on other TrackBots' positions in the world that it has come nearby.
 *
 * Using the beacon data stored in "beaconState," a TrackBot can attempt to follow
 * another TrackBot.
 *
 * @author Brian Jenkins
 */
public class Follower extends Wanderer {

    /**
     * Holds the ID of the TrackBot that this behavior is running on.
     */
    protected int trackBotID;

    /**
     * ID of the TrackBot currently being followed
     */
    protected int followee = -1;

    /**
     * <p>Holds the current beacon states for each TrackBot sensor - each TrackBot has an instance of this array.
     * Call the TrackBot which holds an instance of this array "this" TrackBot,
     * "us" or "our TrackBot". Our TrackBot is the TrackBot whose sensors we
     * can read; all the others emit and this TrackBot receives them (or not).
     * </p>
     * <p>
     * In Greenfoot, overlap of sensor regions between bots defines sensors "seeing" each other.
     * </p>
     * <p>
     * The first index is the ID of the other TrackBot which our TrackBot might see.
     * Assume our robot has ID 5. Therefore robot 0 is not us.
     * </p>
     * <p>
     * So [0][X] holds data for robot 0's emitters. Are they seen by us?
     * X is the index of the {@link SENSOR_NAMES} array, in which
     * "Aft Starboard" is the 0th element.
     * So element [0][0] is an int, referring to our AFT STARBOARD sensor.
     * What value does that element hold? If 0, we don't see any other emitters
     * of Robot 0: {@link SENSORS_NONE}
     * </p>
     * <p>
     * If nonzero, the value is the logical OR of all other emitters
     * of Robot 0 which we do see. So if the value is 3, that's the OR of
     * {@link SENSOR_AFT_STARBOARD} and {@link SENSOR_AFT_PORT}, so that
     * means that our Aft Starboard sensor saw these two emitters of Robot 0.
     * </p>
     * <p>
     * In this example, if we are Robot 5, then element [5][X] holds data
     * for our own emitters. It's not clear how we would use this "self"
     * array right now. For example [5][0] = 0 means our aft starboard receiver
     * didn't see any of our own emitters.
     * </p>
     * <p>
     * The first index can be [0..63] to support 64 robots
     * Second index has 8 int elements; one for each of our robot's sensors.
     * </p>
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
     * Holds the last known beacon state on the current followee.
     */
    protected int[] catchUpState = new int[8];


    // State constants for state machine

    /** The "follow" state */
    protected static final int STATE_FOLLOW = Wanderer.MAX_STATE + 1; //3

    /** The "catch up" state */
    protected static final int STATE_CATCH_UP = STATE_FOLLOW + 1; //4

    /** The "get ready to follow" state */
    protected static final int STATE_AVOID_and_CATCH_UP = STATE_CATCH_UP + 1; //5

    /**
     * Holds the last possible state for objects of this class. Classes
     * extending the Follower class should start their state constants at
     * Follower.MAX_STATE + 1.
     */
    protected static final int MAX_STATE = STATE_AVOID_and_CATCH_UP;

    /** The current state */
    protected int state = STATE_STOPPED;

    /** The last direction */
    protected int lastDir = DIR_STOP;

    /** The maximum number of catchup attempts */
    protected int max_catchup_state = 3;

    /** The current catchup iteration */
    protected int catchup_count;

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


    /**
     * Creates the Follower.
     *
     * @throws java.lang.Exception
     */
    public Follower() throws Exception {
        super();
        setDefaults();
    }

    /**
     * Creates the Follower.
     *
     * @param robotIO the robotIO object
     * @throws java.lang.Exception
     */
     public Follower(RobotIO robotIO) throws Exception {
         super(robotIO);
         setDefaults();
     }

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
      * Main
      * 
      * @param args
      * @throws java.lang.Exception
      */
     public static void main(String[] args) throws Exception {
         Follower follower = new Follower();
     }

     /**
      * Runs the state machine.
      */
     protected void stateMachine() {
         chooseNextState();
         runState();
     }

     /**
      * Chooses the next state.
      */
     protected void chooseNextState() {
        if (this.trackBotID == 0) {
            super.chooseNextState();
            return;
        }
        switch (state) {
            case STATE_STOPPED:
            case STATE_RUNAWAY:
            case STATE_WANDER:
                if (readyToFollow()) {
                    state = STATE_FOLLOW;
                    break;
                }
                state = STATE_STOPPED;
                break;
            case STATE_FOLLOW:
                if (!readyToFollow()) {
                    if (noSensorObstacles()) {
                        state = STATE_CATCH_UP;
                        catchup_count = 0;
                        break;
                    }
                    else {
                        state = STATE_AVOID_and_CATCH_UP;
                        break;
                    }
                }
                break;
            case STATE_CATCH_UP: 
                if (readyToFollow()) {
                    state = STATE_FOLLOW;
                    break;
                }
                if (!noSensorObstacles()) {
                    state = STATE_AVOID_and_CATCH_UP;
                }
                if (catchup_count++ > 3) { //never execute catch-up state more than 3x
                    state = STATE_STOPPED;
                }

                break;
            case STATE_AVOID_and_CATCH_UP:
                if (readyToFollow()) {
                    state = STATE_FOLLOW;
                }
                break;
            default:
                super.chooseNextState();
        }
       // Debug.fine("State " + STATE_NAMES[state]);
     }

     /**
      * Runs the appropriate state.
      */
     protected void runState() {
        switch (state) {
            case STATE_FOLLOW:
                doFollow();
                break;
            case STATE_CATCH_UP:
                doCatchup();
                break;
            case STATE_AVOID_and_CATCH_UP:
                doAvoidAndCatchUp();
                break;
            default:
                super.runState();
        }
     }


     /**
      * Checks if any of this TrackBot's IR sensors are beaconing with any of a potential followee's.
      *
      * NOTE: must always be called AFTER a call to readyToFollow() so that any fore/aft intersections are taken care of first.
      * @return true if one or more of this TrackBot's sensors are beaconing with one or more of another's
      *
      */
     protected boolean otherTrackBotInRange() {
         for (int i = 0; i < NUM_OTHER_TRACKBOTS; i++) {
             if (otherTrackBotIDs[i] == 0) { // No beacon from this TrackBot
                 continue;
             }
             if (
                    beaconState[i][MY_FORE_STAR] != 0
                 || beaconState[i][MY_FORE_PORT] != 0
                 || beaconState[i][MY_PORT_FORE] != SENSORS_NONE
                 || beaconState[i][MY_PORT_AFT]  != SENSORS_NONE
                 || beaconState[i][MY_AFT_STAR]  != SENSORS_NONE
                 || beaconState[i][MY_AFT_PORT]  != SENSORS_NONE
                 || beaconState[i][MY_STAR_AFT]  != SENSORS_NONE
                 || beaconState[i][MY_STAR_FORE] != SENSORS_NONE
                )
             {
                followee = i;
                return true;
             }
         }
         return false;
     }

     /**
      * Checks if this TrackBot's fore sensors are intersecting any other TrackBot's
      * aft sensors for the purpose of determining if this TrackBot can try to
      * start following the other.
      *
      * @return true if either of this TrackBot's fore sensors are intersecting
      *         one or more of the other's aft sensors
      */
     protected boolean readyToFollow() {
         for (int i = 0; i < NUM_OTHER_TRACKBOTS; i++) { // Scan all possible TrackBots for beacon signals
             if (otherTrackBotIDs[i] == 0) { // No beacon from this TrackBot
                 continue;
             }
             if (   (beaconState[i][MY_FORE_STAR] & OTHER_AFT_STAR) == OTHER_AFT_STAR // this TrackBot's fore star intersects other's aft star
                 || (beaconState[i][MY_FORE_STAR] & OTHER_AFT_PORT) == OTHER_AFT_PORT // this TrackBot's fore star intersects other's aft port
                 || (beaconState[i][MY_FORE_PORT] & OTHER_AFT_STAR) == OTHER_AFT_STAR // this TrackBot's fore port intersects other's aft star
                 || (beaconState[i][MY_FORE_PORT] & OTHER_AFT_PORT) == OTHER_AFT_PORT // this TrackBot's fore port intersects other's aft port
                )
             {
                followee = i;
                return true;
             }

         }
         return false;
     }


    /**
      * Attempts to swing the TrackBot in the appropriate manner so that it lines itself
      * up behind the TrackBot being followed. TrackBot must be in a follow-ready
      * state (i.e., fore sensor(s) beaconing with followee's aft sensor(s).
      *
      * This method assumes that the followee's body is not in range and
      * therefore there is no immediate danger of crashing into it. (Therefore,
      * sensor states are not examined here.)
      *
      * @return the appropriate direction to "swing;" -1 if unable to compute "swing"
      */
     protected int swingAround() {

         // SIDE SENSORS

         // Star fore intersected aft star and fore star intersected aft star
         if ((beaconState[followee][MY_STAR_FORE] & OTHER_AFT_PORT) == 0 &&
             (beaconState[followee][MY_STAR_FORE] & OTHER_AFT_STAR) == OTHER_AFT_STAR &&
             (beaconState[followee][MY_STAR_AFT]  & OTHER_AFT_PORT) == 0 &&
             (beaconState[followee][MY_STAR_AFT]  & OTHER_AFT_STAR) == 0 
             &&
             (beaconState[followee][MY_FORE_STAR] & OTHER_AFT_PORT) == 0 &&
             (beaconState[followee][MY_FORE_STAR] & OTHER_AFT_STAR) == OTHER_AFT_STAR &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_PORT) == 0 &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_STAR) == 0)
         {
            Debug.fine("case 0");
            return DIR_TURN_RIGHT;
         }



         // FORE STAR BIAS

         // Fore star intersected both aft and fore port intersected aft port
         if ((beaconState[followee][MY_FORE_STAR] & OTHER_AFT_PORT) == OTHER_AFT_PORT &&
             (beaconState[followee][MY_FORE_STAR] & OTHER_AFT_STAR) == OTHER_AFT_STAR &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_PORT) == OTHER_AFT_PORT &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_STAR) == 0)
         {
             Debug.fine("case 1");
             return DIR_VEER_FORWARD_RIGHT;
         }

         // Fore star has intersected other's aft port and aft star
         if ((beaconState[followee][MY_FORE_STAR] & OTHER_AFT_PORT) == OTHER_AFT_PORT &&
             (beaconState[followee][MY_FORE_STAR] & OTHER_AFT_STAR) == OTHER_AFT_STAR &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_PORT) == 0 &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_STAR) == 0)
         {
             Debug.fine("case 2");
             return DIR_TURN_RIGHT;
             //return DIR_VEER_FORWARD_RIGHT;
         }

         // Fore star has intersected other's aft port
         if ((beaconState[followee][MY_FORE_STAR] & OTHER_AFT_PORT) == OTHER_AFT_PORT &&
             (beaconState[followee][MY_FORE_STAR] & OTHER_AFT_STAR) == 0 &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_PORT) == 0 &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_STAR) == 0 &&
             (beaconState[followee][MY_STAR_FORE] & OTHER_AFT_PORT) == 0 )
         {
             Debug.fine("case 3");
             return DIR_TURN_RIGHT;
         }

         // Fore star has intersected other's aft star
         if ((beaconState[followee][MY_FORE_STAR] & OTHER_AFT_PORT) == 0 &&
             (beaconState[followee][MY_FORE_STAR] & OTHER_AFT_STAR) == OTHER_AFT_STAR &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_PORT) == 0 &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_STAR) == 0 &&
             (beaconState[followee][MY_STAR_FORE] & OTHER_AFT_PORT) == 0 )
         {
             switch(lastDir) {
                 case DIR_VEER_FORWARD_LEFT:
                 //case DIR_TURN_LEFT:
                 case DIR_FORWARD:
                     Debug.fine("case 4");
                     return DIR_FORWARD;
                 default:
                     Debug.fine("case 5");
                     return DIR_TURN_RIGHT;
             }
         }

         // FORE PORT BIAS

         // Fore port intersected both aft and fore star intersected aft star
         if ((beaconState[followee][MY_FORE_STAR] & OTHER_AFT_PORT) == 0 &&
             (beaconState[followee][MY_FORE_STAR] & OTHER_AFT_STAR) == OTHER_AFT_STAR &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_PORT) == OTHER_AFT_PORT &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_STAR) == OTHER_AFT_STAR)
         {
             Debug.fine("case 6");
             return DIR_VEER_FORWARD_LEFT;
         }

         // Fore port intersected other's aft port and aft star
         if ((beaconState[followee][MY_FORE_STAR] & OTHER_AFT_PORT) == 0 &&
             (beaconState[followee][MY_FORE_STAR] & OTHER_AFT_STAR) == 0 &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_PORT) == OTHER_AFT_PORT &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_STAR) == OTHER_AFT_STAR)
         {
             Debug.fine("case 7");
             // return DIR_TURN_LEFT;
             return DIR_VEER_FORWARD_LEFT;
         }

         // Fore port intersected other's aft star
         if ((beaconState[followee][MY_FORE_STAR] & OTHER_AFT_PORT) == 0 &&
             (beaconState[followee][MY_FORE_STAR] & OTHER_AFT_STAR) == 0 &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_PORT) == 0 &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_STAR) == OTHER_AFT_STAR)
         {
             Debug.fine("case 8");
             return DIR_TURN_LEFT;
         }

         // Fore port intersected other's aft port
         if ((beaconState[followee][MY_FORE_STAR] & OTHER_AFT_PORT) == 0 &&
             (beaconState[followee][MY_FORE_STAR] & OTHER_AFT_STAR) == 0 &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_PORT) == OTHER_AFT_PORT &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_STAR) == 0)
         {
             Debug.fine("case 9");
             //return DIR_TURN_LEFT;
             return DIR_FORWARD;
         }

         // Ideal: Fore star intersected other's aft star and fore port intersected other's aft port
         // NOTE: this case must be last, because all 4 sensors (both fore of follower and both aft of
         //       followee) may be intersecting all of each other
         if ((beaconState[followee][MY_FORE_STAR] & OTHER_AFT_STAR) == OTHER_AFT_STAR &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_PORT) == OTHER_AFT_PORT)
         {
             Debug.fine("case 10");
             return DIR_FORWARD;
         }       

         Debug.fine("swing not determined...");
         return -1;
     }

     /**
      * Behavior for the FOLLOW state.
      */
     protected void doFollow() {

        catchUpState = beaconState[followee]; // record last known sensor intersection(s) for possible catch-up later
        
        int dir = DIR_STOP, speed = -1;

        switch ((powerNodeState >> 12) & 0x000f | (sensorNodeState >> 4) & 0x00f0)
        {
            case SENSOR_FORE_STARBOARD:
                if (readyToFollow()) {
                    dir = DIR_STOP;
                    break;
                }
                else {
                    dir = DIR_TURN_LEFT;
                    break;
                }
            case SENSOR_FORE_PORT:
                if (readyToFollow()) {
                    dir = DIR_STOP;
                    break;
                }
                else {
                    dir = DIR_TURN_RIGHT;
                    break;
                }
            case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT:
                Debug.fine("case 13");
                dir = DIR_STOP; // TrackBot being followed is directly in front: we've acheived our goal
                break;

            case SENSOR_FORE_PORT + SENSOR_PORT_FORE:
                if (readyToFollow()) {
                    dir = DIR_TURN_RIGHT;
                    break;
                }
                Debug.fine("case 14");
                //dir = DIR_VEER_BACKWARD_LEFT;
                dir = DIR_VEER_BACKWARD_RIGHT;
                break;
                
            case SENSOR_FORE_STARBOARD + SENSOR_STARBOARD_FORE:
                if (readyToFollow()) {
                    dir = DIR_TURN_LEFT;
                    break;
                }
                Debug.fine("case 15");
                //dir = DIR_VEER_BACKWARD_RIGHT;
                dir = DIR_VEER_BACKWARD_LEFT;
                break;

            // The following cases usually happen when the follower nears a wall or barrier while attempting to follow the followee

            case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_STARBOARD_FORE:
            case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_STARBOARD_FORE + SENSOR_STARBOARD_AFT:
            case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_STARBOARD_FORE + SENSOR_STARBOARD_AFT + SENSOR_AFT_PORT:
            case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_STARBOARD_FORE + SENSOR_STARBOARD_AFT + SENSOR_AFT_PORT + SENSOR_AFT_STARBOARD:
                dir = DIR_TURN_LEFT;
                break;

            case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_PORT_FORE:
            case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_PORT_FORE + SENSOR_PORT_AFT:
            case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_PORT_FORE + SENSOR_PORT_AFT + SENSOR_AFT_STARBOARD:
            case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_PORT_FORE + SENSOR_PORT_AFT + SENSOR_AFT_STARBOARD + SENSOR_AFT_PORT:
                dir = DIR_TURN_RIGHT;
                break;

            default:
                if ((dir = swingAround()) < 0) {
                    Debug.fine("case 16");
                    //dir = DIR_STOP;
                    dir = DIR_FORWARD;
                    break;
                }


                if (dir == DIR_FORWARD && (lastDir == DIR_VEER_FORWARD_LEFT || lastDir == DIR_VEER_FORWARD_RIGHT
                                            || lastDir == DIR_TURN_LEFT || lastDir == DIR_TURN_RIGHT))
                { // need to catch up
                    speed = Motors.SPEED_FAST;
                }
        }//switch

        if (speed < 0) { // Default speed
            goDirection(dir);
        }
        else {
            goDirection(dir, speed);
        }
        
        Debug.fine("dir: " + DIR_NAMES[dir]);
        lastDir = dir;
     
     }//doFollow


     /**
      * Behavior for the "avoid and catch up" state.
      */
     protected void doAvoidAndCatchUp() {
        int dir = DIR_STOP;

        /*
        if ( (((catchUpState[MY_FORE_STAR] & OTHER_FORE_STAR) == OTHER_FORE_STAR) ||
              ((catchUpState[MY_FORE_STAR] & OTHER_FORE_PORT) == OTHER_FORE_PORT))
              && (catchUpState[MY_FORE_PORT] & OTHER_FORE_STAR) == 0
              && (catchUpState[MY_FORE_PORT] & OTHER_FORE_PORT) == 0)
            {
                switch ((powerNodeState >> 12) & 0x000f | (sensorNodeState >> 4) & 0x00f0)
                {
                    case SENSOR_FORE_STARBOARD:
                    case SENSOR_FORE_PORT:
                    case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT:
                        dir = DIR_TURN_RIGHT;
                        break;

                    case SENSOR_STARBOARD_FORE + SENSOR_STARBOARD_AFT:
                    case SENSOR_STARBOARD_FORE + SENSOR_STARBOARD_AFT + SENSOR_AFT_STARBOARD:
                    case SENSOR_PORT_FORE + SENSOR_PORT_AFT:
                    case SENSOR_PORT_FORE + SENSOR_PORT_AFT + SENSOR_AFT_PORT:
                        dir = DIR_FORWARD;
                        break;

                    case SENSORS_NONE:
                        dir = DIR_FORWARD;
                        break;
                }//switch
            }//if

        else if (   (catchUpState[MY_FORE_STAR] & OTHER_FORE_STAR) == 0
                 && (catchUpState[MY_FORE_STAR] & OTHER_FORE_PORT) == 0
                 && (((catchUpState[MY_FORE_PORT] & OTHER_FORE_STAR) == OTHER_FORE_STAR) ||
                     ((catchUpState[MY_FORE_PORT] & OTHER_FORE_PORT) == OTHER_FORE_PORT)))
            {
                switch ((powerNodeState >> 12) & 0x000f | (sensorNodeState >> 4) & 0x00f0)
                {
                    case SENSOR_FORE_STARBOARD:
                    case SENSOR_FORE_PORT:
                    case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT:
                        dir = DIR_TURN_LEFT;
                        break;

                    case SENSOR_STARBOARD_FORE + SENSOR_STARBOARD_AFT:
                    case SENSOR_STARBOARD_FORE + SENSOR_STARBOARD_AFT + SENSOR_AFT_STARBOARD:
                    case SENSOR_PORT_FORE + SENSOR_PORT_AFT:
                    case SENSOR_PORT_FORE + SENSOR_PORT_AFT + SENSOR_AFT_PORT:
                        dir = DIR_FORWARD;
                        break;
                }
            }
        */
        
        switch ((powerNodeState >> 12) & 0x000f | (sensorNodeState >> 4) & 0x00f0)
        {
            case SENSOR_FORE_STARBOARD:
                dir = DIR_TURN_LEFT;
                break;
            case SENSOR_FORE_PORT:
                dir = DIR_TURN_RIGHT;
                break;
            case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT:
                if (lastDir == DIR_TURN_LEFT || lastDir == DIR_VEER_FORWARD_LEFT) {
                    dir = DIR_TURN_LEFT;
                    break;
                }
                if (lastDir == DIR_TURN_RIGHT || lastDir == DIR_VEER_FORWARD_RIGHT) {
                    dir = DIR_TURN_RIGHT;
                    break;
                }
                break;

            case SENSOR_FORE_PORT + SENSOR_PORT_FORE:
            case SENSOR_FORE_PORT + SENSOR_PORT_FORE + SENSOR_PORT_AFT:
            case SENSOR_FORE_PORT + SENSOR_PORT_FORE + SENSOR_PORT_AFT + SENSOR_AFT_PORT:
                dir = DIR_TURN_RIGHT;
                break;

            case SENSOR_FORE_STARBOARD + SENSOR_STARBOARD_FORE:
            case SENSOR_FORE_STARBOARD + SENSOR_STARBOARD_FORE + SENSOR_STARBOARD_AFT:
            case SENSOR_FORE_STARBOARD + SENSOR_STARBOARD_FORE + SENSOR_STARBOARD_AFT + SENSOR_AFT_STARBOARD:
                dir = DIR_TURN_LEFT;
                break;

            case SENSOR_PORT_FORE:
                if (lastDir == DIR_TURN_LEFT) {
                    dir = DIR_VEER_FORWARD_RIGHT;
                }
                else {
                    dir = DIR_TURN_RIGHT;
                }
                break;
            case SENSOR_PORT_AFT:
                dir = DIR_TURN_LEFT;
                break;

            case SENSOR_STARBOARD_FORE:
                if (lastDir == DIR_TURN_RIGHT) {
                    dir = DIR_VEER_FORWARD_LEFT;
                }
                else {
                    dir = DIR_TURN_LEFT;
                }
                break;
            case SENSOR_STARBOARD_AFT:
                dir = DIR_TURN_RIGHT;
                break;

            case SENSOR_FORE_PORT + SENSOR_AFT_PORT:
                dir = DIR_TURN_RIGHT;
                break;

            case SENSOR_STARBOARD_FORE + SENSOR_STARBOARD_AFT:
            case SENSOR_STARBOARD_FORE + SENSOR_STARBOARD_AFT + SENSOR_AFT_STARBOARD:
            case SENSOR_PORT_FORE + SENSOR_PORT_AFT:
            case SENSOR_PORT_FORE + SENSOR_PORT_AFT + SENSOR_AFT_PORT:
                dir = DIR_FORWARD;
                break;

            case SENSORS_NONE:
                dir = DIR_FORWARD;
                break;
               
        }
        
        if (dir == DIR_FORWARD) {
            goDirection(dir, Motors.SPEED_FAST);
        }
        else {
            goDirection(dir);
        }

        lastDir = dir;
     }
     

     /**
      * Behavior for the "catch up" state.
      */
     protected void doCatchup() {
         //int dir = DIR_STOP;
         /*
         int speed = Motors.SPEED_FAST;

         if (catchUpState[MY_AFT_STAR] != 0) {
         
                if ((catchUpState[MY_AFT_STAR] & OTHER_AFT_STAR) == OTHER_AFT_STAR) {
                    if (noSensorObstacles()) {
                        goDirection(DIR_TURN_LEFT, speed);
                    }
                }

                if ((catchUpState[MY_AFT_STAR] & OTHER_AFT_PORT) == OTHER_AFT_PORT) {
                    if (noSensorObstacles()) {
                        goDirection(DIR_TURN_LEFT, speed);
                    }
                }

                if ((catchUpState[MY_AFT_STAR] & OTHER_FORE_STAR) == OTHER_FORE_STAR) {
                    if (noSensorObstacles()) {
                        goDirection(DIR_VEER_BACKWARD_RIGHT, speed);
                    }
                }

                if ((catchUpState[MY_AFT_STAR] & OTHER_FORE_PORT) == OTHER_FORE_PORT) {
                    if (noSensorObstacles()) {
                        goDirection(DIR_VEER_BACKWARD_RIGHT, speed);
                    }
                }

                if ((catchUpState[MY_AFT_STAR] & OTHER_STAR_AFT) == OTHER_STAR_AFT) {
                    if (noSensorObstacles()) {
                        goDirection(DIR_VEER_BACKWARD_LEFT, speed);
                    }
                }

                if ((catchUpState[MY_AFT_STAR] & OTHER_PORT_AFT) == OTHER_PORT_AFT) {
                    if (noSensorObstacles()) {
                        goDirection(DIR_VEER_BACKWARD_LEFT, speed);
                    }
                }

                if ((catchUpState[MY_AFT_STAR] & OTHER_STAR_FORE) == OTHER_STAR_FORE) {
                    if (noSensorObstacles()) {
                        goDirection(DIR_VEER_BACKWARD_RIGHT, speed);
                    }
                }

                if ((catchUpState[MY_AFT_STAR] & OTHER_PORT_FORE) == OTHER_PORT_FORE) {
                    if (noSensorObstacles()) {
                        goDirection(DIR_BACKWARD, speed);
                    }
                }
          
         }

         if (catchUpState[MY_AFT_PORT] != 0) {
             
         }

         if (catchUpState[MY_FORE_STAR] != 0) {

            if ((catchUpState[MY_FORE_STAR] & OTHER_AFT_STAR) == OTHER_AFT_STAR) {
                    if (noSensorObstacles()) {
                        goDirection(DIR_TURN_RIGHT, speed);
                        Debug.fine("catchup case 0");
                    }
                }

                if ((catchUpState[MY_FORE_STAR] & OTHER_AFT_PORT) == OTHER_AFT_PORT) {
                    if (noSensorObstacles()) {
                        goDirection(DIR_TURN_RIGHT, speed);
                        Debug.fine("catchup case 1");
                    }
                }

                if ((catchUpState[MY_FORE_STAR] & OTHER_FORE_STAR) == OTHER_FORE_STAR) {
                    if (noSensorObstacles()) {
                        goDirection(DIR_VEER_FORWARD_RIGHT, speed);
                        Debug.fine("catchup case 2");
                    }
                }

                if ((catchUpState[MY_FORE_STAR] & OTHER_FORE_PORT) == OTHER_FORE_PORT) {
                    if (noSensorObstacles()) {
                        goDirection(DIR_TURN_RIGHT, speed);
                        goDirection(DIR_FORWARD, speed);
                        Debug.fine("catchup case 3");
                    }
                }

                if ((catchUpState[MY_FORE_STAR] & OTHER_STAR_AFT) == OTHER_STAR_AFT) {
                    if (noSensorObstacles()) {
                        goDirection(DIR_TURN_LEFT, speed);
                        goDirection(DIR_FORWARD, speed);
                        Debug.fine("catchup case 4");
                    }
                }

                if ((catchUpState[MY_FORE_STAR] & OTHER_PORT_AFT) == OTHER_PORT_AFT) {
                    if (noSensorObstacles()) {
                        goDirection(DIR_TURN_RIGHT, speed);
                        goDirection(DIR_TURN_RIGHT, speed);
                        goDirection(DIR_FORWARD, speed);
                        Debug.fine("catchup case 5");
                    }
                }

                if ((catchUpState[MY_FORE_STAR] & OTHER_STAR_FORE) == OTHER_STAR_FORE) {
                    if (noSensorObstacles()) {
                        goDirection(DIR_TURN_LEFT, speed);
                        goDirection(DIR_FORWARD, speed);
                        Debug.fine("catchup case 6");
                    }
                }

                if ((catchUpState[MY_FORE_STAR] & OTHER_PORT_FORE) == OTHER_PORT_FORE) {
                    if (noSensorObstacles()) {
                        goDirection(DIR_TURN_RIGHT, speed);
                        goDirection(DIR_TURN_RIGHT, speed);
                        goDirection(DIR_FORWARD, speed);
                        Debug.fine("catchup case 7");
                    }
                }
         }

         if (catchUpState[MY_FORE_PORT] != 0) {

         }

         if (catchUpState[MY_STAR_AFT] != 0)  {

         }

         if (catchUpState[MY_PORT_AFT] != 0) {

         }

         if (catchUpState[MY_STAR_FORE] != 0) {

         }

         if (catchUpState[MY_PORT_FORE] != 0) {
             
         }

         goDirection(DIR_FORWARD, speed);

         */
         switch(lastDir) {
             case DIR_FORWARD:
             case DIR_VEER_BACKWARD_RIGHT:
             case DIR_VEER_BACKWARD_LEFT:
                    goDirection(DIR_FORWARD, Motors.SPEED_FAST);
                 break;
             case DIR_TURN_LEFT:
             case DIR_VEER_FORWARD_LEFT:
                    goDirection(DIR_TURN_LEFT, Motors.SPEED_FAST);
                    goDirection(DIR_FORWARD, Motors.SPEED_FAST);
                    break;
             case DIR_TURN_RIGHT:
             case DIR_VEER_FORWARD_RIGHT:
                    goDirection(DIR_TURN_RIGHT, Motors.SPEED_FAST);
                    goDirection(DIR_FORWARD, Motors.SPEED_FAST);
                    break;
             default:
                 goDirection(DIR_STOP);
         }
         
     }


     // Convenience methods for some of the "edge" cases

     /**
      *
      * @return whether the followee TrackBot is on my Starboard (right-hand) side and facing the opposite direction
      */
     protected boolean isFolloweeOnStarboardsideAndOppositeDir() {
         return (((beaconState[followee][MY_STAR_FORE] & OTHER_STAR_FORE) == OTHER_STAR_FORE) ||
                 ((beaconState[followee][MY_STAR_FORE] & OTHER_STAR_AFT)  == OTHER_STAR_AFT));
     }

     /**
      *
      * @return whether the followee TrackBot is on my Starboard (right-hand) side and facing the same direction
      */
     protected boolean isFolloweeOnStarboardsideAndSameDir() {
         return (((beaconState[followee][MY_STAR_FORE] & OTHER_PORT_FORE) == OTHER_STAR_FORE) ||
                 ((beaconState[followee][MY_STAR_FORE] & OTHER_PORT_AFT)  == OTHER_STAR_AFT));
     }

     /**
      *
      * @return whether the followee TrackBot is on my Port (left-hand) side and facing the opposite direction
      */
     protected boolean isFolloweeOnPortsideAndOppositeDir() {
         return (((beaconState[followee][MY_PORT_FORE] & OTHER_PORT_FORE) == OTHER_STAR_FORE) ||
                 ((beaconState[followee][MY_PORT_FORE] & OTHER_PORT_AFT)  == OTHER_STAR_AFT));
     }

     /**
      *
      * @return whether the followee TrackBot is on my Port (left-hand) side and facing the same direction
      */
     protected boolean isFolloweeOnPortsideAndSameDir() {
         return (((beaconState[followee][MY_PORT_FORE] & OTHER_STAR_FORE) == OTHER_STAR_FORE) ||
                 ((beaconState[followee][MY_PORT_FORE] & OTHER_STAR_AFT)  == OTHER_STAR_AFT));
     }


     //

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
     /*
    public void allStates(int powerNodeState, int sensorNodeState, int[][] beaconState, int trackBotID) {
        this.powerNodeState = powerNodeState;
        this.sensorNodeState = sensorNodeState;
        this.beaconState = beaconState;
        this.trackBotID = trackBotID;
        checkBeacons();
        stateMachine();
    }//allStates
    */

    /**
     * Check if a beacon was detected from another TrackBot on all sensors and
     * print out status to Debug console if true.
     *
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
}
