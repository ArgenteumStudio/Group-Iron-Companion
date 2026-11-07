package com.bowlsofmayo;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
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
    private final Map<Integer, Long> pendingStorageDeposits = new HashMap<>();
    private final Map<Integer, Integer> pendingStorageDepositExpiry = new HashMap<>();
    private final Map<Integer, Long> pendingInventoryRemovals = new HashMap<>();
    private final Map<Integer, Integer> pendingInventoryRemovalExpiry = new HashMap<>();
    private final Map<Integer, Map<Integer, Long>> keptContainers = new HashMap<>();
    private final Map<Integer, Long> pendingKeptAdds = new HashMap<>();
    private final Map<Integer, Integer> pendingKeptExpiry = new HashMap<>();
    private final Map<Integer, Long> withdrawnInInventory = new HashMap<>();
    private int tick;
    private IntPredicate trackable = id -> true;

    void setTrackable(IntPredicate trackable)
    {
        this.trackable = trackable;
    }

    /**
     * Tracks places a borrowed item can be moved to while still being owed, such as the personal bank
     * or worn equipment. Items leaving the inventory for anywhere else are treated as used up.
     */
    void updateKept(int containerId, ItemContainer container)
    {
        if (container == null)
        {
            return;
        }
        Map<Integer, Long> current = counts(container);
        Map<Integer, Long> previous = keptContainers.put(containerId, current);
        if (previous == null)
        {
            return;
        }
        for (int id : keys(previous, current))
        {
            long increase = current.getOrDefault(id, 0L) - previous.getOrDefault(id, 0L);
            if (increase > 0)
            {
                addPending(pendingKeptAdds, pendingKeptExpiry, id, increase);
            }
        }
        reconcile();
    }

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
                else if (decrease < 0)
                {
                    addPending(pendingStorageDeposits, pendingStorageDepositExpiry, id, -decrease);
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
            pendingInventoryRemovals.clear();
            pendingInventoryRemovalExpiry.clear();
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
                    addPending(pendingInventoryRemovals, pendingInventoryRemovalExpiry, id, -change);
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
        expire(pendingStorageDeposits, pendingStorageDepositExpiry);
        expire(pendingKeptAdds, pendingKeptExpiry);
        pendingInventoryRemovalExpiry.entrySet().removeIf(entry -> {
            if (entry.getValue() > tick)
            {
                return false;
            }
            Long used = pendingInventoryRemovals.remove(entry.getKey());
            if (used != null)
            {
                add(withdrawnInInventory, entry.getKey(), -used);
            }
            return true;
        });
    }

    void clearBorrowed()
    {
        withdrawnInInventory.clear();
    }

    boolean hasItemsToReturn()
    {
        return !withdrawnInInventory.isEmpty();
    }

    Map<Integer, Long> itemsToReturn()
    {
        return Collections.unmodifiableMap(new HashMap<>(withdrawnInInventory));
    }

    void restore(Map<Integer, Long> saved)
    {
        withdrawnInInventory.clear();
        saved.forEach((id, quantity) -> {
            if (trackable.test(id))
            {
                add(withdrawnInInventory, id, quantity);
            }
        });
    }

    static String encode(Map<Integer, Long> items)
    {
        StringBuilder result = new StringBuilder();
        new java.util.TreeMap<>(items).forEach((id, quantity) ->
            result.append(result.length() == 0 ? "" : ",").append(id).append(':').append(quantity));
        return result.toString();
    }

    static Map<Integer, Long> decode(String value)
    {
        Map<Integer, Long> result = new HashMap<>();
        if (value == null || value.isEmpty())
        {
            return result;
        }
        for (String entry : value.split(","))
        {
            String[] parts = entry.split(":");
            try
            {
                if (parts.length == 2)
                {
                    add(result, Integer.parseInt(parts[0].trim()), Long.parseLong(parts[1].trim()));
                }
            }
            catch (NumberFormatException ignored)
            {
                // Skip corrupted entries rather than losing the rest of the saved reminders.
            }
        }
        return result;
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
        pendingStorageDeposits.clear();
        pendingStorageDepositExpiry.clear();
        pendingInventoryRemovals.clear();
        pendingInventoryRemovalExpiry.clear();
        keptContainers.clear();
        pendingKeptAdds.clear();
        pendingKeptExpiry.clear();
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
                if (trackable.test(id))
                {
                    add(withdrawnInInventory, id, withdrawn);
                }
                add(pendingStorageWithdrawals, id, -withdrawn);
                add(pendingInventoryAdds, id, -withdrawn);
                removeExpiryIfEmpty(pendingStorageWithdrawals, pendingStorageExpiry, id);
                removeExpiryIfEmpty(pendingInventoryAdds, pendingInventoryExpiry, id);
            }
        }
        // Only items moved back into Group Storage count as returned.
        for (int id : keys(pendingStorageDeposits, pendingInventoryRemovals))
        {
            long returned = Math.min(withdrawnInInventory.getOrDefault(id, 0L), Math.min(
                pendingStorageDeposits.getOrDefault(id, 0L), pendingInventoryRemovals.getOrDefault(id, 0L)));
            long matched = Math.min(pendingStorageDeposits.getOrDefault(id, 0L),
                pendingInventoryRemovals.getOrDefault(id, 0L));
            if (matched > 0)
            {
                add(withdrawnInInventory, id, -returned);
                add(pendingStorageDeposits, id, -matched);
                add(pendingInventoryRemovals, id, -matched);
                removeExpiryIfEmpty(pendingStorageDeposits, pendingStorageDepositExpiry, id);
                removeExpiryIfEmpty(pendingInventoryRemovals, pendingInventoryRemovalExpiry, id);
            }
        }
        for (int id : keys(pendingKeptAdds, pendingInventoryRemovals))
        {
            long kept = Math.min(pendingKeptAdds.getOrDefault(id, 0L), pendingInventoryRemovals.getOrDefault(id, 0L));
            if (kept > 0)
            {
                add(pendingKeptAdds, id, -kept);
                add(pendingInventoryRemovals, id, -kept);
                removeExpiryIfEmpty(pendingKeptAdds, pendingKeptExpiry, id);
                removeExpiryIfEmpty(pendingInventoryRemovals, pendingInventoryRemovalExpiry, id);
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
