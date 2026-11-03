package com.bowlsofmayo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class LiveXpTracker
{
    private static final Logger LOG = LoggerFactory.getLogger(LiveXpTracker.class);
    private final Map<Skill, Integer> experience = new EnumMap<>(Skill.class);
    private final ConcurrentLinkedQueue<Drop> pending = new ConcurrentLinkedQueue<>();

    static final class Drop
    {
        String id;
        String skill;
        String username;
        int amount;
        int totalXp;
        int level;
        long observedAt;
    }

    static final class Batch
    {
        String username;
        List<Drop> drops = new ArrayList<>();
    }

    void baseline(Skill skill, int xp)
    {
        experience.putIfAbsent(skill, xp);
    }

    void clearBaseline()
    {
        experience.clear();
    }

    boolean hasPending()
    {
        return !pending.isEmpty();
    }

    void clear()
    {
        clearBaseline();
        discardPending();
    }

    void discardPending()
    {
        pending.clear();
    }

    void observe(String username, StatChanged event, int level, boolean enabled, long now)
    {
        Skill skill = event.getSkill();
        if (skill == Skill.OVERALL || skill == null)
        {
            return;
        }
        Integer previous = experience.put(skill, event.getXp());
        if (!enabled || previous == null || event.getXp() <= previous)
        {
            return;
        }
        if (pending.size() >= 2000)
        {
            LOG.error("Live XP queue full; XP drop was not queued");
            return;
        }
        Drop drop = new Drop();
        drop.id = UUID.randomUUID().toString();
        drop.username = username;
        drop.skill = skill.name();
        drop.amount = event.getXp() - previous;
        drop.totalXp = event.getXp();
        drop.level = level;
        drop.observedAt = now;
        pending.add(drop);
    }

    Batch batch(long now)
    {
        int expired = 0;
        for (Drop drop : pending)
        {
            if (now - drop.observedAt > 25000 && pending.remove(drop))
            {
                expired++;
            }
        }
        if (expired > 0)
        {
            LOG.warn("Expired {} unsent live XP drops; historical XP still syncs via snapshots", expired);
        }
        Batch batch = new Batch();
        for (Drop drop : pending)
        {
            if (batch.username == null)
            {
                batch.username = drop.username;
            }
            if (batch.username.equals(drop.username))
            {
                batch.drops.add(drop);
            }
            if (batch.drops.size() == 50)
            {
                break;
            }
        }
        return batch;
    }

    void send(BridgeClient bridge, com.google.gson.Gson gson, long now) throws IOException
    {
        Batch batch = batch(now);
        if (!batch.drops.isEmpty())
        {
            bridge.post("/v1/xp-drops", gson.toJson(batch));
            batch.drops.forEach(pending::remove);
        }
    }
}
