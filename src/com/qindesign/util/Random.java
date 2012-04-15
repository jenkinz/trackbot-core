/*
 * Date: Oct 22, 2007
 * Time: 2:48:12 PM
 * (c) 2007 Shawn Silverman
 */
package com.qindesign.util;

/**
 * Provides <code>nextBoolean</code> and <code>nextInt(n)</code> capability
 * for CLDC.
 *
 * @author Shawn Silverman
 * @see <a href="http://mindprod.com/jgloss/pseudorandom.html#JDK11">pseudo-random
 *      numbers</a>
 */
public class Random extends java.util.Random {
    /**
     * Returns the next pseudorandom, uniformly distributed
     * <code>boolean</code> value.
     *
     * @return the next pseudorandom, uniformly distributed
     *         <code>boolean</code> value.
     */
    public boolean nextBoolean() {
        return next(1) != 0;
    }

    /**
     * Returns a pseudorandom, uniformly distributed <code>int</code> between
     * zero (inclusive) and the specified value (exclusive).  The upper bound
     * must be positive.
     *
     * @param n the positive upper bound (exculsive) on the random number
     * @return a pseudorandom, uniformly distributed <code>int</code> between
     *         zero (inclusive) and the specified value (exclusive).
     * @throws IllegalArgumentException if <code>n</code> is non-positive.
     */
    public int nextInt(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive: " + n);
        }

        if ((n & -n) == n) {  // i.e., n is a power of 2
            return (int)((n * (long)next(31)) >> 31);
        }

        int bits, val;
        do {
            bits = next(31);
            val = bits % n;
        } while (bits - val + (n - 1) < 0);
        return val;
    }
}
