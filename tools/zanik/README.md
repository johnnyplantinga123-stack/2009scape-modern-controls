# ZanikNet — Discord bot for 2009scape (Zanik)

ZanikNet is a small Discord bot for the 2009scape Runescape emulation project.  
It's written in C# using the Discord.Net library and provides integration points such as sending credit-claim requests to a configured channel and other community automation utilities.

## Features
- Written in C# using Discord.Net.
- Sends credit claim requests to a configured Discord channel.
- Lightweight and intended to run alongside 2009scape emulation services.

## Prerequisites
- .NET SDK 9.0.
- A Discord application with a bot token and appropriate permissions.
- Required environment variables (see table below).

## Environment variables

| Variable name | Required | Purpose |
|---|:---:|---|
| `ZANIK_API_TOKEN` | Yes | Discord bot token used to authenticate with the Discord API. Provide the token string from the Discord Developer Portal. |
| `ZANIK_CLAIM_CHANNEL` | Yes | Discord channel ID (ulong) where the bot will send credit claim requests. Use the numeric channel ID (e.g. 123456789012345678). |
| `ZANIK_RESTART_FILE` | No | Optional filesystem path where the bot will create a zero-length file when the /restart command is invoked. Use this with an external supervisor that watches the file to perform an automated restart. |

Examples

Windows PowerShell
```powershell
$env:ZANIK_API_TOKEN = "YOUR_DISCORD_BOT_TOKEN"
$env:ZANIK_CLAIM_CHANNEL = "123456789012345678"
$env:ZANIK_RESTART_FILE = "C:\path\to\restart-signal.file"
```

Linux / macOS (bash)
```bash
export ZANIK_API_TOKEN="YOUR_DISCORD_BOT_TOKEN"
export ZANIK_CLAIM_CHANNEL="123456789012345678"
export ZANIK_RESTART_FILE="/path/to/restart-signal.file"
```

> Note: The bot validates required environment variables on startup and will exit with an error if they are missing or invalid. ZANIK_RESTART_FILE is optional; when set, the /restart command creates the file before exiting so an external supervisor can act.

## Included scripts

This repository includes a small helper shell script for Linux/macOS: `start-zanik.sh` (located at the repository root). Briefly, the script:

- Exports example environment variables (`ZANIK_API_TOKEN`, `ZANIK_CLAIM_CHANNEL`, `ZANIK_RESTART_FILE`) — edit or remove these lines to use externally provided variables.
- Executes the bot binary (`./ZanikNet`) and will replace it with `./updated-bin/ZanikNet` if that file exists.
- Watches the restart sentinel file (`ZANIK_RESTART_FILE`). When the bot creates that file (the `/restart` command writes it), the script deletes the file and restarts the bot.

Quick usage (from the repo root):
```bash
chmod +x start-zanik.sh
./start-zanik.sh
```

Edit `start-zanik.sh` to set real token and channel ID values, or export those environment variables elsewhere and remove the example exports in the script.

## Quick start
1. Ensure the environment variables are set.
2. Build:
   dotnet build
3. Run:
   dotnet run --project path/to/your/DiscordBotProject.csproj

## Contributing
Contributions are welcome. Keep changes focused and include any verification steps.

## License
See the repository root for license information.
