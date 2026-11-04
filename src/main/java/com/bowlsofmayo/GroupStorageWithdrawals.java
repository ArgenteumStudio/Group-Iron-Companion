package com.bowlsofmayo;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;

final class GroupStorageWithdrawals
{
    private Map<Integer, Long> storage;
    private Map<Integer, Long> inventory;
    private int inventoryContainerId = -1;
    private final Map<Integer, Long> pendingStorageWithdrawals = new HashMap<>();
    private final Map<Integer, Integer> pendingStorageExpiry = new HashMap<>();
    private final Map<Integer, Long> pendingInventoryAdds = new HashMap<>();
    private final Map<Integer, Integer> pendingInventoryExpiry = new HashMap<>();
    private final Map<Integer, Long> withdrawnInInventory = new HashMap<>();
    private int tick;

    void updateStorage(ItemContainer container)
    {
        if (container == null)
        {
            return;
        }
        Map<Integer, Long> current = counts(container);
        if (storage != null)
        {
            for (int id : keys(storage, current))
            {
                long decrease = storage.getOrDefault(id, 0L) - current.getOrDefault(id, 0L);
                if (decrease > 0)
                {
                    addPending(pendingStorageWithdrawals, pendingStorageExpiry, id, decrease);
                }
            }
            reconcile();
        }
        storage = current;
    }

    void updateInventory(int containerId, ItemContainer container)
    {
        if (container == null)
        {
            return;
        }
        Map<Integer, Long> current = counts(container);
        if (inventoryContainerId != containerId)
        {
            inventory = current;
            inventoryContainerId = containerId;
            pendingInventoryAdds.clear();
            pendingInventoryExpiry.clear();
            return;
        }
        if (inventory != null)
        {
            for (int id : keys(inventory, current))
            {
                long change = current.getOrDefault(id, 0L) - inventory.getOrDefault(id, 0L);
                if (change > 0)
                {
                    addPending(pendingInventoryAdds, pendingInventoryExpiry, id, change);
                }
                else if (change < 0)
                {
                    add(withdrawnInInventory, id, change);
                }
            }
            reconcile();
        }
        inventory = current;
    }

    void finishTick()
    {
        tick++;
        expire(pendingStorageWithdrawals, pendingStorageExpiry);
        expire(pendingInventoryAdds, pendingInventoryExpiry);
    }

    boolean hasItemsToReturn()
    {
        return !withdrawnInInventory.isEmpty();
    }

    Map<Integer, Long> itemsToReturn()
    {
        return Collections.unmodifiableMap(new HashMap<>(withdrawnInInventory));
    }

    void clear()
    {
        storage = null;
        inventory = null;
        inventoryContainerId = -1;
        pendingStorageWithdrawals.clear();
        pendingStorageExpiry.clear();
        pendingInventoryAdds.clear();
        pendingInventoryExpiry.clear();
        withdrawnInInventory.clear();
        tick = 0;
    }

    private void reconcile()
    {
        for (int id : keys(pendingStorageWithdrawals, pendingInventoryAdds))
        {
            long withdrawn = Math.min(pendingStorageWithdrawals.getOrDefault(id, 0L),
                pendingInventoryAdds.getOrDefault(id, 0L));
            if (withdrawn > 0)
            {
                add(withdrawnInInventory, id, withdrawn);
                add(pendingStorageWithdrawals, id, -withdrawn);
                add(pendingInventoryAdds, id, -withdrawn);
                removeExpiryIfEmpty(pendingStorageWithdrawals, pendingStorageExpiry, id);
                removeExpiryIfEmpty(pendingInventoryAdds, pendingInventoryExpiry, id);
            }
        }
    }

    private void addPending(Map<Integer, Long> counts, Map<Integer, Integer> expiries, int id, long amount)
    {
        add(counts, id, amount);
        if (counts.containsKey(id))
        {
            expiries.put(id, tick + 5);
        }
        else
        {
            expiries.remove(id);
        }
    }

    private void expire(Map<Integer, Long> counts, Map<Integer, Integer> expiries)
    {
        expiries.entrySet().removeIf(entry -> {
            if (entry.getValue() <= tick)
            {
                counts.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    private static void removeExpiryIfEmpty(Map<Integer, Long> counts, Map<Integer, Integer> expiries, int id)
    {
        if (!counts.containsKey(id))
        {
            expiries.remove(id);
        }
    }

    private static Map<Integer, Long> counts(ItemContainer container)
    {
        Map<Integer, Long> result = new HashMap<>();
        for (Item item : container.getItems())
        {
            if (item.getId() > 0 && item.getQuantity() > 0)
            {
                result.merge(item.getId(), (long) item.getQuantity(), Long::sum);
            }
        }
        return result;
    }

    private static Set<Integer> keys(Map<Integer, Long> left, Map<Integer, Long> right)
    {
        Set<Integer> result = new HashSet<>(left.keySet());
        result.addAll(right.keySet());
        return result;
    }

    private static void add(Map<Integer, Long> counts, int id, long amount)
    {
        long updated = counts.getOrDefault(id, 0L) + amount;
        if (updated > 0)
        {
            counts.put(id, updated);
        }
        else
        {
            counts.remove(id);
        }
    }
}
