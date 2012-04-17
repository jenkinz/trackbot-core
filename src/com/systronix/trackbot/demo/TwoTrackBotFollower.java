/* Date: Mar 12, 2009
 *
 * (c) 2008 Systronix Inc.  All Rights reserved.
 * 939 Edison Street, Salt Lake City, UT, USA  84111
 * http://www.systronix.com/
 */

package com.systronix.trackbot.demo;

import com.systronix.io.Debug;
import com.systronix.trackbot.Motors;
import com.systronix.trackbot.RobotIO;

/**
 * This file contains methods to implement a Follower behavior for TrackBot based
 * on IR sensor beaconing. Using this behavior, TrackBot stores "beacon data," found
 * in the {@link Beaconer} class,to access information on other TrackBots' 
 * positions in the world.
 *
 * Using the beacon data stored in {@link beaconState}, a TrackBot can attempt to 
 * follow another TrackBot. This particular demo is mean for two TrackBots, with
 * one acting as a simple Wanderer (the {@link followee}, and the other trying to
 * follow (the follower, represented by this object).
 *
 * @version 1.0
 * @author Brian Jenkins
 */
public class TwoTrackBotFollower extends Beaconer {

    /**
     * ID of the TrackBot we're currently trying to follow
     */
    protected int followee = -1;

    /**
     * Holds the last known beacon state on the current followee.
     */
    protected int[] catchUpState = new int[8];


    // State constants for state machine

    /** The "follow" state */
    protected static final int STATE_FOLLOW = Beaconer.MAX_STATE + 1;

    /** The "catch up" state */
    protected static final int STATE_CATCH_UP = STATE_FOLLOW + 1; //4

    /** The "get ready to follow" state */
    protected static final int STATE_AVOID_and_CATCH_UP = STATE_CATCH_UP + 1; //5

    /** The "to the side of followee" state */
    protected static final int STATE_SIDE = STATE_AVOID_and_CATCH_UP + 1;

    /**
     * Holds the last possible state for objects of this class. Classes
     * extending the TwoTrackBotFollower class should start their state constants
     * at Follower.MAX_STATE + 1.
     */
    protected static final int MAX_STATE = STATE_SIDE;

    /** The current state */
    protected int state = STATE_STOPPED;

    /** The last direction */
    protected int lastDir = DIR_STOP;

    /** The maximum number of catchup attempts */
    protected int max_catchup_state = 3;

    /** The current catchup iteration */
    protected int catchup_count;


   // STATE MACHINE and supporting methods for following

