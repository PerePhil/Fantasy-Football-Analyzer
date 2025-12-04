package org.example;

import org.example.models.League;
import org.example.models.Team;

import java.util.*;

/**
 * Analyzes clinch scenarios and magic numbers for playoff qualification.
 */
public class ClinchAnalyzer {
    private final League league;
    private final SimulationEngine simulator;
    private final Map<Integer, Double> playoffOdds;

    public ClinchAnalyzer(League league, SimulationEngine simulator) {
        this.league = league;
        this.simulator = simulator;
        this.playoffOdds = simulator.getAllPlayoffOdds();
    }

    /**
     * Get the status description for a team.
     */
    public String getTeamStatus(int teamId) {
        double odds = playoffOdds.get(teamId);

        if (odds >= 1.0) {
            // Check if truly clinched (playoffs guaranteed regardless of points)
            if (hasTrueClinch(teamId)) {
                return "CLINCHED";
            } else {
                return "Likely In";
            }
        } else if (odds == 0.0) {
            // Check if truly eliminated (can't make playoffs regardless of points)
            if (hasTrueElimination(teamId)) {
                return "ELIMINATED";
            } else {
                return "Likely Out";
            }
        } else if (odds >= 0.95) {
            return "Magic #: 1";
        } else if (odds >= 0.75) {
            return "Strong position";
        } else if (odds >= 0.5) {
            return "In the hunt";
        } else if (odds >= 0.25) {
            return "Need help";
        } else {
            return "Long shot";
        }
    }

    /**
     * Check if a team has truly clinched - makes playoffs in all scenarios
     * even with worst possible points tiebreakers.
     */
    private boolean hasTrueClinch(int teamId) {
        // If no remaining games, 100% odds = clinched
        if (league.getRemainingMatchups().isEmpty()) {
            return true;
        }

        // A team has truly clinched if their WORST possible wins
        // is strictly greater than the (playoff_spots + 1)th best possible wins
        Team team = league.getTeam(teamId);
        int worstWins = team.getWins(); // lose all remaining games
        int playoffSpots = league.getNumberOfPlayoffTeams();

        // Get best possible wins for all other teams, sorted descending
        List<Integer> otherBestWins = new ArrayList<>();
        for (Team other : league.getTeams().values()) {
            if (other.getTeamId() == teamId)
                continue;

            // Best possible wins = current wins + all remaining games
            int bestWins = other.getWins() + other.getRemainingOpponents().size();
            otherBestWins.add(bestWins);
        }

        // Sort descending
        otherBestWins.sort(Collections.reverseOrder());

        // If there are at least (playoffSpots) teams, check the team at position
        // (playoffSpots - 1)
        // This is the cutoff - teams at this position or better would make playoffs
        // We clinch if our worst wins is STRICTLY BETTER than the team at position
        // playoffSpots
        // (the first team OUT of playoffs in best case scenario)
        if (otherBestWins.size() >= playoffSpots) {
            int firstTeamOutBestWins = otherBestWins.get(playoffSpots - 1);
            // We clinch only if we're guaranteed to have MORE wins than the cutoff
            return worstWins > firstTeamOutBestWins;
        } else {
            // Fewer teams than playoff spots - everyone makes it
            return true;
        }
    }

    /**
     * Check if a team is truly eliminated - can't make playoffs even with
     * best possible points tiebreakers.
     */
    private boolean hasTrueElimination(int teamId) {
        // If no remaining games, 0% odds = eliminated
        if (league.getRemainingMatchups().isEmpty()) {
            return true;
        }

        // A team is eliminated if their BEST possible wins
        // is strictly less than the (playoff_spots)th worst possible wins
        Team team = league.getTeam(teamId);
        int bestWins = team.getWins() + team.getRemainingOpponents().size(); // win all
        int playoffSpots = league.getNumberOfPlayoffTeams();

        // Get worst possible wins for all other teams, sorted descending
        List<Integer> otherWorstWins = new ArrayList<>();
        for (Team other : league.getTeams().values()) {
            if (other.getTeamId() == teamId)
                continue;

            // Worst possible wins = current wins (lose all remaining)
            int worstWins = other.getWins();
            otherWorstWins.add(worstWins);
        }

        // Sort descending
        otherWorstWins.sort(Collections.reverseOrder());

        // If there are at least playoffSpots teams, check the team at position
        // (playoffSpots - 1)
        // This is the last team IN playoffs in worst case scenario
        // We're eliminated if our best wins is STRICTLY LESS than this cutoff
        if (otherWorstWins.size() >= playoffSpots) {
            int lastTeamInWorstWins = otherWorstWins.get(playoffSpots - 1);
            // We're eliminated only if we can't match the wins of the playoff cutoff
            return bestWins < lastTeamInWorstWins;
        } else {
            // Fewer teams than playoff spots - everyone makes it
            return false;
        }
    }

