# BowlsofMayo - the private group pantry

A RuneLite plugin for opt-in Group Ironman tracking, with a private dashboard and Discord alerts.
This project is independently developed and is not endorsed by Jagex or RuneLite.

## Screenshots

Settings page captures from the local build:

| Group and website | Website colors | Discord message styles | Preview and save |
| --- | --- | --- | --- |
| ![Group and website settings](screenshots/settings-group-website.png) | ![Website color settings](screenshots/settings-website-colors.png) | ![Discord message titles and drop tiers](screenshots/settings-discord-titles-tiers.png) | ![Discord preview and save controls](screenshots/settings-preview-save.png) |

Fictional demo captures:

| Overview | Banks | Locations and activity | Full-page map |
| --- | --- | --- | --- |
| ![Fictional group overview](screenshots/demo-overview.png) | ![Fictional shared banks](screenshots/demo-banks.png) | ![Fictional locations and activity](screenshots/demo-locations-activity.png) | ![Fictional full-page world map](screenshots/demo-world-map.png) |

## Features

### Privacy and explicit opt-in

The plugin itself and every boolean feature default to **off**. Merely entering
a URL/token does not connect. Enable **Enable connection / consent** only after
reading its warning and trusting the server operator. This new switch also
defaults off for existing installations; saved individual feature preferences
are not overwritten, so review them before opting in after an upgrade.

Connection consent permits sending your RuneScape name, skill levels/XP and
quest points to the configured host. Separate off-by-default switches control
banks/group storage, location/world, live XP, your own GIM chat and gameplay
alerts. The server operator can access, retain and redistribute uploads regardless
of member viewing permissions. Enabled alerts/chat can be forwarded to Discord.
HTTPS protects transport, not data from the operator. Do not send Jagex passwords.
Disabling connection clears pending local uploads and stops future requests;
an already in-flight request may finish. It does not erase server backups,
saved snapshots or Discord messages. Ask the operator for deletion.

- Tracker-style member cards show total level, combat level and total XP, plus an
  OSRS skill-icon grid with per-skill progress to the next level. Hover or keyboard-focus
  a skill to see exact XP and XP remaining. Refined forest-green surfaces, shield-style
  initials, clear training states and floating XP drops share the clan's editable
  color palette. Cards adapt to phones; drop animations respect reduced-motion settings.
- **Live XP drops** show the player, skill icon and exact XP gained as the client
  observes `StatChanged` events. A separate upload worker checks every 250 ms and
  authenticated long-poll viewers wake as soon as the server accepts a batch. Network
  and Discord membership verification can add latency; this is not a guaranteed
  sub-second service. Enable **Live XP drops** in the plugin (off by default;
  connection consent is also required). Rebuild and restart the client after upgrading.
  Login/world-hop values establish a baseline, boosted-level-only changes and XP
  decreases are not drops, and no offline gains are invented. The active skill
  highlight lasts 15 seconds. New drops spawn at the top-right of the matching card,
  rise and fade over three seconds, like in-game XP drops. Reduced-motion mode
  fades without movement. Login/reconnect history is not replayed as animations,
  and normal snapshot refreshes do not restart an active drop. There is no separate
  XP feed panel. The server keeps at most 500 events from the last five minutes in memory. It clears on server
  restart, is restricted to verified group members and never posts XP drops to Discord.
  Logout clears the cards and cancels pending requests. Failed uploads retry with the
  same IDs; drops older than 25 seconds expire with a warning instead of looking live.
  These ephemeral events do not affect the existing persisted XP chart or totals.
  Skill icons are served locally from RuneLite's bundled OSRS skill icons; game
  artwork belongs to Jagex. No group data is sent to image services.
- Discord `!stats playername` uses saved real levels and XP from the player's client,
  including a last-sync timestamp. It is not a Hiscores lookup and cannot fetch data
  from an account that has never connected.
- Group Ironman chat forwards to Discord. Each member relays **their own** messages,
  so every participating sender must run the plugin. Public, private, friends, and
  ordinary clan chat are not forwarded. There is no Discord-to-OSRS input automation.
