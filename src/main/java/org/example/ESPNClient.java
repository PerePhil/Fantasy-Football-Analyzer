package org.example;

import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.json.JSONArray;
import kong.unirest.json.JSONObject;
import org.example.models.League;
import org.example.models.Matchup;
import org.example.models.Team;

import java.util.HashMap;
import java.util.Map;

/**
 * Client for interacting with ESPN Fantasy Football API.
 */
public class ESPNClient {
    private static final String BASE_URL = "https://lm-api-reads.fantasy.espn.com/apis/v3/games/ffl/seasons";
    private final int leagueId;
    private final int year;
    private final String espnS2;
    private final String swid;

    public ESPNClient(int leagueId, int year) {
        this(leagueId, year, null, null);
    }

    public ESPNClient(int leagueId, int year, String espnS2, String swid) {
        this.leagueId = leagueId;
        this.year = year;
        this.espnS2 = espnS2;
        this.swid = swid;
    }

    /**
     * Fetches league data from ESPN API and constructs a League object.
     */
    public League fetchLeagueData() throws Exception {
        String url = String.format("%s/%d/segments/0/leagues/%d", BASE_URL, year, leagueId);

        // Add view parameters to get all needed data
        url += "?view=mTeam&view=mMatchup&view=mSettings&view=mSchedule";

        HttpResponse<JsonNode> response;

        // Add authentication cookies if provided (for private leagues)
        if (espnS2 != null && swid != null) {
            response = Unirest.get(url)
                    .cookie("espn_s2", espnS2)
                    .cookie("SWID", swid)
                    .asJson();
        } else {
            response = Unirest.get(url).asJson();
        }

        if (!response.isSuccess()) {
            throw new Exception("Failed to fetch league data: HTTP " + response.getStatus());
        }

        JSONObject data = response.getBody().getObject();
        return parseLeagueData(data);
    }

    /**
     * Parse the JSON response into a League object.
     */
    private League parseLeagueData(JSONObject data) {
        // Extract league settings
        JSONObject settings = data.getJSONObject("settings");
        String leagueName = settings.optString("name", "Unknown League");

        JSONObject scheduleSettings = settings.getJSONObject("scheduleSettings");
        int playoffTeamCount = scheduleSettings.getInt("playoffTeamCount");
        int regularSeasonMatchupPeriods = scheduleSettings.getInt("matchupPeriodCount");

        // Get current week/scoring period
        int currentMatchupPeriod = data.optInt("scoringPeriodId", 1);
        int currentWeek = currentMatchupPeriod;

        // Playoff start week is after regular season
        int playoffStartWeek = regularSeasonMatchupPeriods + 1;

        League league = new League(
                leagueId,
                leagueName,
                year,
                playoffTeamCount,
                currentWeek,
                playoffStartWeek,
                regularSeasonMatchupPeriods);

        // Parse teams
        JSONArray teams = data.getJSONArray("teams");
        Map<Integer, Team> teamMap = new HashMap<>();

        for (int i = 0; i < teams.length(); i++) {
            JSONObject teamJson = teams.getJSONObject(i);
            Team team = parseTeam(teamJson);
            league.addTeam(team);
            teamMap.put(team.getTeamId(), team);
        }

        // Parse schedule/matchups
        JSONArray schedule = data.getJSONArray("schedule");
        for (int i = 0; i < schedule.length(); i++) {
            JSONObject matchupJson = schedule.getJSONObject(i);

            // Only include regular season matchups
            int matchupPeriodId = matchupJson.getInt("matchupPeriodId");
            if (matchupPeriodId > regularSeasonMatchupPeriods) {
                continue; // Skip playoff games
            }

            Matchup matchup = parseMatchup(matchupJson, matchupPeriodId, currentWeek);
            league.addMatchup(matchup);

            // Process completed games
            if (matchup.isCompleted()) {
                // Record head-to-head results
                Team home = teamMap.get(matchup.getHomeTeamId());
                Team away = teamMap.get(matchup.getAwayTeamId());

                if (home != null && away != null) {
                    if (matchup.getWinnerId() != null) {
                        if (matchup.getWinnerId() == matchup.getHomeTeamId()) {
                            // Home team won
                            home.recordHeadToHeadWin(matchup.getAwayTeamId());
                            away.recordHeadToHeadLoss(matchup.getHomeTeamId());
                        } else {
                            // Away team won
                            away.recordHeadToHeadWin(matchup.getHomeTeamId());
                            home.recordHeadToHeadLoss(matchup.getAwayTeamId());
                        }
                    }
                    // Note: ties don't affect head-to-head tiebreaker
                }
            } else {
                // Add remaining opponents to teams for future games
                Team home = teamMap.get(matchup.getHomeTeamId());
                Team away = teamMap.get(matchup.getAwayTeamId());
                if (home != null) {
                    home.addRemainingOpponent(matchup.getAwayTeamId());
                }
                if (away != null) {
                    away.addRemainingOpponent(matchup.getHomeTeamId());
                }
            }
        }

        return league;
    }

    /**
     * Parse team data from JSON.
     */
    private Team parseTeam(JSONObject teamJson) {
        int teamId = teamJson.getInt("id");

        // Team name can be in different places
        String teamName;
        if (teamJson.has("location") && teamJson.has("nickname")) {
            String location = teamJson.optString("location", "");
            String nickname = teamJson.optString("nickname", "");
            teamName = (location + " " + nickname).trim();
        } else if (teamJson.has("name")) {
            teamName = teamJson.getString("name");
        } else {
            teamName = "Team " + teamId;
        }

        Team team = new Team(teamId, teamName);

        // Parse record
        JSONObject record = teamJson.getJSONObject("record");
        JSONObject overall = record.getJSONObject("overall");

        team.setWins(overall.getInt("wins"));
        team.setLosses(overall.getInt("losses"));
        team.setTies(overall.optInt("ties", 0));

        // Parse points
        team.setPointsFor(overall.getDouble("pointsFor"));
        team.setPointsAgainst(overall.getDouble("pointsAgainst"));

        return team;
    }

    /**
     * Parse matchup data from JSON.
     */
    private Matchup parseMatchup(JSONObject matchupJson, int week, int currentWeek) {
        // Get home and away teams
        JSONObject home = matchupJson.getJSONObject("home");
        JSONObject away = matchupJson.getJSONObject("away");

        int homeTeamId = home.getInt("teamId");
        int awayTeamId = away.getInt("teamId");

        // Check if game has been completed
        boolean isCompleted = week < currentWeek;

        if (isCompleted) {
            double homeScore = home.getDouble("totalPoints");
            double awayScore = away.getDouble("totalPoints");
            return new Matchup(week, homeTeamId, awayTeamId, homeScore, awayScore);
        } else {
            return new Matchup(week, homeTeamId, awayTeamId);
        }
    }
}
