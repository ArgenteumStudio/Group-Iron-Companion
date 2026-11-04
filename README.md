# Group Iron Companion

An opt-in RuneLite plugin for Group Ironman members to share selected game data
with their group's private dashboard and Discord alerts.

This is an independent project and is not affiliated with Jagex or RuneLite.

Created using intellectual property belonging to Jagex Limited under the terms
of Jagex's Fan Content Policy. This content is not endorsed by or affiliated
with Jagex.

## Getting started

Your group must already have a Group Iron Companion server. Ask your group
administrator for the server URL, dashboard link and your individual character
token. Group members do not need to run a server or build the plugin.

1. Search for **Group Iron Companion** in RuneLite's Plugin Hub and install it
   once it is available.
2. Open the plugin settings and enter the server URL and your character token.
   Remote connections require an **HTTPS** URL.
3. Read and accept the connection consent, then enable the connection.
4. Enable only the sharing options you want. Sharing features are off by default.
5. Open the sidebar to view group data, or visit the dashboard link provided by
   your group.

Share your character token only with its intended player. Do not post it in
public channels or screenshots, and never use or share the group owner's key.
If your token no longer works, contact your group administrator.

## What you can use

- Share selected stats, bank and group storage data, activity and locations.
- View your group's shared information on the dashboard.
- Receive game-event alerts in your group's configured Discord channels when
  you opt in.
- Authorized Discord users can use `!stats <player>` for the latest synced stats.

Reopen your bank or group storage after enabling or changing bank sharing so
the plugin can collect a fresh snapshot.

The sidebar bank viewer shows your real bank tabs. Left-click a tab to show only
its items, or right-click a tab (or its heading) to collapse/expand it. The same
works on the web dashboard.

The sidebar can also show your last observed **Death's Coffer** balance, with an
optional low-balance warning. Open the coffer once per character to sync it;
fees paid elsewhere update the next time you open it. This balance stays in
RuneLite and is not uploaded. If you withdraw items from group storage, the
plugin can remind you to return them before logging out.

The default sidebar icon is the three-companion shield. Select
**Sidebar icon > Companion shield** to switch back from a group logo or item icon.

## Privacy and connection status

Only connect to a server you trust. Your selected data is sent to the server
provided by your group, not to RuneLite.

Changing the server URL or character token discards pending uploads and starts
a fresh account snapshot. Disabling a sharing option prevents its queued data
from being sent. Requests already in flight cannot be recalled, and disabling
sharing does not delete data already stored on the server. Contact your group
administrator about stored data.

Turning the plugin off discards unsent alerts, pending loot and live XP drops.
Turning it back on starts fresh. Alerts already accepted by the server can
still finish posting to Discord.

The sidebar reports group, live XP and alert upload errors separately. Temporary
connection failures are retried; rejected alerts are discarded with a warning.
Expired live XP drops are reported as not delivered live, and a later successful
sync does not mean those expired drops were delivered.

## Dashboard preview

These screenshots use fictional group data:

| Overview | Banks | Locations and activity | Full-page map |
| --- | --- | --- | --- |
| ![Fictional group overview](screenshots/demo-overview.png) | ![Fictional shared banks](screenshots/demo-banks.png) | ![Fictional locations and activity](screenshots/demo-locations-activity.png) | ![Fictional full-page world map](screenshots/demo-world-map.png) |

## Support

For server access, tokens or dashboard permissions, contact your group
administrator. For plugin bugs, use the
[issue tracker](https://github.com/ArgenteumStudio/Group-Iron-Companion/issues).

Group administrators can find the separately maintained server and its
documentation in
[GroupIronCompanion-Backend](https://github.com/ArgenteumStudio/GroupIronCompanion-Backend).
Plugin development instructions are in [CONTRIBUTING.md](CONTRIBUTING.md).
