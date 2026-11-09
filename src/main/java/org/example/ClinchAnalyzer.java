package org.example;

import org.example.ScenarioEngine.Fate;
import org.example.ScenarioEngine.Result;
import org.example.ScenarioEngine.Status;
import org.example.models.League;
import org.example.models.Matchup;
import org.example.models.Team;
import org.example.utils.BooleanMinimizer;
import org.example.utils.BooleanMinimizer.Term;

import java.util.*;

/**
 * Turns the full search into clinch and elimination scenarios.
 * Every statement here is exact over the remaining win/loss outcomes.
 */
public class ClinchAnalyzer {
    private static final String RULE = "=".repeat(78);
    private static final String LINE = "-".repeat(78);

    private final League league;
    private final ScenarioEngine engine;
    private final Result result;

    public ClinchAnalyzer(League league, ScenarioEngine engine) {
        this.league = league;
        this.engine = engine;
        this.result = engine.run();
    }

    public Status getStatus(int teamId) {
        return result.status(teamId);
    }

    /** Short status label for the standings table. */
    public String getStatusLabel(int teamId) {
        Fate fate = result.fate(teamId);
        switch (fate) {
            case CLINCHED:
                return "CLINCHED";
            case ELIMINATED:
                return "ELIMINATED";
            case IN_UNLESS_POINTS:
                return "Alive - can only miss on points";
            case OUT_UNLESS_POINTS:
                return "Alive - needs points tiebreaker";
            case POINTS_DECIDE:
                return "Alive - decided by points";
            default:
                break;
        }
        Team team = league.getTeam(teamId);
        if (team.getRemainingOpponents().isEmpty()) {
            return "Alive - needs help";
        }
        switch (result.winOut(teamId)) {
            case CLINCHED:
                return "Alive - controls own destiny";
            case IN_UNLESS_POINTS:
                return "Alive - win out + points";
            default:
                return "Alive - needs help";
        }
    }

    /** Teams ordered by status, then win %, then points for. */
    public List<Team> getSortedTeams() {
        List<Team> teams = new ArrayList<>(league.getTeams().values());
        teams.sort(Comparator
                .comparingInt((Team t) -> statusRank(result.status(t.getTeamId())))
                .thenComparing(Comparator.comparingDouble(Team::getWinPercentage).reversed())
                .thenComparing(Comparator.comparingDouble(Team::getPointsFor).reversed()));
        return teams;
    }

    private static int statusRank(Status s) {
        switch (s) {
            case CLINCHED:
                return 0;
            case ALIVE:
                return 1;
            default:
                return 2;
        }
    }

    public String generateSummaryReport() {
        StringBuilder sb = new StringBuilder();
        sb.append('\n').append(RULE).append('\n');
        sb.append("PLAYOFF CLINCH REPORT\n");
        sb.append(RULE).append('\n');
        sb.append(league).append('\n');
        sb.append("Tiebreakers: win % -> head-to-head among tied teams -> total points for\n");
        sb.append(RULE).append("\n\n");

        sb.append(String.format("%-30s %8s %9s  %s%n", "Team", "Record", "PF", "Status"));
        sb.append(LINE).append('\n');
        for (Team t : getSortedTeams()) {
            sb.append(String.format("%-30s %8s %9.2f  %s%n",
                    truncate(t.getTeamName(), 30), t.getRecord(), t.getPointsFor(),
                    getStatusLabel(t.getTeamId())));
        }
        sb.append(LINE).append('\n');
        sb.append(String.format("Remaining games: %d | Win/loss outcomes checked: %,d%n",
                engine.getGames().size(), engine.getTotalOutcomes()));
        sb.append("CLINCHED / ELIMINATED hold for every outcome, however points go.\n");
        return sb.toString();
    }

    public String generateClinchScenariosReport() {
        StringBuilder sb = new StringBuilder();
        sb.append('\n').append(RULE).append('\n');
        sb.append("CLINCH SCENARIOS\n");
        sb.append(RULE).append("\n\n");

        boolean any = false;
        for (Team t : getSortedTeams()) {
            if (result.status(t.getTeamId()) == Status.ALIVE) {
                any = true;
                sb.append(getTeamScenarios(t.getTeamId())).append('\n');
            }
        }
        if (!any) {
            sb.append("Every team has clinched or been eliminated.\n");
        }
        return sb.toString();
    }

    /** Full scenario breakdown for one team. */
    public String getTeamScenarios(int teamId) {
        Team team = league.getTeam(teamId);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("### %s (%s, %.2f PF) - %s%n",
                team.getTeamName(), team.getRecord(), team.getPointsFor(), getStatusLabel(teamId)));

        Fate fate = result.fate(teamId);
        if (fate == Fate.IN_UNLESS_POINTS) {
            sb.append("  Makes the playoffs in every outcome unless a total-points tiebreaker goes against them.\n");
        } else if (fate == Fate.OUT_UNLESS_POINTS) {
            sb.append("  Misses the playoffs in every outcome unless they win a total-points tiebreaker.\n");
        } else if (fate == Fate.POINTS_DECIDE) {
            sb.append("  Every outcome comes down to a total-points tiebreaker.\n");
        }

        if (!team.getRemainingOpponents().isEmpty()) {
            sb.append(String.format("  Win out:  %s%n", describe(result.winOut(teamId))));
            sb.append(String.format("  Lose out: %s%n", describe(result.loseOut(teamId))));
        }

