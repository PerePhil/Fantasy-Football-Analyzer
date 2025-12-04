package org.example.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a fantasy football team with their record and schedule.
 */
public class Team {
    private final int teamId;
    private final String teamName;
    private int wins;
    private int losses;
    private int ties;
    private double pointsFor;
    private double pointsAgainst;
    private final List<Integer> remainingOpponents; // List of opponent team IDs
    private final Map<Integer, Integer> headToHeadWins; // opponentId -> wins against that opponent
    private final Map<Integer, Integer> headToHeadLosses; // opponentId -> losses against that opponent

    public Team(int teamId, String teamName) {
        this.teamId = teamId;
        this.teamName = teamName;
        this.wins = 0;
        this.losses = 0;
        this.ties = 0;
        this.pointsFor = 0.0;
        this.pointsAgainst = 0.0;
        this.remainingOpponents = new ArrayList<>();
        this.headToHeadWins = new HashMap<>();
        this.headToHeadLosses = new HashMap<>();
    }

    // Copy constructor for simulation
    public Team(Team other) {
        this.teamId = other.teamId;
        this.teamName = other.teamName;
        this.wins = other.wins;
        this.losses = other.losses;
        this.ties = other.ties;
        this.pointsFor = other.pointsFor;
        this.pointsAgainst = other.pointsAgainst;
        this.remainingOpponents = new ArrayList<>(other.remainingOpponents);
        this.headToHeadWins = new HashMap<>(other.headToHeadWins);
        this.headToHeadLosses = new HashMap<>(other.headToHeadLosses);
    }

    // Getters
    public int getTeamId() {
        return teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    public int getTies() {
        return ties;
    }

    public double getPointsFor() {
        return pointsFor;
    }

    public double getPointsAgainst() {
        return pointsAgainst;
    }

    public List<Integer> getRemainingOpponents() {
        return remainingOpponents;
    }

    // Setters
    public void setWins(int wins) {
        this.wins = wins;
    }

    public void setLosses(int losses) {
        this.losses = losses;
    }

    public void setTies(int ties) {
        this.ties = ties;
    }

    public void setPointsFor(double pointsFor) {
        this.pointsFor = pointsFor;
    }

    public void setPointsAgainst(double pointsAgainst) {
        this.pointsAgainst = pointsAgainst;
    }

    // Helper methods
    public void addWin() {
        this.wins++;
    }

    public void addLoss() {
        this.losses++;
    }

    public void addTie() {
        this.ties++;
    }

    public void addPointsFor(double points) {
        this.pointsFor += points;
    }

    public void addPointsAgainst(double points) {
        this.pointsAgainst += points;
    }

    public void recordHeadToHeadWin(int opponentId) {
        headToHeadWins.merge(opponentId, 1, Integer::sum);
    }

    public void recordHeadToHeadLoss(int opponentId) {
        headToHeadLosses.merge(opponentId, 1, Integer::sum);
    }

    public int getHeadToHeadWins(int opponentId) {
        return headToHeadWins.getOrDefault(opponentId, 0);
    }

    public int getHeadToHeadLosses(int opponentId) {
        return headToHeadLosses.getOrDefault(opponentId, 0);
    }

    public void addRemainingOpponent(int opponentId) {
        this.remainingOpponents.add(opponentId);
    }

    public double getWinPercentage() {
        int totalGames = wins + losses + ties;
        if (totalGames == 0)
            return 0.0;
        return (wins + 0.5 * ties) / totalGames;
    }

    public String getRecord() {
        if (ties > 0) {
            return String.format("%d-%d-%d", wins, losses, ties);
        }
        return String.format("%d-%d", wins, losses);
    }

    @Override
    public String toString() {
        return String.format("%s (%s) - PF: %.2f, PA: %.2f",
                teamName, getRecord(), pointsFor, pointsAgainst);
    }
}
