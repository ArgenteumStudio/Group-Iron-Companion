package com.bowlsofmayo;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("bowlsofmayo")
public interface ClanTrackerConfig extends Config
{
    @ConfigSection(name = "Connection", description = "Private server connection settings", position = 0)
    String connectionSection = "connection";

    @ConfigSection(name = "Location & Sharing", description = "Choose what information is shared", position = 1)
    String sharingSection = "sharing";

    @ConfigSection(name = "Notifications", description = "Choose which reminders and alerts are enabled", position = 2)
    String notificationsSection = "notifications";

    @ConfigSection(name = "Trackers & Display", description = "Sidebar trackers and appearance", position = 3)
    String displaySection = "display";

    @ConfigItem(keyName = "enableConnection", name = "Enable connection / consent",
        description = "OFF by default. Enabling sends your RS name, stats and quest points to the configured server operator. They can read, store and share uploads. Optional banks, location and alerts require separate switches. Disabling does not delete previous uploads.",
        position = 0, section = connectionSection)
    default boolean enableConnection() { return false; }

    @ConfigItem(keyName = "serverUrl", name = "Bridge URL",
        description = "Private HTTPS bridge URL; HTTP is allowed only on localhost", position = 1, section = connectionSection)
    default String serverUrl() { return "http://localhost:4000"; }

    @ConfigItem(keyName = "memberToken", name = "Member token", secret = true,
        description = "Your individual backend token, assigned to your RS name", position = 2, section = connectionSection)
    default String memberToken() { return ""; }

    @ConfigItem(keyName = "shareLocation", name = "Share location",
        description = "Share your position and world with your private group", position = 0, section = sharingSection)
    default boolean shareLocation() { return false; }

    @ConfigItem(keyName = "showMap", name = "Group world map markers",
        description = "Show recently synced group members on the in-game world map", position = 1, section = sharingSection)
    default boolean showMap() { return false; }

    @ConfigItem(keyName = "shareBank", name = "Share bank",
        description = "Share personal bank and Group Ironman storage snapshots when you open them", position = 2, section = sharingSection)
    default boolean shareBank() { return false; }

    @ConfigItem(keyName = "shareChat", name = "Forward GIM chat",
        description = "Forward only your own Group Ironman channel messages to private Discord", position = 3, section = sharingSection)
    default boolean shareChat() { return false; }

    @ConfigItem(keyName = "shareLiveXp", name = "Live XP drops",
        description = "Send immediate skill XP changes to the private website (not Discord)", position = 4, section = sharingSection)
    default boolean shareLiveXp() { return false; }

    @ConfigItem(keyName = "bossAlerts", name = "Boss KC alerts",
        description = "Send observed boss kill/completion counts to Discord and share saved kill counts for !kc", position = 0, section = notificationsSection)
    default boolean bossAlerts() { return false; }

    @ConfigItem(keyName = "dropAlerts", name = "Drop alerts",
        description = "Send Loot Tracker drops to Discord, excluding PvP loot", position = 1, section = notificationsSection)
    default boolean dropAlerts() { return false; }

    @ConfigItem(keyName = "questAlerts", name = "Quest alerts",
        description = "Announce quests and achievement diary tiers completed while the plugin is active", position = 2, section = notificationsSection)
    default boolean questAlerts() { return false; }

    @Range(min = 0)
    @ConfigItem(keyName = "minimumDropValue", name = "Minimum drop GP",
        description = "Minimum GE value per item stack to announce; zero includes untradeables", position = 6, section = notificationsSection)
    default int minimumDropValue() { return 0; }

    @ConfigItem(keyName = "gimStorageReminder", name = "GIM Storage Reminder",
        description = "Warn before logging out when items withdrawn from Group Ironman storage remain in your inventory", position = 3, section = notificationsSection)
    default boolean gimStorageReminder() { return true; }

    @ConfigItem(keyName = "notifyDeathCofferLow", name = "Death's Coffer low balance notification",
        description = "Notify when an observed Death's Coffer balance drops to or below the alert threshold", position = 4, section = notificationsSection)
    default boolean notifyDeathCofferLow() { return true; }

    @Range(min = 0)
    @ConfigItem(keyName = "deathCofferLowThreshold", name = "Death's Coffer alert threshold",
        description = "Send a notification when the balance falls to or below this amount", position = 5, section = notificationsSection)
    default int deathCofferLowThreshold() { return 10000; }

    @ConfigItem(keyName = "trackDeathCoffer", name = "Track Death's Coffer",
        description = "Show the last observed balance saved for this character; open Death's Coffer to refresh it", position = 0, section = displaySection)
    default boolean trackDeathCoffer() { return true; }

    @ConfigItem(keyName = "sidebarIcon", name = "Sidebar icon",
        description = "Defaults to the companion shield. Group logo uses the logo set on your website", position = 1, section = displaySection)
    default SidebarIcon sidebarIcon() { return SidebarIcon.COMPANION_SHIELD; }

    @Range(min = 0)
    @ConfigItem(keyName = "sidebarItemId", name = "Custom item ID",
        description = "Item ID shown when Sidebar icon is set to Custom item ID (find IDs on the OSRS Wiki)", position = 2, section = displaySection)
    default int sidebarItemId() { return 1923; }
}
