package com.bowlsofmayo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Queue;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import okhttp3.OkHttpClient;

public final class FeatureChecks
{
    public static void main(String[] args) throws Exception
    {
        checkKill("Your Zulrah kill count is: 1,234.", "Zulrah", 1234);
        checkKill("Your completion count for Chambers of Xeric is: 35.", "Chambers of Xeric", 35);
        checkKill("Your completed The Gauntlet count is: 100.", "The Gauntlet", 100);
        checkKill("Your subdued Wintertodt count is: 45.", "Wintertodt", 45);
        checkKill("Your Vorkath kill count is: 1.", "Vorkath", 1);
        checkKill("Your Lunar Chest count is: 12.", "Lunar Chest", 12);
        assert FeatureParser.isChestCount("Lunar Chest");
        assert !FeatureParser.isChestCount("Eclipse Moon") && !FeatureParser.isChestCount("Brutus");
        assert "Varrock Hard".equals(FeatureParser.parseDiary(
            "Congratulations! You have completed all of the hard tasks in the Varrock area. Speak to Toby to claim your reward."));
        assert "Lumbridge & Draynor Elite".equals(FeatureParser.parseDiary(
            "Congratulations! You have completed all of the elite tasks in the Lumbridge & Draynor area."));
        assert FeatureParser.parseDiary("Well done! You have completed an easy task in the Varrock area.") == null;
        int[] tabs = {2, 0, 3, 0, 0, 0, 0, 0, 0};
        assert BankSnapshots.tabFor(0, tabs) == 1 && BankSnapshots.tabFor(1, tabs) == 1;
        assert BankSnapshots.tabFor(2, tabs) == 3 && BankSnapshots.tabFor(4, tabs) == 3;
        assert BankSnapshots.tabFor(5, tabs) == 0;
        checkKill("Your Barrows chest count is: 250.", "Barrows chest", 250);
        assert FeatureParser.sourceKey("Barrows chest").equals(FeatureParser.sourceKey("Barrows"));
        assert FeatureParser.parseKill("Your Ape Atoll Agility lap count is: 5.") == null;
        assert FeatureParser.parseKill("Someone says: Your Zulrah kill count is: 10.") == null;
        assert FeatureParser.parseKill("Your Zulrah kill count is: 9999999999999999999.") == null;
        assert FeatureParser.parseKill("Your Zulrah kill count is: 0.") == null;
        assert FeatureParser.samePlayer("Test_Player", "test player");
        assert FeatureParser.sourceKey("The Leviathan").equals("leviathan");
        assert FeatureParser.sourceKey("The Corrupted Gauntlet").equals("corruptedgauntlet");
        assert ClanPanel.quantity(1).equals("1");
        assert ClanPanel.quantity(99999).equals("99999");
        assert ClanPanel.quantity(100000).equals("100K");
        assert ClanPanel.quantity(9999999).equals("9999K");
        assert ClanPanel.quantity(10000000).equals("10M");
        assert ClanPanel.quantity(Integer.MAX_VALUE).equals("2147M");
        checkConnectionNotice();
        checkCompanionIcon();
        assert new ClanTrackerConfig() {}.gimStorageReminder() : "GIM storage reminder should stay enabled by default";
        assert !new ClanTrackerConfig() {}.shareStorageWithdrawals() : "Storage withdrawal sharing should be off by default";
        assert new ClanTrackerConfig() {}.trackDeathCoffer() : "Death's Coffer tracker should be enabled by default";
        assert new ClanTrackerConfig() {}.notifyDeathCofferLow() : "Death's Coffer low balance notification should be enabled by default";
        assert new ClanTrackerConfig() {}.deathCofferLowThreshold() == 10000 : "Death's Coffer threshold should default to 10,000 GP";
        assert ClanTrackerPlugin.crossedDeathCofferThreshold(10001, 10000, 10000);
        assert !ClanTrackerPlugin.crossedDeathCofferThreshold(10000, 9000, 10000);
        assert !ClanTrackerPlugin.crossedDeathCofferThreshold(9000, 8000, 10000);
        assert !ClanTrackerPlugin.crossedDeathCofferThreshold(Integer.MIN_VALUE, 9000, 10000);
        checkGroupStorageWithdrawals();
        checkBorrowedItemSharing();
        checkLogoutInterceptorRemoved();
        DeathCofferChecks.run();

        Gson gson = new GsonBuilder().serializeNulls().create();
        TrackerData.Member member = new TrackerData.Member();
        member.username = "Test Player";
        assert gson.toJson(member).contains("\"bank\":null");
        member.bank = new ArrayList<>();
        member.bankShared = true;
        member.bankUpdatedAt = 123456;
        String json = gson.toJson(member);
        assert json.contains("\"bank\":[]");
        assert json.contains("\"bankUpdatedAt\":123456");
        assert json.contains("\"bankShared\":true");
        TrackerData.Event event = new TrackerData.Event();
        event.message = "hello \"group\"\n@everyone";
        assert gson.fromJson(gson.toJson(event), TrackerData.Event.class).message.equals(event.message);
        OkHttpClient http = new OkHttpClient();
        new BridgeClient(http, "http://localhost:4000", "a".repeat(48));
        new BridgeClient(http, "https://example.com", "a".repeat(48));
        rejects(() -> new BridgeClient(http, "http://example.com", "a".repeat(48)));
        rejects(() -> new BridgeClient(http, "https://example.com", ""));
        rejects(() -> new BridgeClient(http, "https://user:pass@example.com", "a".repeat(48)));
        checkEventHandlers();
        SidebarChecks.run();
        checkLiveXp();
        SyncChecks.run();
        BufferedImage wide = new BufferedImage(200, 50, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D paint = wide.createGraphics();
        paint.setColor(java.awt.Color.RED);
        paint.fillRect(20, 10, 160, 30);
        paint.dispose();
        BufferedImage icon = TrackerImages.iconImage(wide);
        assert icon.getWidth() == icon.getHeight() : "Sidebar icon should be square";
        assert (icon.getRGB(0, 0) >>> 24) == 0 : "Logo should be letterboxed, not stretched";
        assert (icon.getRGB(icon.getWidth() / 2, icon.getHeight() / 2) >>> 24) != 0;
        assert TrackerImages.iconImage(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)) != null;
        System.out.println("Java feature checks passed (KC parsing, chat filtering, quest transitions, banks, world map, JSON contract, transport).");
    }

