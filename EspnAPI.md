# ESPN Fantasy Football API Guide

This document explains how to query the ESPN Fantasy Football API for **public leagues** (no authentication required).

## Base URL

```
https://lm-api-reads.fantasy.espn.com/apis/v3/games/ffl/seasons/{year}/segments/0/leagues/{leagueId}
```

### URL Parameters

| Parameter | Description | Example |
|-----------|-------------|---------|
| `{year}` | The NFL season year | `2024` |
| `{leagueId}` | Your ESPN Fantasy Football league ID | `12345678` |

### Finding Your League ID

Your league ID can be found in your ESPN Fantasy Football league URL:
```
https://fantasy.espn.com/football/league?leagueId=12345678
                                                  ^^^^^^^^
```

---

## View Parameters

The API uses query parameters called "views" to specify what data to return. Add them to the URL like:

```
?view=mTeam&view=mMatchup&view=mSettings&view=mSchedule
```

### Available Views

| View | Description |
|------|-------------|
| `mTeam` | Team information (names, records, points) |
| `mMatchup` | Current and past matchup data |
| `mSettings` | League settings (playoff teams, schedule info) |
| `mSchedule` | Full season schedule |
| `mRoster` | Team rosters and player data |
| `mScoreboard` | Scoreboard data |

---

## Making a Request

### Full URL Example

```
https://lm-api-reads.fantasy.espn.com/apis/v3/games/ffl/seasons/2024/segments/0/leagues/12345678?view=mTeam&view=mMatchup&view=mSettings&view=mSchedule
```

### cURL Example

```bash
curl "https://lm-api-reads.fantasy.espn.com/apis/v3/games/ffl/seasons/2024/segments/0/leagues/12345678?view=mTeam&view=mMatchup&view=mSettings&view=mSchedule"
```

### Java Example (using Unirest)

```java
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;

String url = String.format(
    "https://lm-api-reads.fantasy.espn.com/apis/v3/games/ffl/seasons/%d/segments/0/leagues/%d",
    year, leagueId
);
url += "?view=mTeam&view=mMatchup&view=mSettings&view=mSchedule";

HttpResponse<JsonNode> response = Unirest.get(url).asJson();

if (response.isSuccess()) {
    JSONObject data = response.getBody().getObject();
    // Process the data
}
```

### JavaScript/Node.js Example

```javascript
const leagueId = 12345678;
const year = 2024;

const url = `https://lm-api-reads.fantasy.espn.com/apis/v3/games/ffl/seasons/${year}/segments/0/leagues/${leagueId}?view=mTeam&view=mMatchup&view=mSettings&view=mSchedule`;

const response = await fetch(url);
const data = await response.json();
```

### Python Example

```python
import requests

league_id = 12345678
year = 2024

url = f"https://lm-api-reads.fantasy.espn.com/apis/v3/games/ffl/seasons/{year}/segments/0/leagues/{league_id}"
params = {
    "view": ["mTeam", "mMatchup", "mSettings", "mSchedule"]
}