- Boss kill/completion-count messages produce Discord alerts. Keep in-game boss
  kill-count chat messages enabled. Unsupported message formats cannot supply KC.
- RuneLite Loot Tracker events produce drop alerts, including supported raid/chest
  loot. The Loot Tracker plugin must be enabled. PvP player loot is excluded. Minimum
  stack value defaults to zero (all drops, including untradeables).
- Quest-state transitions produce completion alerts. Login establishes a baseline:
  quests completed before connection are not announced retroactively.
- View personal bank snapshots in RuneLite's sidebar and the web dashboard. Open
  your bank or Group Ironman storage after setup to capture it. **Share bank**
  covers both; the inventory displayed beside group storage is never collected.
  In RuneLite's right sidebar, select **Group storage** in the bank dropdown to
  view the latest shared snapshot, capture time and syncing member. Search works
  in both personal banks and shared storage, and the selection survives refreshes.
  Empty and not-yet-synced storage are distinguished. The sidebar respects the
  token's personal-bank and group-storage viewing permissions independently.
  Removed members disappear on the next successful sync (normally every five
  seconds). Failed syncs clear cached sidebar data and group map markers rather
  than leaving revoked-access snapshots visible. Changing the bridge URL or token
  also clears these views while the new connection is checked.
  Both views use a bank-style grid with real item sprites, quantity overlays and
  item-name tooltips. Website tooltips float outside the scrolling bank, so first-row
  items are not clipped; hover or keyboard-focus a slot, and Escape dismisses its
  tooltip. The website retains member filters and name search.
  The sidebar uses RuneLite's item renderer; the plugin uploads rendered PNGs in
  small batches to your authenticated backend for the website. Your real bank's
  item IDs are not sent to an external image service. Initial icon sync can take
  several minutes for a large bank; missing pictures are explicitly labelled.
  This is view-only, not an interactive bank for withdrawals or transfers.
- Group positions appear on RuneLite's in-game world map; different worlds are
  labelled and locations older than 30 seconds are hidden.
  Admins can upload a separate **In-game map icon** under the website's
  **Settings > Group**, or choose **Use default map dot**, then **Save changes**.
  It applies to both the local "You" marker and teammate markers, independently of
  the clan logo/sidebar icon. Upload PNG, JPEG, GIF or WebP up to 1 MB and
  2048 x 2048 pixels; the server stores a transparent, aspect-preserving 32px PNG.
  Fully transparent images are rejected. Only the first frame of an animated image
  is used. Updated clients check the icon about every 30 seconds while syncing;
  failed downloads report a sync error and retain the last working icon (or the
  original cyan dot). The new client build must be restarted once to enable this.
  The website's geographical player markers are unchanged.
- Original green/cream pantry-themed website with member stat cards, bank search,
  activity filters, and a geographical OSRS world map with coordinate-aligned markers.
  Drag or use arrow keys to pan, use the zoom buttons, select a floor, or use
  **Show on map** to open a full-page map following a player. **Open full-page map**
  expands the map without choosing a player; **Back to dashboard** returns to the
  overview. The dedicated view stays in the same authenticated tab (no keys or
  player names in the URL). **Fit group** fits fresh locations on the selected
  floor. World numbers are labelled; locations expire after 30 seconds. Enable
  **Share location** in the plugin to publish your location. Automatic floor selection
  follows live players when the previous floor becomes empty; manual floor selection
  is preserved. If client sync stops, an explicit stale-location message explains why
  the live marker is hidden rather than displaying old coordinates as current. Instanced areas use
  template coordinates and may not have geographical map coverage.
  Public tiles load directly from the OSRS Wiki map service; no player names,
  access keys, bank contents or snapshots are sent to it. The tile host sees ordinary
  browser image requests (including IP and requested map area). Attribution and
  licensing links appear below the map. Set `WORLD_MAP=off` to disable third-party
  tiles, or `MAP_VERSION=2026-10-20_a` to pin a map version. Tile/configuration
  failures are shown explicitly and can be retried with **Reload map**.
