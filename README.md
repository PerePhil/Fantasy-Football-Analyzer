# Fantasy Football Playoff Odds Simulator

A Java application that calculates playoff odds for ESPN Fantasy Football leagues by simulating all possible outcomes of remaining games.

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
- For leagues with >20 remaining games, simulation may take significant time (2^20 = 1M+ scenarios)
- Assumes equal probability for all game outcomes (50/50 win probability)

## Acknowledgments

- ESPN Fantasy Football for the unofficial API
- Inspired by playoff odds calculators for professional sports