   /**
    * Chooses the next state.
    */
    protected void chooseNextState() {
        // The following IF statement causes one of the TrackBots to default to wandering around without regard to the other, so that the other can try to follow it.
        if (this.trackBotID == 0) {
            super.chooseNextState();
            return;
        }

        switch (state) {
            case STATE_STOPPED:
            case STATE_RUNAWAY:
            case STATE_WANDER:
                if (followee == -1) { // Haven't found anyone to follow yet...
                    if (readyToFollow()) {
                        state = STATE_FOLLOW;
                        break;
                    }
                    else {
                        state = STATE_STOPPED;
                        break;
                    }
                }
                else {
                    if (readyToFollow(followee)) {
                        state = STATE_FOLLOW;
                        break;
                    }
                    else if (onSide()) {
                        state = STATE_SIDE;
                        break;
                    }
                    else {
                        state = STATE_STOPPED;
                        break;
                    }
                }

            case STATE_FOLLOW:
                if (!readyToFollow(followee)) {
                    if (onSide()) {
                        state = STATE_SIDE;
                        break;
                    }
                    else {
                        catchup_count = 0;
                        if (noSensorObstacles()) {
                            state = STATE_CATCH_UP;
                        }
                        else {
                            state = STATE_AVOID_and_CATCH_UP;
                        }
                    }
                }
                break;

            case STATE_SIDE:
                if (readyToFollow(followee)) {
                    state = STATE_FOLLOW;
                }
                break;

            case STATE_CATCH_UP:
                if (readyToFollow(followee)) {
                    state = STATE_FOLLOW;
                    break;
                }
                else if (onSide()) {
                    state = STATE_SIDE;
                    break;
                }
                else if (!noSensorObstacles()) {
                    catchup_count = 0;
                    state = STATE_AVOID_and_CATCH_UP;
                }

                if (catchup_count++ > 3) { //never execute catch-up state more than 3x
                    state = STATE_STOPPED;
                }
                break;

            case STATE_AVOID_and_CATCH_UP:
                if (readyToFollow(followee)) {
                    state = STATE_FOLLOW;
                    break;
                }
                else if (onSide()) {
                    state = STATE_SIDE;
                    break;
                }
                break;

            default:
                super.chooseNextState();
        }
        
       //Debug.fine("State " + STATE_NAMES[state]);
    }

    
    /**
     * Runs the appropriate state chosen by {@link chooseNextState}.
     */
    protected void runState() {
        switch (state) {
            case STATE_FOLLOW:
                doFollow();
                break;
            case STATE_SIDE:
                doSide();
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
      * Checks if this TrackBot's fore sensors are intersecting the specified TrackBot's
      * aft sensors.
      *
      * @param followee the ID of the TrackBot we're trying to follow
      * @return true if either of this TrackBot's fore sensors are intersecting
      *         one or more of the other's aft sensors
      */
     protected boolean readyToFollow(int followee) {
         if (otherTrackBotIDs[followee] == 0) { // No beacon from the followee
             return false;
         }
         return (   (beaconState[followee][MY_FORE_STAR] & OTHER_AFT_STAR) == OTHER_AFT_STAR // this TrackBot's fore star intersects other's aft star
                 || (beaconState[followee][MY_FORE_STAR] & OTHER_AFT_PORT) == OTHER_AFT_PORT // this TrackBot's fore star intersects other's aft port
                 || (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_STAR) == OTHER_AFT_STAR // this TrackBot's fore port intersects other's aft star
                 || (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_PORT) == OTHER_AFT_PORT // this TrackBot's fore port intersects other's aft port
                );
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

        ///catchUpState = beaconState[followee]; // record last known sensor intersection(s) for possible catch-up later

        int dir = DIR_STOP, speed = -1;

        // Always stop when both fore sensors detect and Followee is in range (this means we're about to run into the Followee)

        if(((powerNodeState >> 12) & 0x000c) != 0x000c && otherTrackBotInRange(followee)) // If both fore sensors are not detecting and the other TrackBot is in range, determine direction, else stop
        {
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

                case SENSORS_ALL:
                //case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT: //taken care of in first if statement above
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
                case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_STARBOARD_FORE + SENSOR_STARBOARD_AFT + SENSOR_AFT_STARBOARD:
                case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_STARBOARD_FORE + SENSOR_STARBOARD_AFT + SENSOR_AFT_PORT + SENSOR_AFT_STARBOARD:
                case SENSOR_FORE_STARBOARD + SENSOR_STARBOARD_FORE + SENSOR_AFT_STARBOARD + SENSOR_AFT_PORT:
                case SENSOR_FORE_STARBOARD + SENSOR_STARBOARD_AFT + SENSOR_AFT_STARBOARD:
                    dir = DIR_TURN_LEFT;
                    break;

                case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_PORT_FORE:
                case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_PORT_FORE + SENSOR_PORT_AFT:
                case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_PORT_FORE + SENSOR_PORT_AFT + SENSOR_AFT_STARBOARD:
                case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_PORT_FORE + SENSOR_PORT_AFT + SENSOR_AFT_PORT:
                case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_PORT_FORE + SENSOR_PORT_AFT + SENSOR_AFT_STARBOARD + SENSOR_AFT_PORT:
                case SENSOR_FORE_PORT + SENSOR_PORT_FORE + SENSOR_AFT_STARBOARD + SENSOR_AFT_PORT:
                case SENSOR_FORE_PORT + SENSOR_PORT_AFT + SENSOR_AFT_PORT:
                    dir = DIR_TURN_RIGHT;
                    break;

                case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_PORT_FORE + SENSOR_PORT_AFT + SENSOR_STARBOARD_FORE:
                case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_PORT_FORE + SENSOR_PORT_AFT + SENSOR_STARBOARD_FORE + SENSOR_STARBOARD_AFT + SENSOR_AFT_PORT:
                    dir = DIR_VEER_BACKWARD_RIGHT;
                    break;

                case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_PORT_FORE + SENSOR_PORT_AFT + SENSOR_STARBOARD_FORE + SENSOR_STARBOARD_AFT + SENSOR_AFT_STARBOARD:
                    dir = DIR_VEER_BACKWARD_LEFT;
                    break;

                case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_PORT_FORE + SENSOR_PORT_AFT + SENSOR_STARBOARD_FORE + SENSOR_STARBOARD_AFT:
                    dir = DIR_BACKWARD;
                    break;

                case SENSOR_FORE_PORT + SENSOR_AFT_PORT + SENSOR_AFT_STARBOARD:
                    dir = DIR_VEER_FORWARD_RIGHT;
                    break;

                case SENSOR_FORE_STARBOARD + SENSOR_AFT_PORT + SENSOR_AFT_STARBOARD:
                    dir = DIR_VEER_FORWARD_LEFT;
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
        }//if
        
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
      * Behavior for the SIDE state.
      *
      * The primary objective here is to get the follower in a "ready to follow state;" that is,
      * the follower's fore sensor(s) should be beaconing with the Followee's aft sensor(s).
      */
     protected void doSide() {

         int dir = DIR_STOP;

         // BOTH FORE

         // wait until a bias occurs - that is, the Followee has moved so that only one sensor is in range so we can tell which direction it's going

         /*
         if (   (beaconState[followee][MY_FORE_STAR] & OTHER_PORT_AFT) == OTHER_PORT_AFT && (beaconState[followee][MY_FORE_PORT] & OTHER_PORT_FORE) == OTHER_PORT_FORE
             || (beaconState[followee][MY_FORE_STAR] & OTHER_PORT_AFT) == OTHER_PORT_AFT && (beaconState[followee][MY_FORE_STAR] & OTHER_PORT_FORE) == OTHER_PORT_FORE && (beaconState[followee][MY_FORE_PORT] & OTHER_PORT_FORE) == OTHER_PORT_FORE
             || (beaconState[followee][MY_FORE_STAR] & OTHER_PORT_AFT) == OTHER_PORT_AFT && (beaconState[followee][MY_FORE_STAR] & OTHER_PORT_FORE) == OTHER_PORT_FORE && (beaconState[followee][MY_FORE_PORT] & OTHER_PORT_FORE) == OTHER_PORT_FORE && (beaconState[followee][MY_FORE_PORT] & OTHER_PORT_AFT) == OTHER_PORT_AFT
             )
         {

         }
          */

         // FORE STAR

         if (   (beaconState[followee][MY_FORE_STAR] & OTHER_PORT_FORE) == OTHER_PORT_FORE
             || (beaconState[followee][MY_FORE_STAR] & OTHER_PORT_AFT)  == OTHER_PORT_AFT
             || (beaconState[followee][MY_FORE_STAR] & OTHER_STAR_FORE) == OTHER_STAR_FORE
             || (beaconState[followee][MY_FORE_STAR] & OTHER_STAR_AFT)  == OTHER_STAR_AFT)
         {
            dir = DIR_TURN_RIGHT;
         }
         
         // FORE PORT

         if (   (beaconState[followee][MY_FORE_PORT] & OTHER_PORT_FORE) == OTHER_PORT_FORE
             || (beaconState[followee][MY_FORE_PORT] & OTHER_PORT_AFT)  == OTHER_PORT_AFT
             || (beaconState[followee][MY_FORE_PORT] & OTHER_STAR_FORE) == OTHER_STAR_FORE
             || (beaconState[followee][MY_FORE_PORT] & OTHER_STAR_AFT)  == OTHER_STAR_AFT)
         {
             dir = DIR_TURN_LEFT;
         }

         goDirection(dir);
     }

     /**
      * Behavior for the "avoid and catch up" state.
      */
     protected void doAvoidAndCatchUp() {

        int dir = DIR_STOP;

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
                else {
                    dir = DIR_VEER_BACKWARD_RIGHT;
                }
                break;

            case SENSOR_FORE_PORT + SENSOR_AFT_PORT:
            case SENSOR_FORE_PORT + SENSOR_PORT_FORE:
            case SENSOR_FORE_PORT + SENSOR_PORT_FORE + SENSOR_PORT_AFT:
            case SENSOR_FORE_PORT + SENSOR_PORT_FORE + SENSOR_PORT_AFT + SENSOR_AFT_PORT:
                dir = DIR_TURN_RIGHT;
                break;

            case SENSOR_FORE_STARBOARD + SENSOR_AFT_STARBOARD:
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

            case SENSOR_AFT_PORT:
                dir = DIR_VEER_FORWARD_LEFT;
                break;

            case SENSOR_AFT_STARBOARD:
                dir = DIR_VEER_FORWARD_RIGHT;
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

            case SENSOR_STARBOARD_FORE + SENSOR_STARBOARD_AFT:
            case SENSOR_STARBOARD_FORE + SENSOR_STARBOARD_AFT + SENSOR_AFT_STARBOARD:
            case SENSOR_PORT_FORE + SENSOR_PORT_AFT:
            case SENSOR_PORT_FORE + SENSOR_PORT_AFT + SENSOR_AFT_PORT:
                dir = DIR_FORWARD;
                break;

            case SENSOR_AFT_PORT + SENSOR_PORT_AFT:
            case SENSOR_AFT_STARBOARD + SENSOR_AFT_PORT + SENSOR_PORT_AFT:
            case SENSOR_AFT_STARBOARD + SENSOR_AFT_PORT + SENSOR_PORT_FORE:
            case SENSOR_AFT_STARBOARD + SENSOR_AFT_PORT + SENSOR_PORT_AFT + SENSOR_PORT_FORE:
                dir = DIR_VEER_FORWARD_RIGHT;
                break;

            case SENSOR_FORE_PORT + SENSOR_FORE_STARBOARD + SENSOR_STARBOARD_FORE:
            case SENSOR_FORE_PORT + SENSOR_FORE_STARBOARD + SENSOR_STARBOARD_AFT:
            case SENSOR_FORE_PORT + SENSOR_FORE_STARBOARD + SENSOR_STARBOARD_FORE + SENSOR_STARBOARD_AFT:
            case SENSOR_FORE_PORT + SENSOR_FORE_STARBOARD + SENSOR_STARBOARD_FORE + SENSOR_STARBOARD_AFT + SENSOR_AFT_STARBOARD:
            case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_AFT_STARBOARD:
            case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_AFT_STARBOARD + SENSOR_STARBOARD_FORE:
            case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_AFT_STARBOARD + SENSOR_AFT_PORT + SENSOR_STARBOARD_FORE + SENSOR_STARBOARD_AFT:
            case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_AFT_STARBOARD + SENSOR_AFT_PORT + SENSOR_STARBOARD_AFT:
                dir = DIR_TURN_LEFT;
                break;

            case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_AFT_PORT:
            case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_AFT_PORT + SENSOR_PORT_FORE:
            case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_AFT_PORT + SENSOR_PORT_FORE + SENSOR_PORT_AFT:
            case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_AFT_PORT + SENSOR_AFT_STARBOARD + SENSOR_PORT_FORE + SENSOR_PORT_AFT:
            case SENSOR_FORE_STARBOARD + SENSOR_FORE_PORT + SENSOR_AFT_PORT + SENSOR_AFT_STARBOARD + SENSOR_PORT_AFT:
            case SENSOR_FORE_PORT + SENSOR_FORE_STARBOARD + SENSOR_PORT_FORE:
            case SENSOR_FORE_PORT + SENSOR_FORE_STARBOARD + SENSOR_PORT_AFT:
            case SENSOR_FORE_PORT + SENSOR_FORE_STARBOARD + SENSOR_PORT_FORE + SENSOR_PORT_AFT:
            case SENSOR_FORE_PORT + SENSOR_FORE_STARBOARD + SENSOR_PORT_AFT + SENSOR_AFT_PORT:
                dir = DIR_TURN_RIGHT;
                break;

            case SENSOR_FORE_STARBOARD + SENSOR_STARBOARD_AFT + SENSOR_AFT_PORT + SENSOR_AFT_STARBOARD:
            case SENSOR_FORE_STARBOARD + SENSOR_STARBOARD_FORE + SENSOR_STARBOARD_AFT + SENSOR_AFT_PORT + SENSOR_AFT_STARBOARD:
                dir = DIR_VEER_FORWARD_LEFT;
                break;

            case SENSOR_FORE_PORT + SENSOR_PORT_AFT + SENSOR_AFT_PORT + SENSOR_AFT_STARBOARD:
            case SENSOR_FORE_PORT + SENSOR_PORT_FORE + SENSOR_PORT_AFT + SENSOR_AFT_PORT + SENSOR_AFT_STARBOARD:
                dir = DIR_VEER_FORWARD_RIGHT;
                break;

            case SENSORS_NONE:
                dir = DIR_FORWARD;
                break;

            default:
                dir = DIR_STOP;
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

    
    // Other methods

    /**
     * Creates this object.
     *
     * @throws java.lang.Exception
     */
    public TwoTrackBotFollower() throws Exception {
        super();
    }

    /**
     * Creates this object using the given I/O connection.
     *
     * @throws java.lang.Exception
     */
    public TwoTrackBotFollower(RobotIO robotIO) throws Exception {
        super(robotIO);
    }
}