- Group XP-gained chart: one colored line per member and a ranked legend, with
  last hour, 24 hours, 7 days, 15 days, 30 days and all-time ranges. It adds positive
  observed XP changes across skills, not starting account XP. Each member starts
  at zero on their first sync after this feature is installed. New skill baselines
  and decreases are not treated as gains. Missing/late-joining history is labelled.
  Offline gains are attributed to the next sync; the tracker cannot know when
  those XP were earned. Period boundaries use the last minute observation at or
  before the boundary (roughly one-minute precision while continuously connected).
  Recent history retains minute endpoints for 31 days; older all-time history
  retains daily endpoints. The graph samples at most 243 points per member while
  preserving the final gain. Smooth, bounded curves connect observations without
  overshooting recorded XP; the curves are visual interpolation, not extra samples.
  History is private and stored in `state.json`.

Shared net worth, historical drop-rate calculations, and full quest comparisons are
not part of this implementation.

## Build and local checks

Requires JDK 11+ and Node.js 22+. Gradle's wrapper is included.

```powershell
Set-Location 'C:\path\to\runelite'
.\gradlew.bat jar verifyFeatures
Set-Location backend
npm ci
npm test
```

Gradle builds the plugin jar under `build/libs`. The Node.js tests cover the
Discord bot and web backend.

## Local website preview (no credentials or game data)

```powershell
Set-Location 'C:\path\to\runelite\backend'
npm run preview
```

Open `http://127.0.0.1:4173`, then choose **Take a tour**. Demo mode is clearly
labelled and contains fictional players. It cannot send Discord notifications or
access your real group. Stop with Ctrl+C.

## Customising your group

The easiest way is the website's **Settings** tab. It appears after unlocking the
dashboard for verified members who have Discord's **Manage Server** permission in your
server. Change the clan name, upload a logo, edit the website text and colours, and
tweak the Discord message titles, colours, drop tiers and milestones. A live Discord
preview updates as you type, and saving applies everything instantly with no restart.

Behind the scenes everything is stored in `backend/branding.json` (gitignored), which
you can also edit by hand. Start from
`backend/branding.example.json`; any setting you leave out uses the default, so a
two-line file with just `clanName` and `logo` is fine. Restart the backend after
editing the file by hand. Typos, unknown settings and invalid colours stop startup with a clear error.

| Setting | What it changes |
| --- | --- |
| `clanName` | Your group name on the website and in `{clan}` in the Discord footer. |
| `logo` | Path (relative to `branding.json`) to your clan logo: PNG, JPEG, GIF or WebP, up to 5 MB. Keep it in `backend/branding/` (gitignored). Without one, the website shows a badge with your initials. |
| `discord.footer` | Footer text on every embed. `{clan}` is replaced by `clanName`. |
| `discord.useLogoIcon` | Use your logo as the small icon on every embed (otherwise the OSRS stats icon). |
| `discord.colors.*` | Embed colours (`#RRGGBB`) for each loot tier, boss kills, milestones, quests, chat, stats and the `!verify` messages. |
| `discord.titles.*` | The label shown at the top of each embed type, e.g. "Loot Drop" or "Boss Kill". |
| `discord.lootTiers` | GP thresholds for the Valuable, Rare and Legendary drop styles. |
| `discord.milestones` | Kill-count milestones: every `every` kills, then every `largeEvery` kills from `largeFrom`. The first kill is always a milestone. |
| `website.title`, `tagline`, `heading`, `intro`, `dashboardHeading`, `dashboardSubtitle`, `bankHeading`, `activityHeading`, `footer` | Website text. Use `\n` in `heading` for a line break. |
| `website.colors.*` | Website colour scheme: background, panels, borders, text, muted text, accent and highlight. |

## Private backend and Discord setup

**Every group self-hosts.** Each group runs its own copy of this backend with its
own Discord bot. There is no central tracker service or tracker account.
No VPN, tunnel provider or paid hosting subscription is required. Only the host
installs the backend; friends use the website and RuneLite plugin with their own
member tokens. The plugin connects to the configured bridge. The bridge contacts
Discord for authentication and enabled alerts, and RuneLite's public item-icon
CDN for fallback images. Website map tiles contact the OSRS Wiki as described
above; enabled Discord alerts deliberately share their contents with Discord.

