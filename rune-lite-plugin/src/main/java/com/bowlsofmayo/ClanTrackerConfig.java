package com.bowlsofmayo;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("bowlsofmayo")
public interface ClanTrackerConfig extends Config
{
    @ConfigItem(
        keyName = "serverUrl",
        name = "Server URL",
        description = "URL for the BowlsofMayo bridge server (include http:// and port)",
        position = 1
    )
    default String serverUrl()
    {
        return "http://localhost:4000";
    }

    @ConfigItem(
        keyName = "clanName",
        name = "Clan Name",
        description = "Clan name used when posting data",
        position = 2
    )
    default String clanName()
    {
        return "BowlsofMayo";
    }
}