        appendWeekScenarios(sb, teamId);
        appendMustHaves(sb, teamId);
        appendPointsRivals(sb, team);
        return sb.toString();
    }

    private void appendWeekScenarios(StringBuilder sb, int teamId) {
        int k = engine.getWeekGameCount();
        if (k == 0) {
            return;
        }
        boolean finalWeek = engine.getGames().size() == k;
        Map<Fate, List<Integer>> byFate = new EnumMap<>(Fate.class);
        for (int o = 0; o < (1 << k); o++) {
            byFate.computeIfAbsent(result.fateAfterWeek(teamId, o), f -> new ArrayList<>()).add(o);
        }

        sb.append(String.format("  Week %d%s:%n", engine.getNextWeek(), finalWeek ? " (final week)" : ""));
        appendCondition(sb, "Clinches if", byFate.get(Fate.CLINCHED), k);
        appendCondition(sb, "Eliminated if", byFate.get(Fate.ELIMINATED), k);
        appendCondition(sb, "In unless they lose a points tiebreaker if", byFate.get(Fate.IN_UNLESS_POINTS), k);
        appendCondition(sb, "Out unless they win a points tiebreaker if", byFate.get(Fate.OUT_UNLESS_POINTS), k);
        appendCondition(sb, "Decided by a points tiebreaker if", byFate.get(Fate.POINTS_DECIDE), k);
        if (!byFate.containsKey(Fate.CLINCHED) && !byFate.containsKey(Fate.ELIMINATED)
                && !byFate.containsKey(Fate.IN_UNLESS_POINTS) && !byFate.containsKey(Fate.OUT_UNLESS_POINTS)
                && !byFate.containsKey(Fate.POINTS_DECIDE)) {
            sb.append("    Can't clinch or be eliminated this week.\n");
        }
    }

    private void appendCondition(StringBuilder sb, String label, List<Integer> outcomes, int k) {
        if (outcomes == null || outcomes.isEmpty()) {
            return;
        }
        sb.append("    ").append(label).append(":\n");
        List<Term> terms = BooleanMinimizer.minimize(k, outcomes);
        boolean first = true;
        for (Term term : terms) {
            sb.append(first ? "      - " : "      - OR ").append(describe(term)).append('\n');
            first = false;
        }
    }

    private void appendMustHaves(StringBuilder sb, int teamId) {
        Map<Integer, Boolean> must = result.mustHaveResults(teamId);
        if (must.isEmpty()) {
            return;
        }
        sb.append("  Must happen to stay alive:\n");
        for (Map.Entry<Integer, Boolean> e : must.entrySet()) {
            Matchup m = engine.getGames().get(e.getKey());
            sb.append(String.format("    - Week %d: %s%n", m.getWeek(), describe(m, e.getValue())));
        }
    }

    private void appendPointsRivals(StringBuilder sb, Team team) {
        List<Integer> rivals = result.pointsRivals(team.getTeamId());
        if (rivals.isEmpty()) {
            return;
        }
        sb.append("  Can end up in a total-points tiebreaker with:\n");
        for (int id : rivals) {
            Team other = league.getTeam(id);
            double diff = team.getPointsFor() - other.getPointsFor();
            String gap = diff >= 0
                    ? String.format("%s leads by %.2f", team.getTeamName(), diff)
                    : String.format("%s leads by %.2f", other.getTeamName(), -diff);
            sb.append(String.format("    - %s (%s)%n", other.getTeamName(), gap));
        }
    }

    private String describe(Term term) {
        if (term.care() == 0) {
            return "any results";
        }
        List<String> parts = new ArrayList<>();
        for (int g = 0; g < engine.getWeekGameCount(); g++) {
            if ((term.care() >> g & 1) != 0) {
                boolean homeWins = (term.value() >> g & 1) == 0;
                parts.add(describe(engine.getGames().get(g), homeWins));
            }
        }
        return String.join(" AND ", parts);
    }

    private String describe(Matchup m, boolean homeWins) {
        Team winner = league.getTeam(homeWins ? m.getHomeTeamId() : m.getAwayTeamId());
        Team loser = league.getTeam(homeWins ? m.getAwayTeamId() : m.getHomeTeamId());
        return winner.getTeamName() + " beats " + loser.getTeamName();
    }

    private static String describe(Fate fate) {
        switch (fate) {
            case CLINCHED:
                return "clinches";
            case IN_UNLESS_POINTS:
                return "in unless they lose a points tiebreaker";
            case OUT_UNLESS_POINTS:
                return "out unless they win a points tiebreaker";
            case POINTS_DECIDE:
                return "decided by a points tiebreaker";
            case ELIMINATED:
                return "eliminated";
            case UNDECIDED:
                return "depends on other results";
            default:
                return "n/a";
        }
    }

    /** Remaining schedule, for verbose output. */
    public String getRemainingSchedule() {
        StringBuilder sb = new StringBuilder();
        int week = -1;
        for (Matchup m : engine.getGames()) {
            if (m.getWeek() != week) {
                week = m.getWeek();
                sb.append(String.format("Week %d%n", week));
            }
            sb.append(String.format("  %s vs %s%n",
                    league.getTeam(m.getHomeTeamId()).getTeamName(),
                    league.getTeam(m.getAwayTeamId()).getTeamName()));
        }
        return sb.toString();
    }

    private static String truncate(String str, int maxLength) {
        return str.length() <= maxLength ? str : str.substring(0, maxLength - 3) + "...";
    }
}