1. Install Node.js 22 or newer and Java 11 for plugin development.
   In `backend`, run `npm ci` to install the locked dependencies.
   Copy `.env.example` to `.env`, and copy
   `branding.example.json` to `branding.json` (see **Customising your group**
   below). Copy `members.example.json` to
   `members.json`. Both real files are gitignored. For the recommended single-owner
   setup, put **only the owner's Discord user ID** in `discordId`; that account
   needs Discord **Manage Server** permission. Ordinary members do not go in this
   file and need neither Discord nor `!verify`. Their fixed-character tokens are
   generated in **Manage group** after owner verification. Never use Jagex credentials.
   Existing one-to-five approved Discord-account configurations remain supported
   for co-owners; **each must have Manage Server** to issue or use a Discord key.
   Configured non-owners cannot bypass member viewing restrictions with `!verify`;
   use owner-issued character tokens for them.
   Owner characters link automatically (up to five) or can be set with `"name"`.
   `!characters` and `!unlink` still apply to those Discord-linked characters;
   owner-issued member tokens are removed using **Kick member** instead.
   Production still refuses legacy plaintext `token` fields in this configuration.
2. Enable developer mode in Discord and use **Copy User ID** on the owner.
   A username/display name is not a user ID. To remove an owner's approval,
   remove that account from `members.json` and restart the backend.
3. Create a Discord application/bot in the Discord Developer Portal. Enable the
   **Message Content Intent** (prefix commands) and **Server Members Intent**
   (membership-removal events). Both are required by the bot.
4. Invite it to your server with View Channel, Send Messages and Read Message
   History permissions in a dedicated **private channel**. Restrict channel access
   to your group: anyone who can read that channel can see alerts. Only approved
   Discord accounts can use `!stats` or obtain access keys.
5. In `.env`, set `DISCORD_TOKEN`, `DISCORD_GUILD_ID`, and `DISCORD_CHANNEL_ID`.
   Enable developer mode in Discord to copy IDs. The bot only answers commands
   in this configured guild/channel and ignores other bots. To split alerts, set
   any of `DISCORD_DROPS_CHANNEL_ID`, `DISCORD_KILLS_CHANNEL_ID`,
   `DISCORD_QUESTS_CHANNEL_ID` and `DISCORD_CHAT_CHANNEL_ID`; blank ones use
   `DISCORD_CHANNEL_ID`. The bot needs the same permissions in every alert channel.
   Set `DISCORD_STATS_CHANNEL_ID` to answer `!stats` in its own channel; `!verify`
   always stays in `DISCORD_CHANNEL_ID`.
   `!stats` replies with an embed laid out like the in-game skills tab. To use real
   skill icons, upload them as emojis on your server or bot application and create
   `backend/skill-emojis.json` (or set `SKILL_EMOJIS_FILE`), for example
   `{ "ATTACK": "<:attack:123456789012345678>", "TOTAL": "<:stats:123456789012345679>" }`.
   Keys are skill names in capitals plus `TOTAL` and `COMBAT`; missing keys use the
   default emoji.
6. Run `npm start` from `backend`. The server starts only after Discord login and
   channel validation succeed; authentication/configuration errors are explicit.
7. The **owner** types **`!verify`** in the configured bot channel. The bot
   checks membership and DMs a random 64-character access key; it never posts keys
   in the channel. Allow server-member DMs if delivery fails. Requests are limited
   to once per minute per approved account. A successful request replaces the
   previous owner key. Open the website with this private key, then use
   **Manage group > Generate member token** for each member's exact RuneScape name.
   Copy each displayed token and send it privately to its intended member.
   Only a SHA-256 hash is saved; a lost token must be replaced, not retrieved.
   **Never share the owner key.** Owner-key rotation does not rotate member tokens.
