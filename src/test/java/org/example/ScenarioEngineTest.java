package org.example;

import org.example.ScenarioEngine.Fate;
import org.example.ScenarioEngine.Result;
import org.example.ScenarioEngine.Status;
import org.example.models.League;
import org.example.models.Matchup;
import org.example.models.Team;
import org.example.utils.BooleanMinimizer;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioEngineTest {

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /** records[i] = {wins, losses}; team ids are 1..n; pf[i] = points for. */
    static League league(int spots, int[][] records, double[] pf) {
        League lg = new League(1, "Test", 2026, spots, 10, 15, 14);
        for (int i = 0; i < records.length; i++) {
            Team t = new Team(i + 1, String.valueOf((char) ('A' + i)));
            t.setWins(records[i][0]);
            t.setLosses(records[i][1]);
            t.setPointsFor(pf[i]);
            lg.addTeam(t);
        }
        return lg;
    }

    static void game(League lg, int week, int home, int away) {
        lg.addMatchup(new Matchup(week, home, away));
        lg.getTeam(home).addRemainingOpponent(away);
        lg.getTeam(away).addRemainingOpponent(home);
    }

    static void beat(League lg, int winner, int loser) {
        lg.getTeam(winner).recordHeadToHeadWin(loser);
        lg.getTeam(loser).recordHeadToHeadLoss(winner);
    }

    // ---------------------------------------------------------------
    // Cases the old bounds check got wrong
    // ---------------------------------------------------------------

    @Test
    void clinchesWhenTheOnlyThreatsPlayEachOther() {
        // 2 spots. B and C play each other twice, so they can't both reach A's 5 wins.
        League lg = league(2, new int[][] { { 5, 3 }, { 4, 4 }, { 3, 5 }, { 0, 8 } }, new double[] { 1, 2, 3, 4 });
        game(lg, 9, 1, 4);
        game(lg, 9, 2, 3);
        game(lg, 10, 1, 4);
        game(lg, 10, 2, 3);
        Result r = new ScenarioEngine(lg).run();
        assertEquals(Status.CLINCHED, r.status(1));
        assertEquals(Status.ELIMINATED, r.status(4));
    }

    @Test
    void eliminatedWhenLeadersPlayEachOther() {
        // 1 spot. B and C are both on 4 wins and play each other, so one reaches 5; X tops out at 4.
        League lg = league(1, new int[][] { { 3, 6 }, { 4, 5 }, { 4, 5 }, { 0, 9 } }, new double[] { 1, 2, 3, 4 });
        game(lg, 10, 1, 4);
        game(lg, 10, 2, 3);
        Result r = new ScenarioEngine(lg).run();
        assertEquals(Status.ELIMINATED, r.status(1));
    }

    // ---------------------------------------------------------------
    // Tiebreakers
    // ---------------------------------------------------------------

    @Test
    void threeWayHeadToHeadCycleGoesToPointsWhenSeasonIsOver() {
        // A beat B, B beat C, C beat A: head-to-head can't split them, so points decide.
        League lg = league(1, new int[][] { { 8, 6 }, { 8, 6 }, { 8, 6 }, { 2, 12 } },
                new double[] { 1300, 1500, 1400, 1600 });
        beat(lg, 1, 2);
        beat(lg, 2, 3);
        beat(lg, 3, 1);
        Result r = new ScenarioEngine(lg).run();
        assertEquals(Status.CLINCHED, r.status(2)); // most points among the tied teams
        assertEquals(Status.ELIMINATED, r.status(1));
        assertEquals(Status.ELIMINATED, r.status(3));
    }

    @Test
    void threeWayTieUsesCombinedHeadToHead() {
        // A is 2-0 against B and C combined, so A wins the tie despite the fewest points.
        League lg = league(1, new int[][] { { 8, 6 }, { 8, 6 }, { 8, 6 } }, new double[] { 1000, 1500, 1400 });
        beat(lg, 1, 2);
        beat(lg, 1, 3);
        beat(lg, 2, 3);
        Result r = new ScenarioEngine(lg).run();
        assertEquals(Status.CLINCHED, r.status(1));
    }

    @Test
    void openPointsTieIsPointsDependentAndHeadToHeadSettlesIt() {
        // 1 spot, final week. A and B both 5-4; A plays C, B plays D (both winless).
        League lg = league(1, new int[][] { { 5, 4 }, { 5, 4 }, { 0, 9 }, { 0, 9 } },
                new double[] { 1000, 1200, 1, 1 });
        game(lg, 10, 1, 3); // game 0
        game(lg, 10, 2, 4); // game 1
        ScenarioEngine e = new ScenarioEngine(lg);
        Result r = e.run();
        // Bits: game0 then game1, 0 = home wins.
        assertEquals(Fate.CLINCHED, r.fateAfterWeek(1, 0b10));          // A wins, B loses
        assertEquals(Fate.POINTS_DECIDE, r.fateAfterWeek(1, 0b00));     // both win: down to points
        assertEquals(Fate.POINTS_DECIDE, r.fateAfterWeek(1, 0b11));     // both lose: down to points
        assertEquals(Fate.ELIMINATED, r.fateAfterWeek(1, 0b01));        // A loses, B wins
        assertEquals(List.of(2), r.pointsRivals(1));

        // Same week, but A already beat B: A clinches with any win.
        League lg2 = league(1, new int[][] { { 5, 4 }, { 5, 4 }, { 0, 9 }, { 0, 9 } },
                new double[] { 1000, 1200, 1, 1 });
        beat(lg2, 1, 2);
        game(lg2, 10, 1, 3);
        game(lg2, 10, 2, 4);
        Result r2 = new ScenarioEngine(lg2).run();
        assertEquals(Fate.CLINCHED, r2.fateAfterWeek(1, 0b00));
        assertEquals(Fate.CLINCHED, r2.fateAfterWeek(1, 0b10));
        assertTrue(r2.pointsRivals(1).isEmpty());
        assertEquals(Fate.CLINCHED, r2.winOut(1));
    }

    @Test
    void lockedPointsLeadIsNotPointsDependent() {
        // A and B tie on record, no head-to-head. B leads on points and A has no games left,
        // so A can't catch up even though B is still playing.
        League lg = league(1, new int[][] { { 6, 4 }, { 5, 4 }, { 0, 9 } }, new double[] { 1000, 1200, 1 });
        game(lg, 10, 2, 3);
        Result r = new ScenarioEngine(lg).run();
        assertEquals(Fate.CLINCHED, r.fateAfterWeek(1, 0b1)); // B loses
        assertEquals(Fate.ELIMINATED, r.fateAfterWeek(1, 0b0)); // B wins: 6-4 each, B ahead for good
    }

    // ---------------------------------------------------------------
    // Week conditions
    // ---------------------------------------------------------------

    @Test
    void minimizerProducesShortConditions() {
        // True when bit0 == 0, regardless of bit1: one term on bit 0.
        List<BooleanMinimizer.Term> t = BooleanMinimizer.minimize(2, List.of(0b00, 0b10));
        assertEquals(List.of(new BooleanMinimizer.Term(0, 0b01)), t);
        // XOR needs two terms.
        assertEquals(2, BooleanMinimizer.minimize(2, List.of(0b01, 0b10)).size());
        // Always true.
        assertEquals(List.of(new BooleanMinimizer.Term(0, 0)), BooleanMinimizer.minimize(1, List.of(0, 1)));
    }

    // ---------------------------------------------------------------
    // Cross-check against an independent brute force
    // ---------------------------------------------------------------

    @Test
    void matchesReferenceOnRandomLeagues() {
        Random rnd = new Random(7);
        for (int trial = 0; trial < 300; trial++) {
            int n = 4 + rnd.nextInt(4);           // 4..7 teams
            int spots = 1 + rnd.nextInt(n - 1);
            int weeks = 1 + rnd.nextInt(3);
            int[][] rec = new int[n][2];
            double[] pf = new double[n];
            for (int i = 0; i < n; i++) {
                rec[i][0] = rnd.nextInt(4);
                rec[i][1] = 3 - rec[i][0];
                pf[i] = 900 + rnd.nextInt(400) + i * 0.001; // distinct
            }
            League lg = league(spots, rec, pf);
            for (int i = 1; i <= n; i++)
                for (int j = 1; j <= n; j++)
                    if (i != j && rnd.nextInt(4) == 0)
                        beat(lg, i, j);
            List<int[]> games = new ArrayList<>();
            for (int w = 0; w < weeks; w++) {
                List<Integer> ids = new ArrayList<>();
                for (int i = 1; i <= n; i++)
                    ids.add(i);
                Collections.shuffle(ids, rnd);
                for (int p = 0; p + 1 < ids.size(); p += 2) {
                    game(lg, 10 + w, ids.get(p), ids.get(p + 1));
                    games.add(new int[] { 10 + w, ids.get(p), ids.get(p + 1) });
                }
            }

            ScenarioEngine e = new ScenarioEngine(lg);
            Result r = e.run();
            Reference ref = new Reference(lg, e.getGames(), spots);
            for (int id = 1; id <= n; id++) {
                assertEquals(ref.fate(id, -1), r.fate(id), "trial " + trial + " team " + id);
                for (int o = 0; o < (1 << e.getWeekGameCount()); o++) {
                    assertEquals(ref.fate(id, o), r.fateAfterWeek(id, o),
                            "trial " + trial + " team " + id + " week outcome " + o);
                }
            }
        }
    }

    /**
     * Independent check: for each outcome, decide in/out under worst-case and
     * best-case points by actually setting final points (+huge for teams with
     * games left on one side, +0 on the other) and running a plain sort.
     */
    static final class Reference {
        final League lg;
        final List<Matchup> games;
        final int spots;
        final int weekGames;

        Reference(League lg, List<Matchup> games, int spots) {
            this.lg = lg;
            this.games = games;
            this.spots = spots;
            int k = 0;
            while (k < games.size() && games.get(k).getWeek() == games.get(0).getWeek())
                k++;
            this.weekGames = k;
        }

        Fate fate(int teamId, int weekOutcome) {
            int seen = 0;
            for (long o = 0; o < (1L << games.size()); o++) {
                if (weekOutcome >= 0 && (o & ((1L << weekGames) - 1)) != weekOutcome)
                    continue;
                boolean worstIn = isIn(teamId, o, false);
                boolean bestIn = isIn(teamId, o, true);
                seen |= worstIn ? 1 : bestIn ? 2 : 4;
            }
            if (seen == 1)
                return Fate.CLINCHED;
            if (seen == 4)
                return Fate.ELIMINATED;
            if (seen == 2)
                return Fate.POINTS_DECIDE;
            if ((seen & 4) == 0)
                return Fate.IN_UNLESS_POINTS;
            if ((seen & 1) == 0)
                return Fate.OUT_UNLESS_POINTS;
            return Fate.UNDECIDED;
        }

        boolean isIn(int teamId, long outcome, boolean favourable) {
            Map<Integer, Team> t = new HashMap<>();
            for (Team x : lg.getTeams().values())
                t.put(x.getTeamId(), new Team(x));
            Set<Integer> playing = new HashSet<>();
            for (int g = 0; g < games.size(); g++) {
                Matchup m = games.get(g);
                boolean homeWins = (outcome >> g & 1) == 0;
                int w = homeWins ? m.getHomeTeamId() : m.getAwayTeamId();
                int l = homeWins ? m.getAwayTeamId() : m.getHomeTeamId();
                t.get(w).addWin();
                t.get(l).addLoss();
                t.get(w).recordHeadToHeadWin(l);
                t.get(l).recordHeadToHeadLoss(w);
                playing.add(w);
                playing.add(l);
            }
            for (Team x : t.values()) {
                boolean boost = playing.contains(x.getTeamId()) && (x.getTeamId() == teamId) == favourable;
                if (boost)
                    x.addPointsFor(1e6);
            }
            List<Team> order = rank(new ArrayList<>(t.values()));
            for (int i = 0; i < spots; i++)
                if (order.get(i).getTeamId() == teamId)
                    return true;
            return false;
        }

        static List<Team> rank(List<Team> teams) {
            teams.sort(Comparator.comparingDouble(Team::getWinPercentage).reversed());
            List<Team> out = new ArrayList<>();
            int i = 0;
            while (i < teams.size()) {
                int j = i;
                while (j < teams.size() && teams.get(j).getWinPercentage() == teams.get(i).getWinPercentage())
                    j++;
                out.addAll(h2h(new ArrayList<>(teams.subList(i, j))));
                i = j;
            }
            return out;
        }

        static List<Team> h2h(List<Team> group) {
            if (group.size() == 1)
                return group;
            Map<Team, Double> pct = new HashMap<>();
            for (Team a : group) {
                int w = 0, l = 0;
                for (Team b : group) {
                    w += a.getHeadToHeadWins(b.getTeamId());
                    l += b.getHeadToHeadWins(a.getTeamId());
                }
                pct.put(a, w + l == 0 ? 0.5 : (double) w / (w + l));
            }
            group.sort(Comparator.comparingDouble((Team a) -> pct.get(a)).reversed());
            if (pct.get(group.get(0)).equals(pct.get(group.get(group.size() - 1)))) {
                group.sort(Comparator.comparingDouble(Team::getPointsFor).reversed());
                return group;
            }
            List<Team> out = new ArrayList<>();
            int i = 0;
            while (i < group.size()) {
                int j = i;
                while (j < group.size() && pct.get(group.get(j)).equals(pct.get(group.get(i))))
                    j++;
                out.addAll(h2h(new ArrayList<>(group.subList(i, j))));
                i = j;
            }
            return out;
        }
    }
}
