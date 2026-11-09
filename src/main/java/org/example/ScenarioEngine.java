package org.example;

import org.example.models.League;
import org.example.models.Matchup;
import org.example.models.Team;
import org.example.utils.TieBreaker;

import java.util.*;
import java.util.stream.IntStream;

/**
 * Goes through every win/loss outcome of the remaining regular-season games
 * and records, for each team, whether it is in, out, or down to a points
 * tiebreaker. Nothing is estimated: CLINCHED means in for every outcome no
 * matter how points go, and ELIMINATED means out for every outcome no matter
 * how points go.
 *
 * The work is split by the results of the next week's games. Each of those
 * results is one parallel task that goes through every later outcome. That
 * gives exact "clinches this week if..." answers in the same pass.
 *
 * Game ties are not counted as a possible result of a remaining game.
 */
public class ScenarioEngine {
    /** Above this many remaining games a full search is not practical. */
    public static final int MAX_GAMES = 34;
    /** Most games one week can have (each result of the week is reported separately). */
    public static final int MAX_WEEK_GAMES = 12;
    /** The search is split on at least this many games so every core has work. */
    private static final int MIN_SPLIT_GAMES = 8;

    private static final int SEEN_IN = 1 << TieBreaker.IN;
    private static final int SEEN_POINTS = 1 << TieBreaker.POINTS;
    private static final int SEEN_OUT = 1 << TieBreaker.OUT;

    private final League league;
    private final int n;
    private final int spots;
    private final int[] teamIds;
    private final Map<Integer, Integer> indexOf = new HashMap<>();

    // Starting state, by team index.
    private final int[] wins, losses, ties;
    private final int[][] h2h;
    private final double[] pointsFor;
    private final boolean[] hasGamesLeft;

    // Remaining games: this week's first (bits 0..weekGames-1), then the rest.
    private final List<Matchup> games = new ArrayList<>();
    private final int weekGames;
    private final int splitGames;
    private final int nextWeek;
    private final int[] home, away;
    private final long[] teamGameMask; // games the team plays in
    private final long[] teamAwayMask; // games where the team is away (bit 1 = away team wins)

    private Result result;

    public ScenarioEngine(League league) {
        this.league = league;
        List<Team> teamList = new ArrayList<>(league.getTeams().values());
        teamList.sort(Comparator.comparingInt(Team::getTeamId));
        this.n = teamList.size();
        if (n > 64) {
            throw new IllegalArgumentException("Leagues over 64 teams are not supported");
        }
        this.spots = Math.min(league.getNumberOfPlayoffTeams(), n);

        teamIds = new int[n];
        wins = new int[n];
        losses = new int[n];
        ties = new int[n];
        h2h = new int[n][n];
        pointsFor = new double[n];
        hasGamesLeft = new boolean[n];
        for (int i = 0; i < n; i++) {
            Team t = teamList.get(i);
            teamIds[i] = t.getTeamId();
            indexOf.put(t.getTeamId(), i);
            wins[i] = t.getWins();
            losses[i] = t.getLosses();
            ties[i] = t.getTies();
            pointsFor[i] = t.getPointsFor();
        }
        for (int i = 0; i < n; i++) {
            Team t = teamList.get(i);
            for (int j = 0; j < n; j++) {
                h2h[i][j] = t.getHeadToHeadWins(teamIds[j]);
            }
        }

        List<Matchup> remaining = new ArrayList<>();
        for (Matchup m : league.getRemainingMatchups()) {
            if (indexOf.containsKey(m.getHomeTeamId()) && indexOf.containsKey(m.getAwayTeamId())) {
                remaining.add(m);
            }
        }
        remaining.sort(Comparator.comparingInt(Matchup::getWeek));
        if (remaining.size() > MAX_GAMES) {
            throw new IllegalStateException(String.format(
                    "%d games remain; a full search is only practical up to %d. Run again later in the season.",
                    remaining.size(), MAX_GAMES));
        }
        games.addAll(remaining);
        nextWeek = games.isEmpty() ? -1 : games.get(0).getWeek();
        int k = 0;
        while (k < games.size() && games.get(k).getWeek() == nextWeek) {
            k++;
        }
        weekGames = k;
        if (weekGames > MAX_WEEK_GAMES) {
            throw new IllegalStateException(String.format(
                    "%d games in week %d; at most %d are supported.", weekGames, nextWeek, MAX_WEEK_GAMES));
        }
        splitGames = Math.min(games.size(), Math.max(weekGames, MIN_SPLIT_GAMES));

        home = new int[games.size()];
        away = new int[games.size()];
        teamGameMask = new long[n];
        teamAwayMask = new long[n];
        for (int g = 0; g < games.size(); g++) {
            home[g] = indexOf.get(games.get(g).getHomeTeamId());
            away[g] = indexOf.get(games.get(g).getAwayTeamId());
            hasGamesLeft[home[g]] = true;
            hasGamesLeft[away[g]] = true;
            teamGameMask[home[g]] |= 1L << g;
            teamGameMask[away[g]] |= 1L << g;
            teamAwayMask[away[g]] |= 1L << g;
        }
    }

