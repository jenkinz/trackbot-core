/* Date: May 2, 2009
 *
 * (c) 2008 Systronix Inc.  All Rights reserved.
 * 939 Edison Street, Salt Lake City, UT, USA  84111
 * http://www.systronix.com/
 *
 * brian.jenkins@sun.com
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
 * @version 2.0
 * @author Brian Jenkins - brian.jenkins@sun.com
 */
public class TrackBotFollower extends Beaconer {

    /**
     * ID of the TrackBot we're currently trying to follow
     */
    protected int followee = -1;

    // States

    /** The "follow" state */
    protected static final int STATE_FOLLOW = Beaconer.MAX_STATE + 1;

    /** The "side" state */
    protected static final int STATE_SIDE = Beaconer.MAX_STATE + 2;

    /**
     * Holds the last possible state for objects of this class. Classes
     * extending the TwoTrackBotFollower class should start their state constants
     * at Follower.MAX_STATE + 1.
     */
    protected static final int MAX_STATE = STATE_SIDE;

    /** The last direction */
    protected int lastDir = DIR_STOP;

    /** The last known beacon state where something was beaconing */
    protected int lastKnownBeaconingState[][];

    /** Flag to determine whether to "catch up" or not */
    protected boolean catch_up = false;
    

    /**
     * Creates this object.
     *
     * @throws java.lang.Exception
     */
    public TrackBotFollower() throws Exception {
        super();
        state = STATE_STOPPED;
    }

    /**
     * Creates this object using the given I/O connection.
     *
     * @param robotIO the robot I/O object-connection
     * @throws java.lang.Exception
     */
    public TrackBotFollower(RobotIO robotIO) throws Exception {
        super(robotIO);
        state = STATE_STOPPED;
    }


    // Current data, modules, and state

    protected void runState() {
        switch (state) {
            case STATE_FOLLOW:
                doFollow();
                break;
            case STATE_SIDE:
                doSide();
            default:
                super.runState();
        }
    }

