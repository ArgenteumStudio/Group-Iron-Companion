package com.bowlsofmayo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.client.game.ItemManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class BankSnapshots
{
    private static final Logger LOG = LoggerFactory.getLogger(BankSnapshots.class);
    private final Map<Integer, String> itemIcons = new ConcurrentHashMap<>();
    private final Set<Integer> pendingIcons = new HashSet<>();

    String icon(int id)
    {
        return itemIcons.get(id);
    }

    List<TrackerData.BankItem> readItems(ItemManager itemManager, ItemContainer container)
    {
        return readItems(itemManager, container, null);
    }

    /**
     * The bank container stores tab 1..9 contents first (sizes from the BANK_TAB_n varbits),
     * followed by untabbed items, so each slot's tab follows from its index. Placeholders occupy
     * slots too, which is why tabs are assigned before empty stacks are skipped.
     */
    List<TrackerData.BankItem> readItems(ItemManager itemManager, ItemContainer container, int[] tabCounts)
    {
        List<TrackerData.BankItem> result = new ArrayList<>();
        Item[] items = container.getItems();
        for (int slot = 0; slot < items.length; slot++)
        {
            Item item = items[slot];
            if (item.getId() > 0 && item.getQuantity() > 0)
            {
                TrackerData.BankItem entry = new TrackerData.BankItem();
                entry.tab = tabCounts == null ? null : tabFor(slot, tabCounts);
                entry.id = item.getId();
                entry.qty = item.getQuantity();
                entry.name = itemManager.getItemComposition(item.getId()).getName();
                int baseId = itemManager.canonicalize(item.getId());
                ItemComposition definition = itemManager.getItemComposition(baseId);
                entry.price = itemManager.getItemPrice(baseId);
                entry.highAlch = definition.getHaPrice();
                result.add(entry);
            }
        }
        return Collections.unmodifiableList(result);
    }

    static int tabFor(int slot, int[] tabCounts)
    {
        int end = 0;
        for (int tab = 0; tab < tabCounts.length; tab++)
        {
            end += Math.max(0, tabCounts[tab]);
            if (slot < end)
            {
                return tab + 1;
            }
        }
        return 0;
    }

    static List<TrackerData.BankItem> combinedItems(List<TrackerData.BankItem> personal,
        List<TrackerData.BankItem> shared)
    {
        List<TrackerData.BankItem> items = new ArrayList<>();
        if (personal != null)
        {
            items.addAll(personal);
        }
        if (shared != null)
        {
            items.addAll(shared);
        }
        return items;
    }

    void prepareIcons(ItemManager itemManager, List<TrackerData.BankItem> bank, List<TrackerData.BankItem> groupStorage)
    {
        int scheduled = 0;
        for (TrackerData.BankItem item : combinedItems(bank, groupStorage))
        {
            if (itemIcons.containsKey(item.id) || !pendingIcons.add(item.id))
            {
                continue;
            }
            net.runelite.client.util.AsyncBufferedImage image = itemManager.getImage(item.id);
            if (image == null)
            {
                pendingIcons.remove(item.id);
                LOG.warn("Cannot render bank item icon {}", item.id);
                continue;
            }
            image.onLoaded(() -> {
                try
                {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    if (!ImageIO.write(image, "png", bytes))
                    {
                        throw new IOException("PNG encoder unavailable");
                    }
                    itemIcons.put(item.id, Base64.getEncoder().encodeToString(bytes.toByteArray()));
                }
                catch (IOException error)
                {
                    LOG.warn("Failed to encode bank item icon {}", item.id, error);
                }
                finally
                {
                    pendingIcons.remove(item.id);
                }
            });
            if (++scheduled == 32)
            {
                break;
            }
        }
    }
}