    private static void checkCompanionIcon() throws Exception
    {
        assert new ClanTrackerConfig() {}.sidebarIcon() == SidebarIcon.COMPANION_SHIELD;
        assert SidebarIcon.valueOf("GROUP_LOGO") == SidebarIcon.GROUP_LOGO : "Existing icon preferences must remain valid";
        BufferedImage sidebar = net.runelite.client.util.ImageUtil.loadImageResource(ClanTrackerPlugin.class, "companion-icon.png");
        BufferedImage hub = javax.imageio.ImageIO.read(new java.io.File("icon.png"));
        assert sidebar.getWidth() == 32 && sidebar.getHeight() == 32 : "Sidebar icon must be 32 x 32";
        assert hub.getWidth() == 48 && hub.getHeight() == 48 : "Hub icon must fit the 48 x 72 limit";
        for (BufferedImage image : Arrays.asList(sidebar, hub))
        {
            assert image.getColorModel().hasAlpha() : "Icons must support transparency";
            assert (image.getRGB(0, 0) >>> 24) == 0 : "Icon corners must be transparent";
            assert (image.getRGB(image.getWidth() / 2, image.getHeight() / 2) >>> 24) == 255 : "Shield center must be opaque";
        }
    }

    private static void checkGroupStorageWithdrawals()
    {
        GroupStorageWithdrawals withdrawals = new GroupStorageWithdrawals();
        withdrawals.updateStorage(container(4151, 5));
        withdrawals.updateInventory(93, container(4151, 1));

        withdrawals.updateStorage(container(4151, 3));
        withdrawals.updateInventory(93, container(4151, 3));
        assert withdrawals.hasItemsToReturn() : "Storage withdrawals still in inventory should trigger a warning";
        assert withdrawals.itemsToReturn().get(4151) == 2L : "Warning should report remaining item quantity";

        withdrawals.updateKept(95, container());
        withdrawals.updateInventory(93, container(4151, 2));
        withdrawals.updateKept(95, container(4151, 1));
        for (int i = 0; i < 5; i++)
        {
            withdrawals.finishTick();
        }
        assert withdrawals.itemsToReturn().get(4151) == 2L
            : "Deposits into the personal bank must not count as returning borrowed items";
        withdrawals.updateStorage(container(4151, 5));
        withdrawals.updateInventory(93, container());
        assert !withdrawals.hasItemsToReturn() : "Returning the withdrawn item should clear the warning";

        withdrawals.updateStorage(container(4151, 2));
        for (int i = 0; i < 5; i++)
        {
            withdrawals.finishTick();
        }

        assert !withdrawals.hasItemsToReturn() : "Another member's withdrawal must not count without an inventory increase";

        withdrawals.updateInventory(93, container(4151, 2, 995, 10));
        withdrawals.finishTick();
        assert !withdrawals.hasItemsToReturn() : "Inventory gains alone must not count as group storage withdrawals";
        withdrawals.clear();
        assert !withdrawals.hasItemsToReturn() : "Account resets must clear tracked withdrawals";

        withdrawals.updateStorage(container(4151, 5));
        withdrawals.updateInventory(93, container(4151, 1));
        withdrawals.updateStorage(container(4151, 3));
        withdrawals.updateInventory(660, container(4151, 1));
        withdrawals.updateInventory(660, container(4151, 3));
        assert withdrawals.hasItemsToReturn() : "Group storage withdrawal should be tracked in its inventory container";
        withdrawals.updateInventory(93, container(4151, 3));
        assert withdrawals.hasItemsToReturn() : "Switching back to the standard inventory must retain the withdrawal";
        withdrawals.updateStorage(container(4151, 5));
        withdrawals.updateInventory(93, container(4151, 1));
        assert !withdrawals.hasItemsToReturn() : "Returning the withdrawn item should clear after a container switch";

        GroupStorageWithdrawals food = new GroupStorageWithdrawals();
        food.updateStorage(container(3144, 10));
        food.updateInventory(93, container());
        food.updateKept(94, container());
        food.updateStorage(container(3144, 6));
        food.updateInventory(93, container(3144, 4));
        food.updateInventory(93, container(3144, 3));
        for (int i = 0; i < 5; i++)
        {
            food.finishTick();
        }
        assert food.itemsToReturn().get(3144) == 3L : "Eaten borrowed food must be cleared from the reminder";
        food.updateInventory(93, container(3144, 2));
        food.updateKept(94, container(3144, 1));
        for (int i = 0; i < 5; i++)
        {
            food.finishTick();
        }
        assert food.itemsToReturn().get(3144) == 3L : "Worn borrowed items must stay on the reminder";
        food.clearBorrowed();
        assert !food.hasItemsToReturn() : "Clear must remove all borrowed items";

        GroupStorageWithdrawals filtered = new GroupStorageWithdrawals();
        filtered.setTrackable(id -> id != 3144);
        filtered.updateStorage(container(3144, 10, 4151, 1));
        filtered.updateInventory(93, container());
        filtered.updateStorage(container(3144, 6));
        filtered.updateInventory(93, container(3144, 4, 4151, 1));
        assert filtered.itemsToReturn().equals(Collections.singletonMap(4151, 1L))
            : "Food and potions must not be tracked as borrowed items";
        java.util.Map<Integer, Long> savedFood = new java.util.HashMap<>();
        savedFood.put(3144, 2L);
        savedFood.put(4151, 1L);
        filtered.restore(savedFood);
        assert filtered.itemsToReturn().equals(Collections.singletonMap(4151, 1L))
            : "Saved food and potions must be dropped when restoring";
    }

