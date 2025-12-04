package org.example.models;

/**
 * Represents a single matchup between two teams in the fantasy league.
 */
public class Matchup {
    private final int week;
    private final int homeTeamId;
    private final int awayTeamId;
    private Double homeScore;
    private Double awayScore;
    private Integer winnerId; // null if game hasn't been played

    public Matchup(int week, int homeTeamId, int awayTeamId) {
        this.week = week;
        this.homeTeamId = homeTeamId;
        this.awayTeamId = awayTeamId;
        this.homeScore = null;
        this.awayScore = null;
        this.winnerId = null;
    }

    public Matchup(int week, int homeTeamId, int awayTeamId, double homeScore, double awayScore) {
        this.week = week;
        this.homeTeamId = homeTeamId;
        this.awayTeamId = awayTeamId;
        this.homeScore = homeScore;
        this.awayScore = awayScore;
        this.winnerId = determineWinner();
    }

    // Getters
    public int getWeek() {
        return week;
    }

    public int getHomeTeamId() {
        return homeTeamId;
    }

    public int getAwayTeamId() {
        return awayTeamId;
    }

    public Double getHomeScore() {
        return homeScore;
    }

    public Double getAwayScore() {
        return awayScore;
    }

    public Integer getWinnerId() {
        return winnerId;
    }

    // Setters
    public void setScores(double homeScore, double awayScore) {
        this.homeScore = homeScore;
        this.awayScore = awayScore;
        this.winnerId = determineWinner();
    }

    public boolean isCompleted() {
        return homeScore != null && awayScore != null;
    }

    public boolean isTie() {
        return isCompleted() && homeScore.equals(awayScore);
    }

    private Integer determineWinner() {
        if (homeScore == null || awayScore == null) {
            return null;
        }
        if (homeScore > awayScore) {
            return homeTeamId;
        } else if (awayScore > homeScore) {
            return awayTeamId;
        }
        return null; // Tie
    }

    public int getLoserId() {
        if (winnerId == null) {
            return -1; // No loser if tie or incomplete
        }
        return winnerId == homeTeamId ? awayTeamId : homeTeamId;
    }

    @Override
    public String toString() {
        if (isCompleted()) {
            return String.format("Week %d: Team %d (%.2f) vs Team %d (%.2f) - Winner: %s",
                    week, homeTeamId, homeScore, awayTeamId, awayScore,
                    winnerId != null ? "Team " + winnerId : "TIE");
        }
        return String.format("Week %d: Team %d vs Team %d (Not Played)",
                week, homeTeamId, awayTeamId);
    }
}
