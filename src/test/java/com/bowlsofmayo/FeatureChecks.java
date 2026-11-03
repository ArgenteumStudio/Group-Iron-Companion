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
        checkLiveXp();
        BufferedImage wide = new BufferedImage(200, 50, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D paint = wide.createGraphics();
        paint.setColor(java.awt.Color.RED);
        paint.fillRect(20, 10, 160, 30);
        paint.dispose();
        BufferedImage icon = ClanTrackerPlugin.iconImage(wide);
        assert icon.getWidth() == icon.getHeight() : "Sidebar icon should be square";
        assert (icon.getRGB(0, 0) >>> 24) == 0 : "Logo should be letterboxed, not stretched";
        assert (icon.getRGB(icon.getWidth() / 2, icon.getHeight() / 2) >>> 24) != 0;
        assert ClanTrackerPlugin.iconImage(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)) != null;
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
                tracker.send(bridge, gson, now);
                throw new AssertionError("Failed upload must throw");
            }
            catch (java.io.IOException expected)
            {
                assert tracker.hasPending() : "Failed uploads must remain queued";
            }
            status[0] = 200;
            tracker.send(bridge, gson, now);
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