    private static void checkBorrowedItemSharing()
    {
        TrackerData.Member source = new TrackerData.Member();
        source.borrowedItems = Collections.singletonMap(385, 4L);

        TrackerData.Member optedOut = UploadCoordinator.snapshotForUpload(new ClanTrackerConfig() {}, source);
        assert !optedOut.borrowedItemsShared : "Borrowed item data must not be shared by default";
        assert optedOut.borrowedItems == null : "Opted-out snapshots must omit borrowed item data";

        ClanTrackerConfig optedInConfig = new ClanTrackerConfig()
        {
            @Override
            public boolean shareStorageWithdrawals()
            {
                return true;
            }
        };
        TrackerData.Member optedIn = UploadCoordinator.snapshotForUpload(optedInConfig, source);
        assert optedIn.borrowedItemsShared : "Opted-in snapshots must mark borrowed item sharing";
        assert optedIn.borrowedItems.equals(source.borrowedItems) : "Opted-in snapshots must include borrowed item quantities";

        java.util.Map<Integer, Long> saved = new java.util.HashMap<>();
        saved.put(385, 4L);
        saved.put(11802, 1L);
        String encoded = GroupStorageWithdrawals.encode(saved);
        assert GroupStorageWithdrawals.decode(encoded).equals(saved) : "Borrowed items must survive a client restart";
        assert GroupStorageWithdrawals.decode("385:4,bad,1:x").equals(Collections.singletonMap(385, 4L))
            : "Corrupted saved borrowed items must be skipped";
        GroupStorageWithdrawals restored = new GroupStorageWithdrawals();
        restored.restore(saved);
        assert restored.itemsToReturn().equals(saved) : "Restored borrowed items must show in the reminder";
    }

