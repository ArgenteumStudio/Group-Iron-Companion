# BowlsofMayo

An opt-in RuneLite plugin for Group Ironman tracking, with a self-hosted web
dashboard and Discord bot.

This is an independent project and is not affiliated with Jagex or RuneLite.

## What it includes

- **RuneLite plugin:** share selected game data with your group's server.
- **Web dashboard:** view member stats, banks, activity and locations; manage
  group access and customise the panel.
- **Discord bot:** verify the group owner, issue access and receive opt-in
  game-event alerts.

## Screenshots

![Fictional group dashboard](screenshots/demo-overview.png)

<details>
<summary>More screenshots</summary>

**Web panel settings**

![Group and website settings](screenshots/settings-group-website.png)
![Website color settings](screenshots/settings-website-colors.png)
![Discord message settings](screenshots/settings-discord-titles-tiers.png)
![Settings preview and save](screenshots/settings-preview-save.png)

**Fictional demo**

![Fictional shared banks](screenshots/demo-banks.png)
![Fictional locations and activity](screenshots/demo-locations-activity.png)
![Fictional world map](screenshots/demo-world-map.png)

</details>

## Self-hosting

Each group runs its own backend and Discord bot. The host needs Node.js 22 or
newer. Java 11 or newer is needed to build the plugin.

1. In `backend`, install dependencies and create private configuration files:

   ```powershell
   Set-Location backend
   npm ci
   Copy-Item .env.example .env
   Copy-Item members.example.json members.json
   Copy-Item branding.example.json branding.json
   ```

2. Create a Discord bot. Enable **Message Content Intent** and **Server Members
   Intent**, invite it to a private channel with permission to view, send messages
   and read history, then set `DISCORD_TOKEN`, `DISCORD_GUILD_ID` and
   `DISCORD_CHANNEL_ID` in `.env`.
3. Add the owner's Discord user ID to `members.json`. The owner needs **Manage
   Server** permission.
4. Start the service from `backend`:

   ```powershell
   npm start
   ```

5. In the configured Discord channel, the owner runs `!verify` and receives a
   private access key. Use it to open the dashboard and generate a separate
   character token for each player.
6. In RuneLite, enable **BowlsofMayo Clan Tracker**, set the bridge URL and the
   player's token. Review the consent warning and enable connection; optional
   sharing features remain off until selected.

For local use, the backend defaults to `http://localhost:4000`. Remote plugin
connections require HTTPS. The backend itself serves HTTP, so use a hosting
platform or other HTTPS front end; keep the backend listener private. A reverse
proxy is one option, not a required component.

Keep `.env`, `members.json`, `branding.json` and `backend/data` private. Never
publish bot tokens, access keys, real player data or backups. Send each character
token only to its intended player, and keep the owner key private. Anyone who can
read the configured Discord channel can see messages posted there.

## Development

Build and check the plugin, then run the backend tests:

```powershell
.\gradlew.bat jar verifyFeatures
Set-Location backend
npm ci
npm test
```

To preview the website with fictional data, run `npm run preview` from `backend`
and open `http://127.0.0.1:4173`. The preview does not use real group data or send
Discord notifications.
