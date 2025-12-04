package org.example;

import org.example.models.League;
import org.example.models.Matchup;
import org.example.models.Team;
import org.example.utils.TieBreaker;

import java.util.*;

/**
 * Simulates all possible outcomes for remaining games and calculates playoff
 * odds.
 */
public class SimulationEngine {
    private final League league;
    private final List<Matchup> remainingGames;
    private final Map<Integer, Integer> playoffCounts; // teamId -> count of scenarios where team made playoffs
    private final Map<Integer, List<int[]>> playoffScenarios; // teamId -> list of scenario bit patterns that result in
                                                              // playoff
    private long totalScenarios;

    public SimulationEngine(League league) {
        this.league = league;
        this.remainingGames = league.getRemainingMatchups();
        this.playoffCounts = new HashMap<>();
        this.playoffScenarios = new HashMap<>();
        this.totalScenarios = 0;

        // Initialize playoff counts and scenarios
        for (Team team : league.getTeams().values()) {
            playoffCounts.put(team.getTeamId(), 0);
            playoffScenarios.put(team.getTeamId(), new ArrayList<>());
        }
    }

    /**
     * Run the simulation for all possible outcomes.
     */
    public void runSimulation() {
        int numGames = remainingGames.size();

        if (numGames == 0) {
            // No games remaining, just calculate current standings
            totalScenarios = 1;
            Set<Integer> playoffTeams = calculatePlayoffTeams(new HashMap<>(league.getTeams()));
            for (int teamId : playoffTeams) {
                playoffCounts.put(teamId, 1);
            }
            return;
        }

        // Calculate total number of scenarios (2^n)
        totalScenarios = 1L << numGames; // 2^numGames

        System.out.printf("Simulating %,d scenarios (%d remaining games)...\n",
                totalScenarios, numGames);

        // Generate all possible outcomes
        simulateAllOutcomes(0, new HashMap<>(league.getTeams()), new int[numGames]);

        System.out.println("Simulation complete!");
    }

    /**
     * Recursively simulate all possible outcomes using binary tree approach.
     * 
     * @param gameIndex       Current game index being simulated
     * @param currentTeams    Current state of all teams
     * @param scenarioPattern Bit pattern representing current scenario (0=home win,
     *                        1=away win)
     */
    private void simulateAllOutcomes(int gameIndex, Map<Integer, Team> currentTeams, int[] scenarioPattern) {
        // Base case: all games have been simulated
        if (gameIndex >= remainingGames.size()) {
            Set<Integer> playoffTeams = calculatePlayoffTeams(currentTeams);

            // Increment playoff count and record scenario for teams that made it
            for (int teamId : playoffTeams) {
                playoffCounts.merge(teamId, 1, Integer::sum);
                playoffScenarios.get(teamId).add(scenarioPattern.clone());
            }
            return;
        }

        Matchup game = remainingGames.get(gameIndex);

        // Simulate home team win (0 in bit pattern)
        Map<Integer, Team> homeWinScenario = copyTeams(currentTeams);
        applyResult(homeWinScenario, game.getHomeTeamId(), game.getAwayTeamId());
        scenarioPattern[gameIndex] = 0;
        simulateAllOutcomes(gameIndex + 1, homeWinScenario, scenarioPattern);

        // Simulate away team win (1 in bit pattern)
        Map<Integer, Team> awayWinScenario = copyTeams(currentTeams);
        applyResult(awayWinScenario, game.getAwayTeamId(), game.getHomeTeamId());
        scenarioPattern[gameIndex] = 1;
        simulateAllOutcomes(gameIndex + 1, awayWinScenario, scenarioPattern);
    }

    /**
     * Calculate which teams make playoffs based on current standings.
     */
    private Set<Integer> calculatePlayoffTeams(Map<Integer, Team> teams) {
        // Sort teams by standings
        List<Team> sortedTeams = new ArrayList<>(teams.values());
        sortedTeams.sort(new TieBreaker());

        // Top N teams make playoffs
        Set<Integer> playoffTeams = new HashSet<>();
        int numPlayoffSpots = league.getNumberOfPlayoffTeams();

        for (int i = 0; i < Math.min(numPlayoffSpots, sortedTeams.size()); i++) {
            playoffTeams.add(sortedTeams.get(i).getTeamId());
        }

        return playoffTeams;
    }

    /**
     * Apply game result to teams.
     */
    private void applyResult(Map<Integer, Team> teams, int winnerId, int loserId) {
        Team winner = teams.get(winnerId);
        Team loser = teams.get(loserId);

        if (winner != null) {
            winner.addWin();
            winner.recordHeadToHeadWin(loserId);
        }
        if (loser != null) {
            loser.addLoss();
            loser.recordHeadToHeadLoss(winnerId);
        }
    }

    /**
     * Deep copy all teams for independent simulation branches.
     */
    private Map<Integer, Team> copyTeams(Map<Integer, Team> teams) {
        Map<Integer, Team> copy = new HashMap<>();
        for (Map.Entry<Integer, Team> entry : teams.entrySet()) {
            copy.put(entry.getKey(), new Team(entry.getValue()));
        }
        return copy;
    }

    /**
     * Get playoff odds for a specific team (0.0 to 1.0).
     */
    public double getPlayoffOdds(int teamId) {
        if (totalScenarios == 0)
            return 0.0;
        return (double) playoffCounts.get(teamId) / totalScenarios;
    }

    /**
     * Get playoff odds for all teams.
     */
    public Map<Integer, Double> getAllPlayoffOdds() {
        Map<Integer, Double> odds = new HashMap<>();
        for (int teamId : playoffCounts.keySet()) {
            odds.put(teamId, getPlayoffOdds(teamId));
        }
        return odds;
    }

    /**
     * Get total number of scenarios simulated.
     */
    public long getTotalScenarios() {
        return totalScenarios;
    }

    /**
     * Get playoff count (raw number of scenarios team made playoffs).
     */
    public int getPlayoffCount(int teamId) {
        return playoffCounts.getOrDefault(teamId, 0);
    }

    /**
     * Get remaining games list.
     */
    public List<Matchup> getRemainingGames() {
        return remainingGames;
    }

    /**
     * Get playoff scenarios for a specific team.
     */
    public List<int[]> getPlayoffScenarios(int teamId) {
        return playoffScenarios.getOrDefault(teamId, new ArrayList<>());
    }
}