8. In RuneLite enable **BowlsofMayo Clan Tracker**, configure your bridge URL and
   paste their individual owner-issued token into **Member token**, then log in.
   The owner may use their own Discord-verified key for their own characters.
   Defaults point to `http://localhost:4000`.
   Read the privacy warning and explicitly enable **Enable connection / consent**
   to allow name, stats and quest-point uploads. Without this, no bridge requests
   are made, even with a saved URL/token. All optional features are **off by
   default**: turn on the options you want (Share bank,
   Share location, Forward GIM chat, Boss KC / Drop / Quest alerts). Stats and
   quest points sync only with connection consent and a valid token. Pick the sidebar button's look
   with **Sidebar icon**: your group logo from the website (default), a dot, a
   preset item, or any item via **Custom item ID**.
   Open your bank. Check the sidebar connection status.
9. Open the bridge URL in a browser and enter that same access key. After successful
   authentication it is saved in this tab's `sessionStorage` (not `localStorage`
   or the URL), and rechecked on refresh. **Refresh data** also updates without
   reloading. **Lock** clears saved access, as do rejected keys and entering the
   fictional demo. Browser session restore or tab duplication may retain a tab
   session; use Lock on shared computers. Browser storage failures are displayed.

### Standard server hosting

The backend is a normal Node.js HTTP server: `npm start` starts it and Ctrl+C
stops it. The host chooses their networking, firewall, process supervision and HTTPS
termination. A reverse proxy is one option, not a required product dependency;
a hosting platform or another HTTPS front end can provide TLS instead. Nothing
installs or configures a VPN, tunnel, router rule or reverse proxy automatically.

| Setting | Default | Purpose |
| --- | --- | --- |
| `HOST` | `127.0.0.1` | Address on which the HTTP server listens |
| `PORT` | `4000` | HTTP port |
| `DATA_FILE` | `data/state.json` | Persistent group data; back up privately |
| `MEMBERS_FILE` | `members.json` | Approved owner configuration |

- **Local testing:** leave the defaults and use `http://localhost:4000` on the
  server computer. `localhost` on a friend's computer points to their computer,
  not the host.
- **Remote friends:** provide a trusted HTTPS endpoint using your existing
  hosting platform or any reverse proxy you choose. Friends use the same
  `https://your-tracker-address` in their browser and RuneLite bridge setting;
  no extra networking software is required on their computers.
- **Same-machine proxy:** keep `HOST=127.0.0.1` and forward the full site/API
  to `http://127.0.0.1:4000`. Allow long-poll requests to complete; configure
  the proxy's read timeout above 30 seconds. Do not log Authorization headers.
- **Proxy on another machine/container:** bind to a suitable interface using
  `HOST` (for example `0.0.0.0` listens on all IPv4 interfaces), and firewall the
  backend port so only the trusted proxy can reach it. Binding on all interfaces
  does not add encryption or authentication at the network layer.

Remote plugin connections require HTTPS: the plugin rejects remote plain HTTP and
redirects to avoid exposing bearer tokens. The backend itself only speaks HTTP, so
remote hosting needs TLS from your hosting platform, a reverse proxy, or another
HTTPS front end. The choice of front end is yours; exposing this HTTP listener
directly to the internet is not a secure HTTPS setup and can expose tokens and
group data in transit. Keep the listener private behind your chosen HTTPS setup.

The login page may be internet-reachable; private tracker data remains protected
by member tokens and server-side viewing permissions. An unlisted URL is not an
access control. Keep the bot token in the server's private `.env`, never in the
plugin or website. Keep owner tokens private and issue a separate character
token to each friend. Do not publish `.env`, real member/branding configuration,
data, logs or backups on GitHub. Ignore rules are a safeguard, not a substitute
for checking the files being published.

Every authenticated API request rechecks the verified Discord account against
Discord (not a cached member list). For an owner-issued token this is its **issuing
owner**, not the ordinary member, and the owner must retain Manage Server permission.
Leaving/kicking/banning the owner revokes their key and issued member tokens when
the bot receives the removal event or the next request observes absence. Rejoining
requires `!verify` and newly issued member tokens. Losing Manage Server denies
the owner key and issued tokens when detected; restoring the role requires new
verification and newly issued member tokens. Discord checks fail closed during outages:
the API returns 503, not private data. A departure and rejoin entirely while the
bot is offline cannot be detected retrospectively; revoke suspected keys manually
or remove their approval. Already viewed/downloaded data cannot be recalled.
Keys are bearer credentials: send a member token only to its intended member and
never share the owner key. Verification proves access to an approved Discord account,
not ownership of a RuneScape character. A fixed name prevents a token uploading as
another character, but is not independent proof of in-game account ownership.
The public website shell and clearly fictional tour remain available without a key.

