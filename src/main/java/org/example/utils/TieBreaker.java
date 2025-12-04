package org.example.utils;

import org.example.models.Team;

import java.util.Comparator;

/**
 * Comparator for sorting teams by standings with tiebreaker rules.
 */
public class TieBreaker implements Comparator<Team> {

    @Override
    public int compare(Team t1, Team t2) {
        // First: Compare by win percentage
        double wp1 = t1.getWinPercentage();
        double wp2 = t2.getWinPercentage();

        if (wp1 != wp2) {
            return Double.compare(wp2, wp1); // Higher win% is better (descending)
        }

        // Second tiebreaker: Head-to-head record
        int t1WinsVsT2 = t1.getHeadToHeadWins(t2.getTeamId());
        int t2WinsVsT1 = t2.getHeadToHeadWins(t1.getTeamId());

        if (t1WinsVsT2 != t2WinsVsT1) {
            // Team with more wins in head-to-head is ranked higher
            return Integer.compare(t2WinsVsT1, t1WinsVsT2); // Descending order
        }

        // Third tiebreaker: Total points for (higher is better)
        if (t1.getPointsFor() != t2.getPointsFor()) {
            return Double.compare(t2.getPointsFor(), t1.getPointsFor());
        }

        // Fourth tiebreaker: Points against (lower is better - better defense)
        if (t1.getPointsAgainst() != t2.getPointsAgainst()) {
            return Double.compare(t1.getPointsAgainst(), t2.getPointsAgainst());
        }

        // Final tiebreaker: Team ID (arbitrary but consistent)
        return Integer.compare(t1.getTeamId(), t2.getTeamId());
    }
}