    /**
     * Get a detailed analysis for a specific team.
     */
    public String getDetailedAnalysis(int teamId) {
        Team team = league.getTeam(teamId);
        double odds = playoffOdds.get(teamId);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("\n=== %s ===\n", team.getTeamName()));
        sb.append(String.format("Record: %s\n", team.getRecord()));
        sb.append(String.format("Points For: %.2f | Points Against: %.2f\n",
                team.getPointsFor(), team.getPointsAgainst()));
        sb.append(String.format("Playoff Odds: %.1f%%\n", odds * 100));
        sb.append(String.format("Status: %s\n", getTeamStatus(teamId)));

        int remainingGames = team.getRemainingOpponents().size();
        sb.append(String.format("Remaining Games: %d\n", remainingGames));

        // Add scenario analysis
        if (odds > 0 && odds < 1.0) {
            sb.append(String.format("\nMakes playoffs in %,d of %,d scenarios\n",
                    simulator.getPlayoffCount(teamId),
                    simulator.getTotalScenarios()));
        }

        return sb.toString();
    }

    /**
     * Get outcomes sorted by playoff odds.
     */
    public List<Map.Entry<Integer, Double>> getSortedOdds() {
        List<Map.Entry<Integer, Double>> sorted = new ArrayList<>(playoffOdds.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue())); // Descending
        return sorted;
    }

    /**
     * Generate a summary report of all teams.
     */
    public String generateSummaryReport() {
        StringBuilder sb = new StringBuilder();

        sb.append("\n");
        sb.append("=".repeat(70)).append("\n");
        sb.append("FANTASY FOOTBALL PLAYOFF CONDITIONS\n");
        sb.append("=".repeat(70)).append("\n");
        sb.append(league.toString()).append("\n");
        sb.append("=".repeat(70)).append("\n\n");

        // Header
        sb.append(String.format("%-30s %8s %30s\n",
                "Team", "Record", "Status"));
        sb.append("-".repeat(70)).append("\n");

        // Sort teams by playoff odds (just for ordering)
        List<Map.Entry<Integer, Double>> sortedOdds = getSortedOdds();

        for (Map.Entry<Integer, Double> entry : sortedOdds) {
            int teamId = entry.getKey();
            Team team = league.getTeam(teamId);
            String status = getTeamStatus(teamId);

            sb.append(String.format("%-30s %8s %30s\n",
                    truncate(team.getTeamName(), 30),
                    team.getRecord(),
                    status));
        }

        sb.append("-".repeat(70)).append("\n");
        sb.append(String.format("\nWin/loss scenarios analyzed: %,d\n",
                simulator.getTotalScenarios()));
        sb.append(String.format("Remaining games: %d\n",
                league.getRemainingMatchups().size()));
        sb.append("Note: Points tiebreakers shown as conditions below.\n");

        return sb.toString();
    }

    /**
     * Truncate string to max length.
     */
    private String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * Generate clinch scenarios report for teams not clinched or eliminated.
     */
    public String generateClinchScenariosReport() {
        StringBuilder sb = new StringBuilder();

        sb.append("\n");
        sb.append("=".repeat(70)).append("\n");
        sb.append("CLINCH SCENARIOS\n");
        sb.append("=".repeat(70)).append("\n\n");

        List<Map.Entry<Integer, Double>> sortedOdds = getSortedOdds();
        boolean anyShown = false;

        for (Map.Entry<Integer, Double> entry : sortedOdds) {
            int teamId = entry.getKey();
            double odds = entry.getValue();
            Team team = league.getTeam(teamId);
            String status = getTeamStatus(teamId);

            // Show "Likely In" teams (100% but not clinched)
            if (status.equals("Likely In")) {
                anyShown = true;
                sb.append(String.format("### %s (%s) - %s ###\n",
                        team.getTeamName(), team.getRecord(), status));
                sb.append("Makes playoffs in all win/loss scenarios.\n");
                sb.append("Could miss only if points tiebreaker goes against them.\n\n");

                // Show nightmare scenario
                sb.append("Nightmare scenario to miss playoffs:\n");
                sb.append(generateNightmareScenario(teamId));
                sb.append("\n");

                // Analyze potential tiebreaker scenarios
                String tiebreakerDetails = analyzeTiebreakerRisks(teamId);
                sb.append(tiebreakerDetails);
                sb.append("\n");
            }
            // Show "Likely Out" teams (0% but not eliminated)
            else if (status.equals("Likely Out")) {
                anyShown = true;
                sb.append(String.format("### %s (%s) - %s ###\n",
                        team.getTeamName(), team.getRecord(), status));
                sb.append("Misses playoffs in all win/loss scenarios.\n");
                sb.append("Could make it only if points tiebreaker goes in their favor.\n\n");

                // Show miracle scenario
                sb.append("Miracle scenario to make playoffs:\n");
                sb.append(generateMiracleScenario(teamId));
                sb.append("\n");
            }
            // Show teams in contention (not clinched, not eliminated, not 100%/0%)
            else if (odds > 0.0 && odds < 1.0) {
                anyShown = true;
                sb.append(String.format("### %s (%s) - In Contention ###\n",
                        team.getTeamName(), team.getRecord()));

                // Show playoff conditions
                sb.append("Playoff conditions:\n");
                sb.append(generatePlayoffConditions(teamId));
                sb.append("\n");
            }
        }

        if (!anyShown) {
            sb.append("No scenarios to display.\n");
            sb.append("All teams have either clinched or been eliminated.\n");
        }

        return sb.toString();
    }

    /**
     * Generate playoff conditions for a team in contention.
     * Shows exactly what needs to happen for them to make playoffs.
     */
    private String generatePlayoffConditions(int teamId) {
        StringBuilder sb = new StringBuilder();
        Team team = league.getTeam(teamId);
        List<org.example.models.Matchup> remainingGames = simulator.getRemainingGames();
        List<int[]> playoffScenarios = simulator.getPlayoffScenarios(teamId);

        if (playoffScenarios.isEmpty()) {
            sb.append("  Cannot make playoffs in any win/loss scenario.\n");
            sb.append("  Would need to tie on record AND win points tiebreaker.\n");
            return sb.toString();
        }

        // Find required outcomes (games where same team wins in ALL playoff scenarios)
        Map<Integer, Boolean> requiredResults = new LinkedHashMap<>(); // gameIndex -> true=home wins, false=away wins

        for (int gameIdx = 0; gameIdx < remainingGames.size(); gameIdx++) {
            Boolean requiredResult = null;
            boolean consistent = true;

            for (int[] scenario : playoffScenarios) {
                boolean homeWins = scenario[gameIdx] == 0;
                if (requiredResult == null) {
                    requiredResult = homeWins;
                } else if (requiredResult != homeWins) {
                    consistent = false;
                    break;
                }
            }

            if (consistent && requiredResult != null) {
                requiredResults.put(gameIdx, requiredResult);
            }
        }

        // Show required outcomes
        if (requiredResults.isEmpty()) {
            sb.append("  Makes playoffs in SOME scenarios, not in others.\n");
            sb.append("  Outcome depends on combination of game results.\n\n");
        } else {
            sb.append("  Required game results:\n");
            for (Map.Entry<Integer, Boolean> entry : requiredResults.entrySet()) {
                int gameIdx = entry.getKey();
                boolean homeWins = entry.getValue();
                org.example.models.Matchup game = remainingGames.get(gameIdx);

                String winner = homeWins
                        ? league.getTeam(game.getHomeTeamId()).getTeamName()
                        : league.getTeam(game.getAwayTeamId()).getTeamName();
                String loser = homeWins
                        ? league.getTeam(game.getAwayTeamId()).getTeamName()
                        : league.getTeam(game.getHomeTeamId()).getTeamName();

                sb.append(String.format("    - %s beats %s\n", winner, loser));
            }
            sb.append("\n");
        }

        // Show potential tiebreaker conditions
        int bestWins = team.getWins() + team.getRemainingOpponents().size();

        sb.append("  Potential tiebreaker scenarios (if tied on record):\n");
        for (Team other : league.getTeams().values()) {
            if (other.getTeamId() == teamId)
                continue;

            String otherStatus = getTeamStatus(other.getTeamId());
            if (otherStatus.equals("CLINCHED") || otherStatus.equals("ELIMINATED")) {
                continue;
            }

            // Could they finish with same record?
            int otherMinWins = other.getWins();
            int otherMaxWins = other.getWins() + other.getRemainingOpponents().size();

            if (bestWins >= otherMinWins && bestWins <= otherMaxWins) {
                int h2hWins = team.getHeadToHeadWins(other.getTeamId());
                int h2hLosses = team.getHeadToHeadLosses(other.getTeamId());
                boolean playAgain = team.getRemainingOpponents().contains(other.getTeamId());

                if (playAgain) {
                    // H2H could change this week
                    sb.append(String.format("    vs %s: If same record, winner of this week's game wins tiebreaker\n",
                            other.getTeamName()));
                } else if (h2hWins > h2hLosses) {
                    sb.append(String.format("    vs %s: Wins H2H tiebreaker (%d-%d) ✓\n",
                            other.getTeamName(), h2hWins, h2hLosses));
                } else if (h2hLosses > h2hWins) {
                    sb.append(String.format("    vs %s: Loses H2H tiebreaker (%d-%d) ✗\n",
                            other.getTeamName(), h2hWins, h2hLosses));
                } else {
                    // H2H tied, show points condition
                    double pointsDiff = team.getPointsFor() - other.getPointsFor();
                    if (pointsDiff > 0) {
                        sb.append(String.format("    vs %s: Must not be outscored by more than %.1f pts\n",
                                other.getTeamName(), pointsDiff));
                    } else {
                        sb.append(String.format("    vs %s: Must outscore by %.1f+ pts\n",
                                other.getTeamName(), Math.abs(pointsDiff) + 0.1));
                    }
                }
            }
        }

        return sb.toString();
    }

    /**
     * Generate nightmare scenario for a "Likely In" team showing how they could
     * miss playoffs.
     */
    private String generateNightmareScenario(int teamId) {
        StringBuilder sb = new StringBuilder();
        Team team = league.getTeam(teamId);

        // Worst case record for this team
        int worstWins = team.getWins(); // lose all remaining
        int worstLosses = team.getLosses() + team.getRemainingOpponents().size();

        // Find teams that could tie or beat this record
        List<Team> threats = new ArrayList<>();
        for (Team other : league.getTeams().values()) {
            if (other.getTeamId() == teamId)
                continue;

            String otherStatus = getTeamStatus(other.getTeamId());
            if (otherStatus.equals("CLINCHED") || otherStatus.equals("ELIMINATED")) {
                continue;
            }

            // Best possible record for other team
            int otherBestWins = other.getWins() + other.getRemainingOpponents().size();

            // Could they finish with same or better record?
            if (otherBestWins >= worstWins) {
                threats.add(other);
            }
        }

        // Sort threats by current record (descending)
        threats.sort((a, b) -> Integer.compare(b.getWins(), a.getWins()));

        sb.append(String.format("  1. Finish %d-%d (lose all remaining games)\n", worstWins, worstLosses));

        // Filter threats to only show teams that need to DO something (win games) to
        // threaten
        List<Team> activeThreats = new ArrayList<>();
        for (Team threat : threats) {
            // Only include if they're not already at or above the threshold
            if (threat.getWins() < worstWins) {
                activeThreats.add(threat);
            }
        }

        if (activeThreats.isEmpty()) {
            sb.append("  2. Other teams already at same or better record - tiebreakers determine outcome\n");
        } else {
            sb.append(String.format("  2. These teams need to win games to catch up:\n"));
            for (Team threat : activeThreats) {
                int winsNeeded = worstWins - threat.getWins();
                sb.append(String.format("     - %s (needs to win %d of remaining games)\n",
                        threat.getTeamName(), winsNeeded));
            }
        }

        // Separate tiebreakers into already lost vs need to lose
        List<String> alreadyLost = new ArrayList<>();
        List<String> needToLose = new ArrayList<>();

        for (Team threat : threats) {
            int h2hWins = team.getHeadToHeadWins(threat.getTeamId());
            int h2hLosses = team.getHeadToHeadLosses(threat.getTeamId());
            boolean playAgain = team.getRemainingOpponents().contains(threat.getTeamId());

            if (h2hLosses > h2hWins && !playAgain) {
                // Already locked in - can't change
                alreadyLost.add(String.format("     - Loses H2H to %s (%d-%d)",
                        threat.getTeamName(), h2hWins, h2hLosses));
            } else if (h2hWins == h2hLosses && !playAgain) {
                double pointsDiff = team.getPointsFor() - threat.getPointsFor();
                if (pointsDiff < 0) {
                    // Already behind - locked in
                    alreadyLost.add(String.format("     - Behind %s by %.1f pts",
                            threat.getTeamName(), Math.abs(pointsDiff)));
                } else {
                    // Need to be outscored - can change
                    needToLose.add(String.format("     - Get outscored by %s by %.1f+ pts",
                            threat.getTeamName(), pointsDiff + 0.1));
                }
            } else if (playAgain) {
                // H2H could change this week
                needToLose.add(String.format("     - Lose to %s this week AND lose pts tiebreaker",
                        threat.getTeamName()));
            }
        }

        // Only show tiebreakers that can still change
        if (!needToLose.isEmpty()) {
            sb.append("  3. AND these tiebreakers must go against them:\n");
            for (String item : needToLose) {
                sb.append(item).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Generate miracle scenario for a "Likely Out" team showing how they could make
     * playoffs.
     */
    private String generateMiracleScenario(int teamId) {
        StringBuilder sb = new StringBuilder();
        Team team = league.getTeam(teamId);

        // Best case record for this team
        int bestWins = team.getWins() + team.getRemainingOpponents().size(); // win all
        int bestLosses = team.getLosses();

        // Find teams that could tie or fall below this record
        List<Team> targets = new ArrayList<>();
        for (Team other : league.getTeams().values()) {
            if (other.getTeamId() == teamId)
                continue;

            String otherStatus = getTeamStatus(other.getTeamId());
            if (otherStatus.equals("CLINCHED") || otherStatus.equals("ELIMINATED")) {
                continue;
            }

            // Worst possible record for other team
            int otherWorstWins = other.getWins();

            // Could they finish with same or worse record?
            if (otherWorstWins <= bestWins) {
                targets.add(other);
            }
        }

        // Sort targets by current record (ascending - worst teams first)
        targets.sort((a, b) -> Integer.compare(a.getWins(), b.getWins()));

        sb.append(String.format("  1. Win all remaining games to finish %d-%d\n", bestWins, bestLosses));

        // Filter targets to only show teams that need to lose games
        List<Team> activeTargets = new ArrayList<>();
        for (Team target : targets) {
            if (target.getWins() > bestWins) {
                activeTargets.add(target);
            }
        }

        if (activeTargets.isEmpty()) {
            sb.append("  2. Already tied with other bubble teams on record\n");
        } else {
            sb.append("  2. These teams need to lose games:\n");
            for (Team target : activeTargets) {
                int lossesNeeded = target.getWins() - bestWins;
                sb.append(String.format("     - %s (needs to lose %d of remaining games)\n",
                        target.getTeamName(), lossesNeeded));
            }
        }

        // Show specific tiebreaker requirements
        sb.append("  3. AND win these specific tiebreakers:\n");
        for (Team target : targets) {
            int h2hWins = team.getHeadToHeadWins(target.getTeamId());
            int h2hLosses = team.getHeadToHeadLosses(target.getTeamId());
            boolean playAgain = team.getRemainingOpponents().contains(target.getTeamId());

            if (h2hWins > h2hLosses && !playAgain) {
                sb.append(String.format("     - Already wins H2H vs %s (%d-%d) ✓\n",
                        target.getTeamName(), h2hWins, h2hLosses));
            } else if (h2hWins == h2hLosses && !playAgain) {
                double pointsDiff = team.getPointsFor() - target.getPointsFor();
                if (pointsDiff > 0) {
                    sb.append(String.format("     - Already ahead of %s by %.1f points ✓\n",
                            target.getTeamName(), pointsDiff));
                } else {
                    sb.append(String.format("     - Outscore %s by %.1f+ points\n",
                            target.getTeamName(), Math.abs(pointsDiff) + 0.1));
                }
            } else if (playAgain) {
                sb.append(String.format("     - Beat %s this week AND win points tiebreaker\n",
                        target.getTeamName()));
            }
        }

        return sb.toString();
    }

    /**
     * Analyze tiebreaker risks for a "Likely In" team.
     */
    private String analyzeTiebreakerRisks(int teamId) {
        StringBuilder sb = new StringBuilder();
        Team team = league.getTeam(teamId);

        // Find teams that could potentially tie with this team on record
        List<Team> riskyTiebreakers = new ArrayList<>();
        for (Team other : league.getTeams().values()) {
            if (other.getTeamId() == teamId)
                continue;

            // Skip teams that are clinched or eliminated - they're not competing for same
            // spots
            String otherStatus = getTeamStatus(other.getTeamId());
            if (otherStatus.equals("CLINCHED") || otherStatus.equals("ELIMINATED")) {
                continue;
            }

            // Check if this team could end up with the same record
            int thisTeamMinWins = team.getWins();
            int thisTeamMaxWins = team.getWins() + team.getRemainingOpponents().size();
            int otherMinWins = other.getWins();
            int otherMaxWins = other.getWins() + other.getRemainingOpponents().size();

            // If win ranges overlap, they could tie
            if (!(thisTeamMaxWins < otherMinWins || otherMaxWins < thisTeamMinWins)) {
                // Check H2H tiebreaker
                int h2hWins = team.getHeadToHeadWins(other.getTeamId());
                int h2hLosses = team.getHeadToHeadLosses(other.getTeamId());

                // Check if they play each other in remaining games
                boolean playAgain = team.getRemainingOpponents().contains(other.getTeamId());

                // Only include if this is a risky tiebreaker scenario:
                // 1. Team loses H2H (and they don't play again, so it's locked in)
                // 2. Teams are tied in H2H (goes to points or they play again)
                if ((h2hLosses > h2hWins && !playAgain) || h2hWins == h2hLosses) {
                    riskyTiebreakers.add(other);
                }
                // Skip if team wins H2H and they don't play again - team always wins this
                // tiebreaker
            }
        }

        if (riskyTiebreakers.isEmpty()) {
            sb.append("No risky tiebreaker scenarios - wins all potential head-to-head tiebreakers.\n");
            return sb.toString();
        }

        sb.append("Risky tiebreaker scenarios:\n");
        for (Team other : riskyTiebreakers) {
            int h2hWins = team.getHeadToHeadWins(other.getTeamId());
            int h2hLosses = team.getHeadToHeadLosses(other.getTeamId());
            double pointsDiff = team.getPointsFor() - other.getPointsFor();
            boolean playAgain = team.getRemainingOpponents().contains(other.getTeamId());

            sb.append(String.format("  vs %s:\n", other.getTeamName()));
            sb.append(String.format("    H2H: %d-%d", h2hWins, h2hLosses));
            if (playAgain) {
                sb.append(" (play again this week - outcome TBD)\n");
            } else if (h2hWins > h2hLosses) {
                sb.append(" (wins H2H tiebreaker)\n");
            } else if (h2hLosses > h2hWins) {
                sb.append(" (loses H2H tiebreaker) ⚠️\n");
            } else {
                // Tied on H2H, show what needs to happen with points
                if (pointsDiff > 0) {
                    // Currently ahead - show how much they can afford to be outscored
                    sb.append(String.format(" - Can be outscored by up to %.1f points\n", pointsDiff));
                } else {
                    // Currently behind - show how much they need to outscore by
                    sb.append(String.format(" - Must outscore by %.1f points ⚠️\n", Math.abs(pointsDiff) + 0.1));
                }
            }
        }

        return sb.toString();
    }
}