### Manage group: roles, tokens and viewing permissions

- **Owner:** the configured, verified Discord account with Manage Server permission.
  Can generate/replace tokens, kick members, promote/demote managers, change all
  member viewing permissions, and edit group branding/settings. Owners do not appear
  as kickable member-token rows; ownership cannot be transferred from this tab.
- **Manager:** an owner-promoted member token. Can invite, replace/kick regular
  members, and edit regular members' viewing permissions. Cannot promote anyone,
  manage themselves/other managers, modify owner access or branding/settings.
  Managers cannot grant or retrieve access beyond their own viewing permissions;
  invitations inherit the manager's current permissions.
- **Member:** can upload only the exact assigned character (spaces/underscores and
  case are normalized) and view permitted categories. Members do not need Discord.
  Uploads remain controlled independently by the player's RuneLite sharing settings.
- Five independent **server-enforced** viewing switches: personal banks, group
  storage, stats/XP (including XP history and live drops), locations, activity/chat.
  Owner-generated tokens start with all five enabled. Manager invitations inherit
  the manager's permissions. Owners can change manager permissions or demote them
  without rotating their token. Regular members cannot open Manage group.
- Up to 25 character tokens per hosted group (including alts). **Replace token**
  invalidates the old token; **Kick member** revokes it and removes the character
  from visible group data. A manager can kick only regular members.
  Data is retained on disk; kicking is not an erasure request. Previously viewed
  or downloaded data cannot be recalled. Access changes apply on the next request;
  an open page refreshes its permissions on the next dashboard poll.
- Tokens are shown only immediately after generation/replacement and cleared on
  Lock/demo. Copy them privately; they are not saved in URLs or plaintext server
  state. The member keeps their token in RuneLite and the current browser tab session.
  Rotation/revocation persistence failures return errors; failed revocations are
  also denied in the current process. Resolve disk failures before restarting,
  since a failed write cannot guarantee revocation survives a restart.
- Website restrictions do **not** restrict Discord channel access: anyone able to
  read an alert channel can see its messages. Keep channel permissions aligned
  with the intended audience. Revoking a token does not erase existing Discord
  messages or cancel already queued notifications.

## Data and reliability

- The backend persists latest snapshots, the last 300 activity entries, event
  deduplication IDs for seven days, and its Discord outbox in `backend\data\state.json`.
  Back up this private file. It contains account data, chat, approved Discord IDs
  for issued keys, and SHA-256 key hashes, but not plaintext access keys.
  Rendered 36x32 PNG sprites are cached alongside it in `backend\data\icons`.
  The four bundled fictional-demo item pictures come from RuneLite's public item
  icon service (`https://static.runelite.net/cache/item/icon/`). The supplied clan
  logo is used on the website; no attached original was modified.
- Disabling bank sharing removes that member's saved personal bank and group-storage
  observation on the next successful snapshot. The website displays the most recent
  group-storage observation from currently linked members once, with its publisher
  and capture time; another authorized member's older observation may then appear.
  Disabling position sharing clears the position. Re-enabling bank sharing requires
  a bank observation if none is cached this session. Polls do not freshen a bank's
  observation timestamp. A relog preserves the server's previous bank snapshot.
- Updated plugins capture GE unit prices and high-alchemy values from RuneLite's
  local item data, canonicalizing noted items for valuation. Reopen each storage
  to populate prices; older snapshots explicitly show partial/unsynced values.
  The bank view totals only visible items, once for shared storage, and shows each
  bank's totals and unit/stack values on hover/focus. GE is an estimate at capture
  time, not live offers; high alch is gross value, excluding rune costs and item
  eligibility restrictions. Untradeables can have zero GE value. Coins use their
  face value for GE, not an invented alch profit. GP arithmetic uses exact integers.
