package org.example.utils;

/**
 * League tiebreaker: win percentage, then head-to-head record among the tied
 * teams, then total points for.
 *
 * Head-to-head with three or more tied teams uses each team's combined record
 * against the others in the tie. If that splits the group, each smaller group
 * that is still tied starts over at head-to-head. If head-to-head can't split
 * a group, it goes to total points.
 *
 * Final points totals are unknown while games remain, so a tie that reaches
 * the points step is only settled when the order is already locked (the team
 * behind has no games left to make up the gap). Otherwise the teams involved
 * are "points-dependent".
 *
 * Not thread-safe: each thread needs its own instance (it reuses scratch arrays).
 */
public final class TieBreaker {
    /** Makes the playoffs no matter how points tiebreakers go. */
    public static final int IN = 0;
    /** Playoff fate depends on a total-points tiebreaker. */
    public static final int POINTS = 1;
    /** Misses the playoffs no matter how points tiebreakers go. */
    public static final int OUT = 2;

    private final int n;
    private final int spots;
    private final double[] pointsFor;
    private final boolean[] pointsCanChange;

    // Scratch space reused on every call.
    private final int[] order;
    private final long[] num;
    private final long[] den;
    private int[][] h2h;
    private int[] stateOut;
    private long[] rivalsOut;

    /**
     * @param spots           number of playoff spots
     * @param pointsFor       current total points for, by team index
     * @param pointsCanChange whether each team still has games left (so its
     *                        points total can still go up)
     */
    public TieBreaker(int spots, double[] pointsFor, boolean[] pointsCanChange) {
        this.n = pointsFor.length;
        this.spots = spots;
        this.pointsFor = pointsFor.clone();
        this.pointsCanChange = pointsCanChange.clone();
        this.order = new int[n];
        this.num = new long[n];
        this.den = new long[n];
    }

    /**
     * Classify every team as IN, POINTS or OUT for one final set of records.
     *
     * @param h2h       h2h[i][j] = wins by team i over team j
     * @param stateOut  receives IN / POINTS / OUT for each team
     * @param rivalsOut for POINTS teams, receives a bitmask of the teams whose
     *                  order against it comes down to total points; 0 otherwise
     */
    public void evaluate(int[] wins, int[] losses, int[] ties, int[][] h2h,
            int[] stateOut, long[] rivalsOut) {
        this.h2h = h2h;
        this.stateOut = stateOut;
        this.rivalsOut = rivalsOut;

        // Step 1: win percentage, stored as an exact fraction (2W + T) / 2G.
        for (int i = 0; i < n; i++) {
            order[i] = i;
            long games = (long) wins[i] + losses[i] + ties[i];
            if (games == 0) {
                num[i] = 0;
                den[i] = 1;
            } else {
                num[i] = 2L * wins[i] + ties[i];
                den[i] = 2L * games;
            }
        }
        sortDescending(0, n);

        int from = 0;
        while (from < n) {
            int to = tierEnd(from, n);
            resolveHeadToHead(from, to, from);
            from = to;
        }
    }

    /** Teams order[from..to) are tied on everything so far; they take positions start..start+size-1. */
    private void resolveHeadToHead(int from, int to, int start) {
        if (to - from == 1) {
            settle(order[from], start);
            return;
        }

        // Combined head-to-head record of each team against the rest of the group.
        for (int a = from; a < to; a++) {
            int i = order[a];
            long w = 0, l = 0;
            for (int b = from; b < to; b++) {
                int j = order[b];
                w += h2h[i][j];
                l += h2h[j][i];
            }
            if (w + l == 0) {
                num[i] = 1; // no games against the group counts as .500
                den[i] = 2;
            } else {
                num[i] = w;
                den[i] = w + l;
            }
        }
        sortDescending(from, to);

        if (tierEnd(from, to) == to) {
            // Head-to-head doesn't split anyone: go to total points.
            resolvePoints(from, to, start);
            return;
        }

        int a = from;
        while (a < to) {
            int b = tierEnd(a, to);
            resolveHeadToHead(a, b, start + (a - from));
            a = b;
        }
    }

    /** Teams order[from..to) are still tied after head-to-head, so total points decides. */
    private void resolvePoints(int from, int to, int start) {
        int size = to - from;
        for (int a = from; a < to; a++) {
            int i = order[a];
            int surelyAhead = 0, surelyBehind = 0;
            long rivals = 0;
            for (int b = from; b < to; b++) {
                if (a == b)
                    continue;
                int j = order[b];
                // Points only go up, so a lead is locked once the trailing team has no games left.
                if (pointsFor[j] > pointsFor[i] && !pointsCanChange[i]) {
                    surelyAhead++;
                } else if (pointsFor[i] > pointsFor[j] && !pointsCanChange[j]) {
                    surelyBehind++;
                } else {
                    rivals |= 1L << j;
                }
            }
            int best = start + surelyAhead;
            int worst = start + size - 1 - surelyBehind;
            if (worst < spots) {
                stateOut[i] = IN;
                rivalsOut[i] = 0;
            } else if (best >= spots) {
                stateOut[i] = OUT;
                rivalsOut[i] = 0;
            } else {
                stateOut[i] = POINTS;
                rivalsOut[i] = rivals;
            }
        }
    }

    private void settle(int team, int position) {
        stateOut[team] = position < spots ? IN : OUT;
        rivalsOut[team] = 0;
    }

    /** Index just past the run of teams starting at {@code from} that share the same fraction. */
    private int tierEnd(int from, int to) {
        int first = order[from];
        int end = from + 1;
        while (end < to && compare(order[end], first) == 0) {
            end++;
        }
        return end;
    }

    /** Insertion sort of order[from..to), highest fraction first. Groups are small. */
    private void sortDescending(int from, int to) {
        for (int a = from + 1; a < to; a++) {
            int x = order[a];
            int b = a - 1;
            while (b >= from && compare(order[b], x) < 0) {
                order[b + 1] = order[b];
                b--;
            }
            order[b + 1] = x;
        }
    }

    /** Compare fractions num/den of teams i and j. */
    private int compare(int i, int j) {
        return Long.compare(num[i] * den[j], num[j] * den[i]);
    }
}
