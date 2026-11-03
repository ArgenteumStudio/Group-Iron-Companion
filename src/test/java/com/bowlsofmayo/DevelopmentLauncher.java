package com.bowlsofmayo;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class DevelopmentLauncher
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(ClanTrackerPlugin.class);
        RuneLite.main(args);
    }
}