    private static ItemContainer container(int... items)
    {
        Item[] contents = new Item[items.length / 2];
        for (int i = 0; i < items.length; i += 2)
        {
            final int id = items[i];
            final int quantity = items[i + 1];
            contents[i / 2] = new Item(id, quantity);
        }
        return (ItemContainer) Proxy.newProxyInstance(ItemContainer.class.getClassLoader(),
            new Class<?>[]{ItemContainer.class}, (proxy, method, arguments) -> {
                if (method.getName().equals("getItems")) return contents;
                throw new AssertionError("Unexpected container access: " + method.getName());
            });
    }

    private static void checkLogoutInterceptorRemoved()
    {
        assert Arrays.stream(ClanTrackerPlugin.class.getDeclaredMethods())
            .noneMatch(method -> method.getName().equals("onMenuOptionClicked"))
            : "Logout reminders must not intercept menu actions";
    }

    private static void checkConnectionNotice() throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            ClanPanel panel = new ClanPanel(null);
            java.awt.Container header = (java.awt.Container) panel.getComponent(0);
            java.awt.Component notice = header.getComponent(1);
            panel.setSize(net.runelite.client.ui.PluginPanel.PANEL_WIDTH, 600);
            panel.doLayout();
            header.doLayout();
            assert notice.getPreferredSize().width <= header.getWidth() : "Consent notice must fit the sidebar";
            assert notice.getX() == 0 : "Consent notice must align with the sidebar's left edge";
            java.awt.Insets padding = ((javax.swing.JComponent) notice).getInsets();
            assert padding.top == 8 && padding.bottom == 8 : "Consent notice must have vertical spacing";
            assert notice.isVisible() : "Consent notice must be visible before opting in";
            panel.setConnectionEnabled(true);
            assert !notice.isVisible() : "Consent notice must hide after opting in";
            panel.clear("Not synced: connection unavailable");
            assert !notice.isVisible() : "Connection errors must not restore the consent notice";
            panel.setConnectionEnabled(false);
            assert notice.isVisible() : "Consent notice must return when connection is disabled";
            panel.setStatus("Live XP not synced: connection unavailable\nAlerts waiting for retry");
            javax.swing.JTextArea status = (javax.swing.JTextArea) header.getComponent(0);
            javax.swing.JPanel trackerCard = (javax.swing.JPanel) header.getComponent(2);
            javax.swing.JPanel trackerDetails = (javax.swing.JPanel) trackerCard.getComponent(1);
            javax.swing.JLabel coffer = (javax.swing.JLabel) trackerDetails.getComponent(1);
            javax.swing.JLabel threshold = (javax.swing.JLabel) trackerDetails.getComponent(3);
            javax.swing.JButton bankToggle = (javax.swing.JButton) header.getComponent(4);
            javax.swing.JComboBox<?> bankSelector = (javax.swing.JComboBox<?>) header.getComponent(6);
            javax.swing.JScrollPane bankScroll = (javax.swing.JScrollPane) panel.getComponent(1);
            panel.setDeathCofferValue(1234567);
            panel.setDeathCofferThreshold(10000);
            assert coffer.getText().equals("1,234,567 gp");
            assert threshold.getText().equals("Low alert at or below 10,000 gp");
            bankToggle.doClick();
            assert !bankSelector.isVisible() && !bankScroll.isVisible();
            assert coffer.isVisible() : "Collapsing bank snapshots must leave the coffer tracker visible";
            bankToggle.doClick();
            assert bankSelector.isVisible() && bankScroll.isVisible();
            panel.doLayout();
            header.doLayout();
            assert status.getLineWrap() && status.getWrapStyleWord();
            assert status.getPreferredSize().width <= header.getWidth() : "Status messages must fit the sidebar";
            assert status.getHeight() >= status.getPreferredSize().height : "Multiple statuses must not be clipped";
        });
    }

    private static void checkEventHandlers() throws Exception
    {
        ClanTrackerPlugin plugin = new ClanTrackerPlugin();
        set(plugin, "config", new ClanTrackerConfig() {
            @Override
            public boolean enableConnection() { return true; }
            @Override
            public boolean showMap() { return true; }
            @Override
            public boolean shareLocation()
            {
                return false;
            }

            @Override
            public boolean shareBank()
            {
                return true;
            }

            @Override
            public boolean shareChat()
            {
                return true;
            }

            @Override
            public boolean bossAlerts()
            {
                return true;
            }

            @Override
            public boolean dropAlerts()
            {
                return true;
            }

            @Override
            public boolean questAlerts()
            {
                return true;
            }
        });
        set(plugin, "running", true);
        set(plugin, "configManager", TestServices.configManager());
        set(plugin, "account", "Test Player");
        Field queued = ClanTrackerPlugin.class.getDeclaredField("events");
        queued.setAccessible(true);
        Object value = queued.get(plugin);
        assert value instanceof Queue;
        Queue<?> events = (Queue<?>) value;
        plugin.onChatMessage(chat(ChatMessageType.PUBLICCHAT, "Test Player", "Do not relay"));
        plugin.onChatMessage(chat(ChatMessageType.PRIVATECHAT, "Test Player", "Do not relay"));
        plugin.onChatMessage(chat(ChatMessageType.CLAN_CHAT, "Test Player", "Do not relay"));
        plugin.onChatMessage(chat(ChatMessageType.CLAN_GIM_CHAT, "Other Player", "Not our message"));
        assert events.isEmpty() : "Unrelated chat leaked";
        plugin.onChatMessage(chat(ChatMessageType.CLAN_GIM_CHAT, "<img=1>Test_Player", "Hello group"));
        assert events.size() == 1;
        Object first = events.poll();
        assert first instanceof TrackerData.Event;
        assert ((TrackerData.Event) first).message.equals("Hello group");
        plugin.onChatMessage(chat(ChatMessageType.GAMEMESSAGE, "", "Your Zulrah kill count is: <col=ff0000>127</col>."));
        plugin.onChatMessage(chat(ChatMessageType.GAMEMESSAGE, "", "Your Zulrah kill count is: <col=ff0000>127</col>."));
        assert events.size() == 1 : "Repeated KC should not create multiple alerts";
        events.clear();
        plugin.onChatMessage(chat(ChatMessageType.GAMEMESSAGE, "", "Your Lunar Chest count is: 12."));
        assert events.isEmpty() : "Lunar Chest must not create a boss alert";
        plugin.onChatMessage(chat(ChatMessageType.GAMEMESSAGE, "",
            "Congratulations! You have completed all of the hard tasks in the Varrock area."));
        assert events.size() == 1;
        TrackerData.Event diary = (TrackerData.Event) events.poll();
        assert diary.type.equals("diary") && diary.diary.equals("Varrock Hard");
        assert UploadCoordinator.eventEnabled(new ClanTrackerConfig() {
            public boolean questAlerts() { return true; }
        }, diary);
        assert !UploadCoordinator.eventEnabled(new ClanTrackerConfig() {}, diary);

        int[] currentQuest = new int[1];
        int[] completedQuest = {-1};
        WorldView worldView = (WorldView) Proxy.newProxyInstance(WorldView.class.getClassLoader(),
            new Class<?>[]{WorldView.class}, (proxy, method, arguments) -> {
                if (method.getName().equals("getBaseX"))
                {
                    return 3200;
                }
                if (method.getName().equals("getBaseY"))
                {
                    return 3200;
                }
                if (method.getName().equals("getPlane"))
                {
                    return 0;
                }
                if (method.getName().equals("isInstance"))
                {
                    return false;
                }
                throw new AssertionError("Unexpected world view access: " + method.getName());
            });
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
            (proxy, method, arguments) -> {
                if (method.getName().equals("runScript"))
                {
                    Object[] scriptArguments = (Object[]) arguments[0];
                    currentQuest[0] = ((Number) scriptArguments[1]).intValue();
                    return null;
                }
                if (method.getName().equals("getIntStack"))
                {
                    return new int[]{currentQuest[0] == completedQuest[0] ? 2 : 1};
                }
                if (method.getName().equals("getGameState"))
                {
                    return GameState.LOGGED_IN;
                }
                if (method.getName().equals("getWidget"))
                {
                    return null;
                }
                if (method.getName().equals("getWorld"))
                {
                    return 444;
                }
                if (method.getName().equals("getWorldView") ||
                    method.getName().equals("getTopLevelWorldView"))
                {
                    return worldView;
                }
                if (method.getName().equals("getLocalPlayer"))
                {
                    return Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                        (playerProxy, playerMethod, playerArguments) -> {
                            if (playerMethod.getName().equals("getLocalLocation"))
                            {
                                return LocalPoint.fromScene(10, 20);
                            }
                            if (playerMethod.getName().equals("getName"))
                            {
                                return "Test Player";
                            }
                            throw new AssertionError("Unexpected player access: " + playerMethod.getName());
                        });
                }
                if (method.getName().equals("getRealSkillLevel"))
                {
                    return 99;
                }
                if (method.getName().equals("getSkillExperience"))
                {
                    return 13034431;
                }
                if (method.getName().equals("getVarpValue") && ((Number) arguments[0]).intValue() == VarPlayerID.QP)
                {
                    return 40;
                }
                if (method.getName().equals("getVarbitValue"))
                {
                    return 0;
                }
                throw new AssertionError("Unexpected client access: " + method.getName());
            });
        set(plugin, "client", client);
        Method checkQuests = ClanTrackerPlugin.class.getDeclaredMethod("checkQuests");
        checkQuests.setAccessible(true);
        checkQuests.invoke(plugin);
        assert events.isEmpty() : "Quest login baseline generated alerts";
        completedQuest[0] = Quest.values()[0].getId();
        checkQuests.invoke(plugin);
        assert events.size() == 1 : "Quest completion should generate one alert";
        checkQuests.invoke(plugin);
        assert events.size() == 1 : "Unchanged quest generated another alert";

        ItemContainer emptyBank = (ItemContainer) Proxy.newProxyInstance(ItemContainer.class.getClassLoader(),
            new Class<?>[]{ItemContainer.class}, (proxy, method, arguments) -> {
                if (method.getName().equals("getItems"))
                {
                    return new Item[0];
                }
                throw new AssertionError("Unexpected container access");
            });
        plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.INVENTORY.getId(), emptyBank));
        assert get(plugin, "snapshot") == null : "Inventory event should not collect bank";
        plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.BANK.getId(), emptyBank));
        TrackerData.Member captured = (TrackerData.Member) get(plugin, "snapshot");
        assert captured.bank != null && captured.bank.isEmpty() : "Empty bank should be shared";
        assert captured.bankUpdatedAt > 0;
        assert captured.stats.size() >= 23;
        assert captured.questPoints == 40 : "Quest points should be shared";
        assert captured.killCounts.equals(java.util.Map.of("zulrah", 127)) : "Only positive saved KCs should be shared";
        assert UploadCoordinator.snapshotForUpload(new ClanTrackerConfig() {}, captured).killCounts == null :
            "Saved KCs require boss-alert consent";
        assert UploadCoordinator.snapshotForUpload(new ClanTrackerConfig() {
            public boolean bossAlerts() { return true; }
        }, captured).killCounts.equals(captured.killCounts);
        assert captured.position == null : "Location sharing is disabled";
        plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.GROUP_STORAGE_INV.getId(), emptyBank));
        assert get(plugin, "snapshot") == captured : "Inventory shown beside group storage is not shared storage";
        plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.GROUP_STORAGE.getId(), emptyBank));
        captured = (TrackerData.Member) get(plugin, "snapshot");
        assert captured.groupStorage != null && captured.groupStorage.isEmpty() : "Empty group storage should sync";
        assert captured.groupStorageUpdatedAt > 0;
        assert captured.bank != null : "Group storage must not replace the personal bank";

        WorldMapPointManager manager = new WorldMapPointManager();
        set(plugin, "mapManager", manager);
        Method updateMap = ClanTrackerPlugin.class.getDeclaredMethod("updateMap", List.class);
        updateMap.setAccessible(true);
        TrackerData.Member other = new TrackerData.Member();
        other.username = "Other Player";
        other.updatedAt = System.currentTimeMillis();
        other.position = new TrackerData.Position();
        other.position.x = 3200;
        other.position.y = 3200;
        other.position.world = 445;
        TrackerData.Member self = new TrackerData.Member();
        self.username = "Test Player";
        self.updatedAt = other.updatedAt;
        self.position = other.position;
        updateMap.invoke(plugin, Arrays.asList(other, self));
        List<?> points = (List<?>) get(manager, "worldMapPoints");
        assert points.size() == 2 : "Teammate and local player should have markers";
        WorldMapPoint point = (WorldMapPoint) points.get(0);
        assert point.getTooltip().contains("different world");
        assert point.getWorldPoint().getX() == 3200;
        WorldMapPoint selfPoint = (WorldMapPoint) points.get(1);
        assert selfPoint.getName().equals("You");
        assert selfPoint.getWorldPoint().equals(new WorldPoint(3210, 3220, 0));
        other.updatedAt -= 31000;
        BufferedImage customMarker = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        set(plugin, "mapIcon", customMarker);
        updateMap.invoke(plugin, Arrays.asList(other, self));
        assert ((WorldMapPoint) points.get(0)).getImage() == customMarker :
            "The local marker must use the website map icon";
        other.updatedAt += 31000;
        updateMap.invoke(plugin, Arrays.asList(other, self));
        assert points.size() == 2;
        assert ((WorldMapPoint) points.get(0)).getImage() == customMarker &&
            ((WorldMapPoint) points.get(1)).getImage() == customMarker :
            "Both teammate and local markers must use the website map icon";
        set(plugin, "mapIcon", null);
        other.updatedAt -= 31000;
        updateMap.invoke(plugin, Arrays.asList(other, self));
        assert ((WorldMapPoint) points.get(0)).getImage().getWidth() == 16 :
            "Without a downloaded map icon the original dot must remain available";
        assert points.size() == 1 && ((WorldMapPoint) points.get(0)).getName().equals("You") :
            "Stale teammate markers must expire while the local marker remains";
    }

    private static void checkLiveXp() throws Exception
    {
        LiveXpTracker tracker = new LiveXpTracker();
        long now = System.currentTimeMillis();
        tracker.observe("Test Player", new net.runelite.api.events.StatChanged(net.runelite.api.Skill.MINING, 1000, 9, 9),
            9, true, now);
        assert !tracker.hasPending() : "Login XP is a baseline, not a drop";
        tracker.observe("Test Player", new net.runelite.api.events.StatChanged(net.runelite.api.Skill.MINING, 1035, 9, 9),
            9, true, now);
        assert tracker.batch(now).drops.size() == 1;
        assert tracker.batch(now).drops.get(0).amount == 35 : "Mining drop must be exactly +35";
        tracker.observe("Test Player", new net.runelite.api.events.StatChanged(net.runelite.api.Skill.MINING, 1035, 9, 15),
            9, true, now);
        assert tracker.batch(now).drops.size() == 1 : "Boosted levels must not create XP";
        tracker.observe("Test Player", new net.runelite.api.events.StatChanged(net.runelite.api.Skill.MINING, 1070, 9, 9),
            9, false, now);
        tracker.observe("Test Player", new net.runelite.api.events.StatChanged(net.runelite.api.Skill.MINING, 1105, 9, 9),
            9, true, now);
        assert tracker.batch(now).drops.get(1).amount == 35 : "Disabled XP must not be replayed";
        tracker.clearBaseline();
        tracker.observe("Test Player", new net.runelite.api.events.StatChanged(net.runelite.api.Skill.MINING, 1200, 10, 10),
            10, true, now);
        assert tracker.batch(now).drops.size() == 2 : "World hop must establish a new baseline";
        tracker.clear();
        assert !tracker.hasPending();
        tracker.baseline(net.runelite.api.Skill.MINING, 1200);
        tracker.observe("Test Player", new net.runelite.api.events.StatChanged(net.runelite.api.Skill.MINING, 1235, 10, 10),
            10, true, now);
        String id = tracker.batch(now).drops.get(0).id;
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        int[] status = {500};
        String[] body = {""};
        server.createContext("/v1/xp-drops", exchange -> {
            body[0] = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            byte[] response = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status[0], response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try
        {
            BridgeClient bridge = new BridgeClient(new OkHttpClient(),
                "http://127.0.0.1:" + server.getAddress().getPort(), "a".repeat(48));
            Gson gson = new Gson();
            try
            {
                tracker.send(bridge, gson, tracker.batch(now));
                throw new AssertionError("Failed upload must throw");
            }
            catch (java.io.IOException expected)
            {
                assert tracker.hasPending() : "Failed uploads must remain queued";
            }
            status[0] = 200;
            tracker.send(bridge, gson, tracker.batch(now));
            assert !tracker.hasPending() : "Successful upload must acknowledge drops";
            assert body[0].contains(id) && body[0].contains("\"amount\":35");
            tracker.baseline(net.runelite.api.Skill.FISHING, 1000);
            tracker.observe("Test Player", new net.runelite.api.events.StatChanged(net.runelite.api.Skill.FISHING, 1020, 9, 9),
                9, true, now);
            assert tracker.batch(now + 26000).drops.isEmpty() : "Expired drops must not look live";
        }
        finally
        {
            server.stop(0);
        }
    }

    private static ChatMessage chat(ChatMessageType type, String name, String message)
    {
        ChatMessage event = new ChatMessage();
        event.setType(type);
        event.setName(name);
        event.setMessage(message);
        return event;
    }

    private static void set(Object instance, String name, Object value) throws Exception
    {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static Object get(Object instance, String name) throws Exception
    {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static void checkKill(String message, String boss, int count)
    {
        FeatureParser.Kill kill = FeatureParser.parseKill(message);
        assert kill != null : message;
        assert kill.boss.equals(boss) : kill.boss;
        assert kill.kc == count : kill.kc;
    }

    private static void rejects(Runnable action)
    {
        try
        {
            action.run();
            throw new AssertionError("Expected configuration rejection");
        }
        catch (IllegalArgumentException expected)
        {
            assert expected.getMessage() != null;
        }
    }
}
