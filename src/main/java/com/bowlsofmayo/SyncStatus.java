package com.bowlsofmayo;

import java.util.EnumMap;
import java.util.Map;

final class SyncStatus
{
    enum Channel
    {
        GROUP, XP, XP_EXPIRY, ALERTS, BANK_ICONS, MAP_IMAGE, LOGO_IMAGE
    }

    private final Map<Channel, String> messages = new EnumMap<>(Channel.class);

    void set(Channel channel, String message)
    {
        if (message.isEmpty())
        {
            messages.remove(channel);
        }
        else
        {
            messages.put(channel, message);
        }
    }

    void clear()
    {
        messages.clear();
    }

    String text()
    {
        return String.join("\n", messages.values());
    }
}