    /** Run the full search. Safe to call once; later calls return the cached result. */
    public synchronized Result run() {
        if (result != null) {
            return result;
        }
        Task[] tasks = IntStream.range(0, 1 << splitGames)
                .parallel()
                .mapToObj(this::runTask)
                .toArray(Task[]::new);
        result = new Result(tasks);
        return result;
    }

    /** Everything recorded for one result of the first {@code splitGames} games. */
    private final class Task {
        final int[] seen = new int[n];          // SEEN_* bits over all later outcomes
        final long[] rivals = new long[n];       // points-tiebreak rivals
        final long[] aliveOr = new long[n];      // OR of outcomes where the team isn't OUT
        final long[] aliveAnd = new long[n];     // AND of the same
        final int[] winOutSeen = new int[n];
        final int[] loseOutSeen = new int[n];

        // Working state for this task.
        final int[] w = wins.clone(), l = losses.clone(), t = ties.clone();
        final int[][] hh = new int[n][];
        final int[] state = new int[n];
        final long[] leafRivals = new long[n];
        final TieBreaker tb = new TieBreaker(spots, pointsFor, hasGamesLeft);

        Task() {
            for (int i = 0; i < n; i++) {
                hh[i] = h2h[i].clone();
                aliveAnd[i] = -1L;
            }
        }
    }

    private Task runTask(int prefix) {
        Task task = new Task();
        for (int g = 0; g < splitGames; g++) {
            apply(task, g, ((prefix >> g) & 1) == 0);
        }
        search(task, splitGames, prefix);
        return task;
    }

    private void search(Task task, int g, long path) {
        if (g == games.size()) {
            record(task, path);
            return;
        }
        apply(task, g, true);
        search(task, g + 1, path);
        undo(task, g, true);

        apply(task, g, false);
        search(task, g + 1, path | (1L << g));
        undo(task, g, false);
    }

    private void apply(Task task, int g, boolean homeWins) {
        int win = homeWins ? home[g] : away[g];
        int lose = homeWins ? away[g] : home[g];
        task.w[win]++;
        task.l[lose]++;
        task.hh[win][lose]++;
    }

    private void undo(Task task, int g, boolean homeWins) {
        int win = homeWins ? home[g] : away[g];
        int lose = homeWins ? away[g] : home[g];
        task.w[win]--;
        task.l[lose]--;
        task.hh[win][lose]--;
    }

    private void record(Task task, long path) {
        task.tb.evaluate(task.w, task.l, task.t, task.hh, task.state, task.leafRivals);
        for (int i = 0; i < n; i++) {
            int s = task.state[i];
            int bit = 1 << s;
            task.seen[i] |= bit;
            if (s == TieBreaker.POINTS) {
                task.rivals[i] |= task.leafRivals[i];
            }
            if (s != TieBreaker.OUT) {
                task.aliveOr[i] |= path;
                task.aliveAnd[i] &= path;
            }
            long mine = path & teamGameMask[i];
            if (mine == teamAwayMask[i]) {
                task.winOutSeen[i] |= bit;
            }
            if (mine == (teamGameMask[i] & ~teamAwayMask[i])) {
                task.loseOutSeen[i] |= bit;
            }
        }
    }

    // ------------------------------------------------------------------
    // Results
    // ------------------------------------------------------------------

    /** Overall status of a team. */
    public enum Status {
        /** In for every outcome, however points go. */
        CLINCHED,
        /** Out for every outcome, however points go. */
        ELIMINATED,
        /** Still undecided. */
        ALIVE
    }

    /** What a set of outcomes means for one team. */
    public enum Fate {
        /** Makes the playoffs every time, however points go. */
        CLINCHED,
        /** Never out on record; only a total-points tiebreaker can knock them out. */
        IN_UNLESS_POINTS,
        /** Never in on record; only a total-points tiebreaker can get them in. */
        OUT_UNLESS_POINTS,
        /** Every outcome comes down to a total-points tiebreaker. */
        POINTS_DECIDE,
        /** Misses every time, however points go. */
        ELIMINATED,
        /** Depends on later results. */
        UNDECIDED,
        /** No outcomes to judge (e.g. the team has no remaining games for win-out). */
        NONE;

