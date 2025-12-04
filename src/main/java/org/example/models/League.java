package org.example.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a fantasy football league with its configuration and teams.
 */
public class League {
    private final int leagueId;
    private final String leagueName;
    private final int year;
    private final int numberOfPlayoffTeams;
    private final int currentWeek;
    private final int playoffStartWeek;
    private final int regularSeasonWeeks;
    private final Map<Integer, Team> teams; // teamId -> Team
    private final List<Matchup> matchups;

    public League(int leagueId, String leagueName, int year,
            int numberOfPlayoffTeams, int currentWeek,
            int playoffStartWeek, int regularSeasonWeeks) {
        this.leagueId = leagueId;
        this.leagueName = leagueName;
        this.year = year;
        this.numberOfPlayoffTeams = numberOfPlayoffTeams;
        this.currentWeek = currentWeek;
        this.playoffStartWeek = playoffStartWeek;
        this.regularSeasonWeeks = regularSeasonWeeks;
        this.teams = new HashMap<>();
        this.matchups = new ArrayList<>();
    }

    // Getters
    public int getLeagueId() {
        return leagueId;
    }

    public String getLeagueName() {
        return leagueName;
    }

    public int getYear() {
        return year;
    }

    public int getNumberOfPlayoffTeams() {
        return numberOfPlayoffTeams;
    }

    public int getCurrentWeek() {
        return currentWeek;
    }

    public int getPlayoffStartWeek() {
        return playoffStartWeek;
    }

    public int getRegularSeasonWeeks() {
        return regularSeasonWeeks;
    }

    public Map<Integer, Team> getTeams() {
        return teams;
    }

    public List<Matchup> getMatchups() {
        return matchups;
    }

    // Helper methods
    public void addTeam(Team team) {
        teams.put(team.getTeamId(), team);
    }

    public Team getTeam(int teamId) {
        return teams.get(teamId);
    }

    public void addMatchup(Matchup matchup) {
        matchups.add(matchup);
    }

    public List<Matchup> getRemainingMatchups() {
        List<Matchup> remaining = new ArrayList<>();
        for (Matchup m : matchups) {
            if (!m.isCompleted()) {
                remaining.add(m);
            }
        }
        return remaining;
    }

    public List<Matchup> getCompletedMatchups() {
        List<Matchup> completed = new ArrayList<>();
        for (Matchup m : matchups) {
            if (m.isCompleted()) {
                completed.add(m);
            }
        }
        return completed;
    }

    public int getRemainingWeeks() {
        return regularSeasonWeeks - currentWeek + 1;
    }

    public int getNumberOfTeams() {
        return teams.size();
    }

    @Override
    public String toString() {
        return String.format("League: %s (ID: %d) - Year: %d\n" +
                "Current Week: %d | Playoff Week: %d | Regular Season Weeks: %d\n" +
                "Teams: %d | Playoff Spots: %d\n" +
                "Matchups: %d (Completed: %d, Remaining: %d)",
                leagueName, leagueId, year, currentWeek, playoffStartWeek,
                regularSeasonWeeks, getNumberOfTeams(), numberOfPlayoffTeams,
                matchups.size(), getCompletedMatchups().size(),
                getRemainingMatchups().size());
    }
}