response = requests.get(url, params=params)
data = response.json()
```

---

## Response Structure

The API returns a JSON object with the following key fields:

### Top-Level Fields

```json
{
  "id": 12345678,
  "scoringPeriodId": 14,
  "settings": { ... },
  "teams": [ ... ],
  "schedule": [ ... ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `id` | integer | League ID |
| `scoringPeriodId` | integer | Current week number |
| `settings` | object | League configuration |
| `teams` | array | Array of team objects |
| `schedule` | array | Array of matchup objects |

---

### Settings Object

```json
{
  "settings": {
    "name": "My Fantasy League",
    "scheduleSettings": {
      "playoffTeamCount": 6,
      "matchupPeriodCount": 14
    }
  }
}
```

| Field | Description |
|-------|-------------|
| `name` | League name |
| `scheduleSettings.playoffTeamCount` | Number of teams that make playoffs |
| `scheduleSettings.matchupPeriodCount` | Number of regular season weeks |

---

### Team Object

Each team in the `teams` array has this structure:

```json
{
  "id": 1,
  "location": "Philadelphia",
  "nickname": "Eagles",
  "record": {
    "overall": {
      "wins": 8,
      "losses": 5,
      "ties": 0,
      "pointsFor": 1456.78,
      "pointsAgainst": 1234.56
    }
  }
}
```

| Field | Description |
|-------|-------------|
| `id` | Unique team identifier |
| `location` | First part of team name |
| `nickname` | Second part of team name |
| `record.overall.wins` | Total wins |
| `record.overall.losses` | Total losses |
| `record.overall.ties` | Total ties |
| `record.overall.pointsFor` | Total points scored |
| `record.overall.pointsAgainst` | Total points against |

**Team Name:** Combine `location` + `nickname` for full team name (e.g., "Philadelphia Eagles")

---

### Schedule/Matchup Object

Each matchup in the `schedule` array has this structure:

```json
{
  "matchupPeriodId": 10,
  "home": {
    "teamId": 1,
    "totalPoints": 125.5
  },
  "away": {
    "teamId": 5,
    "totalPoints": 118.2
  }
}
```

| Field | Description |
|-------|-------------|
| `matchupPeriodId` | Week number |
| `home.teamId` | Home team's ID |
| `home.totalPoints` | Home team's score (if game completed) |
| `away.teamId` | Away team's ID |
| `away.totalPoints` | Away team's score (if game completed) |

**Determining Winner:** Compare `home.totalPoints` vs `away.totalPoints` to determine the winner.

**Completed Games:** A game is completed if `matchupPeriodId < scoringPeriodId`.

---

## Parsing Example (Java)

Here's how to parse the response into usable objects:

```java
JSONObject data = response.getBody().getObject();

// Get league settings
JSONObject settings = data.getJSONObject("settings");
String leagueName = settings.optString("name", "Unknown League");
JSONObject scheduleSettings = settings.getJSONObject("scheduleSettings");
int playoffTeams = scheduleSettings.getInt("playoffTeamCount");
int regularSeasonWeeks = scheduleSettings.getInt("matchupPeriodCount");

// Get current week
int currentWeek = data.optInt("scoringPeriodId", 1);

// Parse teams
JSONArray teams = data.getJSONArray("teams");
for (int i = 0; i < teams.length(); i++) {
    JSONObject teamJson = teams.getJSONObject(i);
    int teamId = teamJson.getInt("id");
    String name = teamJson.optString("location", "") + " " + teamJson.optString("nickname", "");
    
    JSONObject record = teamJson.getJSONObject("record").getJSONObject("overall");
    int wins = record.getInt("wins");
    int losses = record.getInt("losses");
    double pointsFor = record.getDouble("pointsFor");
}

// Parse matchups
JSONArray schedule = data.getJSONArray("schedule");
for (int i = 0; i < schedule.length(); i++) {
    JSONObject matchup = schedule.getJSONObject(i);
    int week = matchup.getInt("matchupPeriodId");
    
    // Skip playoff games
    if (week > regularSeasonWeeks) continue;
    
    JSONObject home = matchup.getJSONObject("home");
    JSONObject away = matchup.getJSONObject("away");
    
    int homeTeamId = home.getInt("teamId");
    int awayTeamId = away.getInt("teamId");
    
    // Check if game is completed
    if (week < currentWeek) {
        double homeScore = home.getDouble("totalPoints");
        double awayScore = away.getDouble("totalPoints");
    }
}
```

---

## Error Handling

| HTTP Status | Meaning |
|-------------|---------|
| `200` | Success |
| `401` | Unauthorized (private league, needs authentication) |
| `404` | League not found or invalid year |
| `500` | Server error |

### Example Error Check

```java
if (!response.isSuccess()) {
    throw new Exception("Failed to fetch league data: HTTP " + response.getStatus());
}
```

---

## Notes

- This is an **unofficial API** and may change without notice
- Public leagues require no authentication
- For private leagues, see the authentication section in `README.md`
- Rate limiting may apply; avoid excessive requests
- The API returns all data in a single request when using multiple views