        static Fate of(int seen) {
            if (seen == 0)
                return NONE;
            if (seen == SEEN_IN)
                return CLINCHED;
            if (seen == SEEN_OUT)
                return ELIMINATED;
            if (seen == SEEN_POINTS)
                return POINTS_DECIDE;
            if ((seen & SEEN_OUT) == 0)
                return IN_UNLESS_POINTS;
            if ((seen & SEEN_IN) == 0)
                return OUT_UNLESS_POINTS;
            return UNDECIDED;
        }
    }

    public final class Result {
        private final int[][] weekSeen; // [this week's outcome][team]
        private final int[] seen = new int[n];
        private final long[] rivals = new long[n];
        private final long[] aliveOr = new long[n];
        private final long[] aliveAnd = new long[n];
        private final int[] winOutSeen = new int[n];
        private final int[] loseOutSeen = new int[n];

        private Result(Task[] tasks) {
            weekSeen = new int[1 << weekGames][n];
            Arrays.fill(aliveAnd, -1L);
            int weekBits = (1 << weekGames) - 1;
            for (int t = 0; t < tasks.length; t++) {
                Task task = tasks[t];
                int[] ws = weekSeen[t & weekBits]; // this week's games are the low bits
                for (int i = 0; i < n; i++) {
                    ws[i] |= task.seen[i];
                    seen[i] |= task.seen[i];
                    rivals[i] |= task.rivals[i];
                    aliveOr[i] |= task.aliveOr[i];
                    aliveAnd[i] &= task.aliveAnd[i];
                    winOutSeen[i] |= task.winOutSeen[i];
                    loseOutSeen[i] |= task.loseOutSeen[i];
                }
            }
        }

        public Status status(int teamId) {
            Fate f = fate(teamId);
            if (f == Fate.CLINCHED)
                return Status.CLINCHED;
            if (f == Fate.ELIMINATED)
                return Status.ELIMINATED;
            return Status.ALIVE;
        }

        /** Fate over all remaining outcomes. */
        public Fate fate(int teamId) {
            return Fate.of(seen[idx(teamId)]);
        }

        /** Fate once this week's games go a given way, over all later outcomes. */
        public Fate fateAfterWeek(int teamId, int weekOutcome) {
            return Fate.of(weekSeen[weekOutcome][idx(teamId)]);
        }

        /** Fate if the team wins all its remaining games. */
        public Fate winOut(int teamId) {
            return Fate.of(winOutSeen[idx(teamId)]);
        }

        /** Fate if the team loses all its remaining games. */
        public Fate loseOut(int teamId) {
            return Fate.of(loseOutSeen[idx(teamId)]);
        }

        /** Teams this one can end up in a total-points tiebreaker with. */
        public List<Integer> pointsRivals(int teamId) {
            List<Integer> out = new ArrayList<>();
            long mask = rivals[idx(teamId)];
            for (int j = 0; j < n; j++) {
                if ((mask >> j & 1) != 0)
                    out.add(teamIds[j]);
            }
            return out;
        }

        /**
         * Games whose result is the same in every outcome where the team is
         * still alive. Map of game index to whether the home team must win.
         */
        public Map<Integer, Boolean> mustHaveResults(int teamId) {
            int i = idx(teamId);
            Map<Integer, Boolean> out = new LinkedHashMap<>();
            if ((seen[i] & (SEEN_IN | SEEN_POINTS)) == 0) {
                return out; // never alive
            }
            for (int g = 0; g < games.size(); g++) {
                long bit = 1L << g;
                if ((aliveOr[i] & bit) == 0) {
                    out.put(g, true);   // away team never wins in a live outcome
                } else if ((aliveAnd[i] & bit) != 0) {
                    out.put(g, false);  // away team always wins in a live outcome
                }
            }
            return out;
        }
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    private int idx(int teamId) {
        Integer i = indexOf.get(teamId);
        if (i == null)
            throw new IllegalArgumentException("Unknown team " + teamId);
        return i;
    }

    public League getLeague() {
        return league;
    }

    /** Remaining games in search order (this week's first). */
    public List<Matchup> getGames() {
        return Collections.unmodifiableList(games);
    }

    /** Number of remaining games in the next week to be played. */
    public int getWeekGameCount() {
        return weekGames;
    }

    /** The next week to be played, or -1 if none remain. */
    public int getNextWeek() {
        return nextWeek;
    }

    public int getPlayoffSpots() {
        return spots;
    }

    public long getTotalOutcomes() {
        return 1L << games.size();
    }
}
