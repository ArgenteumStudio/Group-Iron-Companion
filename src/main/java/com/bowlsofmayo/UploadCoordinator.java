package com.bowlsofmayo;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.Queue;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class UploadCoordinator
{
    private static final Logger LOG = LoggerFactory.getLogger(UploadCoordinator.class);

    static boolean eventEnabled(ClanTrackerConfig config, TrackerData.Event event)
    {
        switch (event.type)
        {
            case "chat": return config.shareChat();
            case "boss": return config.bossAlerts();
            case "loot": return config.dropAlerts();
            case "quest": return config.questAlerts();
            default: throw new IllegalArgumentException("Unknown alert type: " + event.type);
        }
    }

    static TrackerData.Member snapshotForUpload(ClanTrackerConfig config, TrackerData.Member source)
    {
        TrackerData.Member upload = new TrackerData.Member();
        upload.username = source.username;
        upload.stats = source.stats;
        upload.questPoints = source.questPoints;
        upload.observedAt = source.observedAt;
        upload.position = config.shareLocation() ? source.position : null;
        upload.bankShared = config.shareBank() && source.bankShared;
        if (upload.bankShared)
        {
            upload.bank = source.bank;
            upload.bankUpdatedAt = source.bankUpdatedAt;
            upload.groupStorage = source.groupStorage;
            upload.groupStorageUpdatedAt = source.groupStorageUpdatedAt;
        }
        return upload;
    }

    static String sendAlerts(BridgeClient bridge, Gson gson, Queue<TrackerData.Event> events,
        ClanTrackerConfig config, BooleanSupplier currentAccess)
    {
        String status = "";
        for (int count = 0; count < 50 && currentAccess.getAsBoolean(); count++)
        {
            TrackerData.Event event = events.peek();
            if (event == null)
            {
                break;
            }
            if (!eventEnabled(config, event))
            {
                events.remove(event);
                continue;
            }
            try
            {
                String json = gson.toJson(event);
                if (!currentAccess.getAsBoolean() || !eventEnabled(config, event))
                {
                    break;
                }
                bridge.post("/v1/events", json);
                events.remove(event);
            }
            catch (IOException error)
            {
                if (!currentAccess.getAsBoolean())
                {
                    break;
                }
                boolean rejected = error instanceof BridgeClient.HttpException &&
                    ((BridgeClient.HttpException) error).rejectsEvent();
                status = rejected ? "Alert rejected and discarded: " + error.getMessage() :
                    "Alerts waiting for retry: " + error.getMessage();
                LOG.warn("{} (type={}, id={})", status, event.type, event.id);
                if (rejected)
                {
                    events.remove(event);
                }
                else
                {
                    break;
                }
            }
        }
        return status;
    }
}
