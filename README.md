# Fantasy Football Playoff Clinch Scenarios

A Java application that finds exact playoff clinch and elimination scenarios for ESPN Fantasy Football leagues by checking every win/loss outcome of the remaining regular-season games.

## What it reports

- **CLINCHED / ELIMINATED**: in (or out) for every remaining outcome, however total points end up.
- **This week's scenarios** for every team still alive, e.g. "Clinches if: Iron beats Granite OR (Marble beats Cobalt AND Basalt beats Slate)".
- Whether a team **controls its own destiny** (clinches by winning out), and what happens if it loses out.
- Results that **must happen** for a team to stay alive.
- Teams it can end up in a **total-points tiebreaker** with. When a tie comes down to points, the team is shown as points-dependent, because final points totals aren't known yet.

## Tiebreakers

1. Win percentage
2. Head-to-head record among the tied teams (combined record for 3+ teams; any smaller group still tied starts over at head-to-head)
3. Total points for

## Requirements

- Java 23 (or compatible version)
- Maven
- ESPN Fantasy Football League ID

### Quick Start

2. **Edit `config.json`** with your league settings:
   - Replace `leagueId` with your ESPN Fantasy League ID
   - Update `year` if needed (defaults to current year if omitted)
   - For **private leagues**, add your `espnS2` and `swid` cookies (see below)
   - Set `verbose` to `true` for detailed team analysis

### Getting ESPN Cookies (Private Leagues Only)

For private leagues, you'll need your ESPN authentication cookies:

1. Log into ESPN Fantasy Football in your browser
2. Open Developer Tools (F12)
3. Go to Application/Storage > Cookies > espn.com
4. Copy the values for `espn_s2` and `SWID`
5. Add them to your `config.json`:

```json
{
  "leagueId": 123456,
  "year": 2024,
  "espnS2": "AEBxyz123...",
  "swid": "{ABC-123-DEF-456}",
  "verbose": false
}
```

## Limitations

- Uses ESPN's unofficial API (may change without notice)
- Checks all $2^n$ outcomes for $n$ remaining games. On 4 cores: 24 games (12 teams, 4 weeks left) takes about 2 seconds; 30 games (12 teams, 5 weeks left) takes a few minutes. More than 34 remaining games is refused.
- A remaining game ending in an exact tie is not treated as a possible result.
- Divisions and extra games (such as a weekly league-median win) are not modelled. A warning is printed if ESPN's standings don't match the schedule.

## Acknowledgments

- ESPN Fantasy Football for the unofficial API
- Inspired by playoff odds calculators for professional sports