- **Estimated banked XP** uses a finite local list in `backend/public/bank-info.js`:
  burn common logs, successfully cook common raw fish, bury common bones/scatter
  ashes, clean grimy herbs, smith existing bars or smelt ores (not both stages for
  the same material). Bronze needs copper + tin; mithril/adamantite/runite require
  4/6/8 coal, allocated in that order. Iron assumes smelting succeeds; steel
  smelting and alternative methods are not estimated. Supported unfinished potions
  require their matching secondaries in that same storage; shared secondaries are
  consumed once in the listed recipe order. Clean herbs are not counted as future
  potions. Recipes do not borrow across members or shared storage. Search also
  limits supplies available to estimates. These are potential base-XP estimates,
  not guaranteed gains: no levels, burns, bonuses, future purchases, alternate
  methods or later processing stages. Unsupported items are reported as excluded.
  Hover a supply for its chosen method and XP/action. No private item list is sent
  to an external pricing or calculator service.
- Snapshot capture is roughly every six seconds and bridge sync every five seconds.
  Cached stats remain available offline, clearly timestamped. No historic KC or
  quest completion can be reconstructed when the plugin was not active.
- Boss alerts are deduplicated by player/boss/KC. Loot uses event IDs; a briefly
  delayed alert attaches KC only when a matching source count was recently observed.
  No KC is invented when a match is unavailable.
- Discord failures retain events for retry across backend restarts. An interruption
  after Discord accepts a message but before disk acknowledgement may deliver it
  twice (at-least-once delivery).
- Client-side unsent alerts are queued in memory (limit 2,000) and retried while
  connected. Closing RuneLite loses unsent client alerts; watch the connection
  status/logs. The backend outbox has a 10,000-event limit and reports failure
  rather than acknowledging events it could not persist.
- Storage uses local JSON files and is intended for one small group running one
  backend process. Do not run multiple processes against the same data file.
- Keep `.env`, `members.json`, `branding.json` and the backend data directory private.
  Restrict access to them on the host and include the data file in your backup plan.

## Live acceptance checklist

Automated checks do not replace testing inside the game and your Discord server.

- Connect two authorized accounts; verify `!stats Player Name` and last-sync time.
- With the rebuilt client, mine an ore or catch a fish: verify the exact per-action
  XP amount and matching skill icon rise and fade at the top-right of that player's
  card. Train two accounts at once, reconnect the browser, and retry an upload:
  no duplicate drops should appear. Toggle **Live XP drops** off and on, world-hop
  and relog: no accumulated or login XP should be shown as a new action. Stop the
  bridge to check the explicit connection error, then resume; stale drops must not
  be presented as live. The Discord XP chart and notifications must be unchanged.
- Try `!verify` from an unapproved server account: no key should be sent. Verify
  as the owner, check the private DM, and generate a member token in Manage group.
  Use the member token without a member Discord account. Confirm wrong-character
  uploads are refused; members cannot change settings or manage tokens.
  Promote/demote a manager; confirm they cannot manage managers or escalate viewing
  permissions via invitations/replacements. Test every view switch against direct
  API requests as well as the website; kick/replace must invalidate the old token.
  Request a new key after a minute: the previous one must stop working. Leave
  the server: both clients must be denied on their next private request. Rejoin
  and verify again. Test blocked DMs and a Discord outage without leaking keys.
- Send a GIM channel message from each account: one Discord message per sender;
  public/private messages must never appear.
- Kill a boss with KC chat enabled, then confirm the boss name and exact count.
- Receive NPC and raid/chest loot; verify items, quantities and optional matching KC.
- Complete a quest; verify one alert, with no login-time backlog.
- Open/change both banks; view each bank in the sidebar and website, including
  empty banks and timestamps. Disable sharing and verify removal.
- Move both players and change worlds; verify world-map markers, world labels and
  expiry after a client disconnects. On the website, check a known landmark against
  game coordinates, pan/zoom, follow a player, change floors, and check tile-failure
  messages. Verify stale markers disappear and logout clears player names.
- Disconnect/reconnect the backend and Discord; verify explicit connection errors,
  retries, saved snapshots and the pending-outbox counter.
