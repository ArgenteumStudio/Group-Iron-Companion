package com.bowlsofmayo;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("bowlsofmayo")
public interface ClanTrackerConfig extends Config
{
    @ConfigItem(keyName = "enableConnection", name = "Enable connection / consent",
        description = "OFF by default. Enabling sends your RS name, stats and quest points to the configured server operator. They can read, store and share uploads. Optional banks, location and alerts require separate switches. Disabling does not delete previous uploads.", position = -1)
    default boolean enableConnection() { return false; }

    @ConfigItem(keyName = "serverUrl", name = "Bridge URL",
        description = "Private HTTPS bridge URL; HTTP is allowed only on localhost", position = 0)
    default String serverUrl() { return "http://localhost:4000"; }

    @ConfigItem(keyName = "memberToken", name = "Member token", secret = true,
        description = "Your individual backend token, assigned to your RS name", position = 1)
    default String memberToken() { return ""; }

    @ConfigItem(keyName = "shareBank", name = "Share bank",
        description = "Share personal bank and Group Ironman storage snapshots when you open them", position = 2)
    default boolean shareBank() { return false; }

    @ConfigItem(keyName = "shareLocation", name = "Share location",
        description = "Share your position and world with your private group", position = 3)
    default boolean shareLocation() { return false; }

    @ConfigItem(keyName = "showMap", name = "Group world map markers",
        description = "Show recently synced group members on the in-game world map", position = 4)
    default boolean showMap() { return false; }

    @ConfigItem(keyName = "shareChat", name = "Forward GIM chat",
        description = "Forward only your own Group Ironman channel messages to private Discord", position = 5)
    default boolean shareChat() { return false; }

    @ConfigItem(keyName = "bossAlerts", name = "Boss KC alerts",
        description = "Send observed boss kill/completion counts to Discord", position = 6)
    default boolean bossAlerts() { return false; }

    @ConfigItem(keyName = "dropAlerts", name = "Drop alerts",
        description = "Send Loot Tracker drops to Discord, excluding PvP loot", position = 7)
    default boolean dropAlerts() { return false; }

    @Range(min = 0)
    @ConfigItem(keyName = "minimumDropValue", name = "Minimum drop GP",
        description = "Minimum GE value per item stack to announce; zero includes untradeables", position = 10)
    default int minimumDropValue() { return 0; }

    @ConfigItem(keyName = "questAlerts", name = "Quest alerts",
        description = "Announce quests completed while the plugin is active", position = 8)
    default boolean questAlerts() { return false; }

    @ConfigItem(keyName = "sidebarIcon", name = "Sidebar icon",
        description = "Defaults to the companion shield. Group logo uses the logo set on your website", position = 11)
    default SidebarIcon sidebarIcon() { return SidebarIcon.COMPANION_SHIELD; }

    @Range(min = 0)
    @ConfigItem(keyName = "sidebarItemId", name = "Custom item ID",
        description = "Item ID shown when Sidebar icon is set to Custom item ID (find IDs on the OSRS Wiki)", position = 12)
    default int sidebarItemId() { return 1923; }

    @ConfigItem(keyName = "shareLiveXp", name = "Live XP drops",
        description = "Send immediate skill XP changes to the private website (not Discord)", position = 9)
    default boolean shareLiveXp() { return false; }
}
