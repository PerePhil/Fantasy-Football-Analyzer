# Configuration File Notes

## Overview

- **File**: `config.json` 
- **Location**: Root of project directory
- **Format**: JSON

## Fields

### Required
- `leagueId` (integer): Your ESPN Fantasy Football League ID

### Optional
- `year` (integer): Season year, defaults to current year if not specified
- `espnS2` (string): ESPN S2 authentication cookie for private leagues
- `swid` (string): SWID authentication cookie for private leagues  
- `verbose` (boolean): Enable detailed team analysis output