    protected void chooseNextState() {
        switch (state) {
            case STATE_STOPPED:
            case STATE_RUNAWAY:
                // Move to the WANDER state if there are no front sensors,
                // including front cliff sensors

                if ((powerNodeState & 0xc000) == 0
                      && (sensorNodeState & 0xf000) == 0xf000) {
                    this.state = STATE_WANDER;
                } else {
                    this.state = STATE_RUNAWAY;
                }

                readyToFollow(); // assign a new followee if possible

                break;

            case STATE_WANDER:
                // Move to the RUNAWAY state if there are any rear sensors
                // and at least one front sensor
                // or if there are any cliff sensors

                if (((powerNodeState & 0x3000) != 0 && (powerNodeState & 0xc000) != 0)
                      || (sensorNodeState & 0xf000) != 0xf000) {
                    this.state = STATE_RUNAWAY;
                    break;
                }

                // Check if another TrackBot is in range to follow

                if (followee != -1) {
                    if (readyToFollow(followee)) {
                        state = STATE_FOLLOW;
                        lastKnownBeaconingState = beaconState;
                        catch_up = false; // reset flag
                        break;
                    }
                }
                
                if (readyToFollow()) { // assigns a new followee if in range
                    state = STATE_FOLLOW;
                    lastKnownBeaconingState = beaconState;
                    catch_up = false; // reset flag
                    break;
                }

                /*
                if (onSide()) {
                    System.out.println("SIDE DETECTED");
                    catch_up = false;
                    state = STATE_SIDE;
                }
                else*/
                if (beingFollowed()) {
                    catch_up = false; // cancel catch up if we're being followed
                }
                break;

            case STATE_FOLLOW:
                if (!readyToFollow(followee)) {
                    doPrepWander();
                    state = STATE_WANDER;
                    catch_up = true; // we lost the followee; try to catch up
                }
                break;

            case STATE_SIDE:
                if (readyToFollow()) { // assigns a new followee if in range
                    state = STATE_FOLLOW;
                    break;
                }

                if (!onSide()) {
                    state = STATE_WANDER;
                }
                break;

            default:
                super.chooseNextState();
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
                 || (beaconState[i][MY_FORE_PORT] & OTHER_AFT_PORT) == OTHER_AFT_PORT)// this TrackBot's fore port intersects other's aft port
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
      * Checks if this TrackBot is likely being followed by another.
      *
      * @return true if another TrackBot's fore sensor(s) are beaconing with this TrackBot's aft sensor(s)
      */
     protected boolean beingFollowed() {
         for (int i = 0; i < NUM_OTHER_TRACKBOTS; i++) {
             if (otherTrackBotIDs[i] == 0) {
                 continue;
             }
             if (   (beaconState[i][MY_AFT_STAR] & OTHER_FORE_STAR) == OTHER_FORE_STAR
                 || (beaconState[i][MY_AFT_STAR] & OTHER_FORE_PORT) == OTHER_FORE_PORT
                 || (beaconState[i][MY_AFT_PORT] & OTHER_FORE_STAR) == OTHER_FORE_STAR
                 || (beaconState[i][MY_AFT_PORT] & OTHER_FORE_PORT) == OTHER_FORE_PORT)
             {
                return true;
             }
         }
         return false;
     }

    /**
      * Attempts to "swing" the TrackBot in the appropriate manner so that it lines itself
      * up behind the TrackBot being followed. TrackBot must be in a follow-ready
      * state (i.e., fore sensor(s) beaconing with followee's aft sensor(s)).
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
            //Debug.fine("case 0");
            return DIR_TURN_RIGHT;
         }


         // FORE STAR BIAS

         // Fore star intersected both aft and fore port intersected aft port
         if ((beaconState[followee][MY_FORE_STAR] & OTHER_AFT_PORT) == OTHER_AFT_PORT &&
             (beaconState[followee][MY_FORE_STAR] & OTHER_AFT_STAR) == OTHER_AFT_STAR &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_PORT) == OTHER_AFT_PORT &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_STAR) == 0)
         {
             //Debug.fine("case 1");
             return DIR_VEER_FORWARD_RIGHT;
         }

         // Fore star has intersected other's aft port and aft star
         if ((beaconState[followee][MY_FORE_STAR] & OTHER_AFT_PORT) == OTHER_AFT_PORT &&
             (beaconState[followee][MY_FORE_STAR] & OTHER_AFT_STAR) == OTHER_AFT_STAR &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_PORT) == 0 &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_STAR) == 0)
         {
             //Debug.fine("case 2");
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
             //Debug.fine("case 3");
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
                 case DIR_FORWARD:
                     //Debug.fine("case 4");
                     return DIR_FORWARD;
                 default:
                     //Debug.fine("case 5");
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
             //Debug.fine("case 6");
             return DIR_VEER_FORWARD_LEFT;
         }

         // Fore port intersected other's aft port and aft star
         if ((beaconState[followee][MY_FORE_STAR] & OTHER_AFT_PORT) == 0 &&
             (beaconState[followee][MY_FORE_STAR] & OTHER_AFT_STAR) == 0 &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_PORT) == OTHER_AFT_PORT &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_STAR) == OTHER_AFT_STAR)
         {
             //Debug.fine("case 7");
             // return DIR_TURN_LEFT;
             return DIR_VEER_FORWARD_LEFT;
         }

         // Fore port intersected other's aft star
         if ((beaconState[followee][MY_FORE_STAR] & OTHER_AFT_PORT) == 0 &&
             (beaconState[followee][MY_FORE_STAR] & OTHER_AFT_STAR) == 0 &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_PORT) == 0 &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_STAR) == OTHER_AFT_STAR)
         {
             //Debug.fine("case 8");
             return DIR_TURN_LEFT;
         }

         // Fore port intersected other's aft port
         if ((beaconState[followee][MY_FORE_STAR] & OTHER_AFT_PORT) == 0 &&
             (beaconState[followee][MY_FORE_STAR] & OTHER_AFT_STAR) == 0 &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_PORT) == OTHER_AFT_PORT &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_STAR) == 0)
         {
             //Debug.fine("case 9");
             return DIR_FORWARD;
         }

         // Ideal: Fore star intersected other's aft star and fore port intersected other's aft port
         // NOTE: this case must be last, because all 4 sensors (both fore of follower and both aft of
         //       followee) may be intersecting all of each other
         if ((beaconState[followee][MY_FORE_STAR] & OTHER_AFT_STAR) == OTHER_AFT_STAR &&
             (beaconState[followee][MY_FORE_PORT] & OTHER_AFT_PORT) == OTHER_AFT_PORT)
         {
             //Debug.fine("case 10");
             return DIR_FORWARD;
         }

         //Debug.fine("swing not determined...");
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
                    //Debug.fine("case 13");
                    dir = DIR_STOP; // TrackBot being followed is directly in front: we've acheived our goal
                    break;

                case SENSOR_FORE_PORT + SENSOR_PORT_FORE:
                    if (readyToFollow()) {
                        dir = DIR_TURN_RIGHT;
                        break;
                    }
                    //Debug.fine("case 14");
                    //dir = DIR_VEER_BACKWARD_LEFT;
                    dir = DIR_VEER_BACKWARD_RIGHT;
                    break;

                case SENSOR_FORE_STARBOARD + SENSOR_STARBOARD_FORE:
                    if (readyToFollow()) {
                        dir = DIR_TURN_LEFT;
                        break;
                    }
                    //Debug.fine("case 15");
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
                        //Debug.fine("case 16");
                        //dir = DIR_STOP;
                        dir = DIR_FORWARD;
                        break;
                    }


                    if (dir == DIR_FORWARD && (lastDir == DIR_VEER_FORWARD_LEFT || lastDir == DIR_VEER_FORWARD_RIGHT
                                                || lastDir == DIR_TURN_LEFT || lastDir == DIR_TURN_RIGHT))
                    { // need to catch up
                        speed = SPEED_FAST;
                    }
            }//switch
        }//if

        if (((lastDir != DIR_TURN_LEFT) && (lastDir != DIR_TURN_RIGHT)) &&
            (dir == DIR_TURN_LEFT || dir == DIR_TURN_RIGHT))
        { // usually this means the followee is passing by at a near-perpendicular angle; need to turn FAST
            speed = SPEED_FAST;
        }

        if (speed < 0) { // Default speed
            goDirection(dir);
        }
        else {
            goDirection(dir, speed);
        }

        //Debug.fine("dir: " + DIR_NAMES[dir]);
        lastDir = dir;

     }//doFollow

     /**
      * Behavior for the PREP_WANDER state.
      *
      * This state is simply for making the Follower turn in the appropriate direction
      * so as to maintain a following in the general direction of the followee.
      * If left solely up to the WANDER state, the follower might turn in a random,
      * undesirable direction.
      */
      protected void doPrepWander() {
          int dir = -1;

          // Followee is going off to the left
          if ((lastKnownBeaconingState[followee][MY_FORE_PORT] & OTHER_AFT_PORT) == OTHER_AFT_PORT)
          {
                dir = DIR_TURN_LEFT;
          }

          // Followee is going off to the right
          else
          if ((lastKnownBeaconingState[followee][MY_FORE_STAR] & OTHER_AFT_STAR) == OTHER_AFT_STAR)
          {
                dir = DIR_TURN_RIGHT;
          }

          if (dir != -1) {
            goDirection(dir);
          }
      }//doPrepWander

      /**
      * Behavior for the SIDE state.
      *
      * The primary objective here is to get the follower in a "ready to follow state;" that is,
      * the follower's fore sensor(s) should be beaconing with the Followee's aft sensor(s).
      */
      protected void doSide() {
            int dir = -1;

            // FORE STAR

            if (    (beaconState[followee][MY_FORE_STAR] & OTHER_PORT_FORE) == OTHER_PORT_FORE
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

            if (dir != -1) {
               goDirection(dir, SPEED_FAST);
            }
     }

     
     // Overidden goDirection methods to handle variable speeds
     
     /**
     * Moves the motors in the given direction.  Please see the
     * <code>DIR_<em>XXX</em></code> constants.
     *
     * @param dir the direction
     */
     @Override
     protected void goDirection(int dir) {
        if (catch_up) {
            goDirection(dir, SPEED_FAST);
        }
        else {
            switch (dir) {
                case DIR_STOP:
                    motors.brake(Motors.MOTOR_ALL);
                    break;
                case DIR_FORWARD:
                    goForward(Motors.MOTOR_ALL, SPEED_MEDIUM);
                    break;
                case DIR_BACKWARD:
                    goReverse(Motors.MOTOR_ALL, SPEED_MEDIUM);
                    break;
                case DIR_VEER_FORWARD_LEFT:
                    goForward(Motors.MOTOR_PORT, SPEED_SLOW);
                    goForward(Motors.MOTOR_STARBOARD, SPEED_MEDIUM);
                    break;
                case DIR_VEER_FORWARD_RIGHT:
                    goForward(Motors.MOTOR_PORT, SPEED_MEDIUM);
                    goForward(Motors.MOTOR_STARBOARD, SPEED_SLOW);
                    break;
                case DIR_VEER_BACKWARD_LEFT:
                    goReverse(Motors.MOTOR_PORT, SPEED_SLOW);
                    goReverse(Motors.MOTOR_STARBOARD, SPEED_MEDIUM);
                    break;
                case DIR_VEER_BACKWARD_RIGHT:
                    goReverse(Motors.MOTOR_PORT, SPEED_MEDIUM);
                    goReverse(Motors.MOTOR_STARBOARD, SPEED_SLOW);
                    break;
                case DIR_TURN_LEFT:
                    goReverse(Motors.MOTOR_PORT, TURN_SPEED);
                    goForward(Motors.MOTOR_STARBOARD, TURN_SPEED);
                    break;
                case DIR_TURN_RIGHT:
                    goForward(Motors.MOTOR_PORT, TURN_SPEED);
                    goReverse(Motors.MOTOR_STARBOARD, TURN_SPEED);
                    break;
            }

            lastDirection = dir;
        }//else
     }//goDirection



    /**
     * Moves the motors in the given direction and speed.  Please see the
     * <code>DIR_<em>XXX</em></code> constants.
     *
     * @param dir the direction
     * @param speed the speed
     */
     @Override
     protected void goDirection(int dir, int speed) {
        switch (dir) {
            case DIR_STOP:
                motors.brake(Motors.MOTOR_ALL);
                break;
            case DIR_FORWARD:
                goForward(Motors.MOTOR_ALL, speed);
                break;
            case DIR_BACKWARD:
                goReverse(Motors.MOTOR_ALL, speed);
                break;
            case DIR_VEER_FORWARD_LEFT:
                goForward(Motors.MOTOR_PORT, speed);
                goForward(Motors.MOTOR_STARBOARD, speed);
                break;
            case DIR_VEER_FORWARD_RIGHT:
                goForward(Motors.MOTOR_PORT, speed);
                goForward(Motors.MOTOR_STARBOARD, speed);
                break;
            case DIR_VEER_BACKWARD_LEFT:
                goReverse(Motors.MOTOR_PORT, speed);
                goReverse(Motors.MOTOR_STARBOARD, speed);
                break;
            case DIR_VEER_BACKWARD_RIGHT:
                goReverse(Motors.MOTOR_PORT, speed);
                goReverse(Motors.MOTOR_STARBOARD, speed);
                break;
            case DIR_TURN_LEFT:
                goReverse(Motors.MOTOR_PORT, speed);
                goForward(Motors.MOTOR_STARBOARD, speed);
                break;
            case DIR_TURN_RIGHT:
                goForward(Motors.MOTOR_PORT, speed);
                goReverse(Motors.MOTOR_STARBOARD, speed);
                break;
        }

        lastDirection = dir;
     }
}
