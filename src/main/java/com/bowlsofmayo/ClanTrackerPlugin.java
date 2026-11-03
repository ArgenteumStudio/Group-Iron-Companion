package com.bowlsofmayo;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
    name = "Group Iron Companion",
    description = "Private GIM banks, world map and Discord updates",
    tags = {"group", "ironman", "discord", "bank"}
)
public class ClanTrackerPlugin extends Plugin
{
    private static final Logger LOG = LoggerFactory.getLogger(ClanTrackerPlugin.class);
    private static final long LOGO_REFRESH_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final int LOGO_LIMIT = 5 * 1024 * 1024;
    private static final int ICON_SIZE = 32;
    private static final BufferedImage COMPANION_ICON = ImageUtil.loadImageResource(ClanTrackerPlugin.class, "companion-icon.png");

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ClanTrackerConfig config;
    @Inject private Gson runeLiteGson;
    @Inject private ClientToolbar toolbar;
    @Inject private ItemManager itemManager;
    @Inject private WorldMapPointManager mapManager;
    @Inject private okhttp3.OkHttpClient http;

    private final ConcurrentLinkedQueue<TrackerData.Event> events = new ConcurrentLinkedQueue<>();
    private final Map<Quest, QuestState> quests = new HashMap<>();
    private final Map<String, FeatureParser.Kill> kills = new HashMap<>();
    private final List<PendingLoot> loot = new ArrayList<>();
    private volatile TrackerData.Member snapshot;
    private volatile boolean running;
    private Gson gson;
    private volatile int generation;
    private volatile int accessRevision;
    private ScheduledExecutorService worker;
    private ScheduledExecutorService xpWorker;
    private final LiveXpTracker liveXp = new LiveXpTracker();
    private volatile long nextXpAttempt;
    private ClanPanel panel;
    private volatile NavigationButton button;
    private volatile BufferedImage logo;
    private volatile int logoHash;
    private volatile long logoFetchedAt;
    private volatile BufferedImage mapIcon;
    private volatile long mapIconFetchedAt;
    private static final long MAP_ICON_REFRESH_MILLIS = TimeUnit.SECONDS.toMillis(30);
    private String account;
    private List<TrackerData.BankItem> bank;
    private long bankUpdatedAt;
    private List<TrackerData.BankItem> groupStorage;
    private long groupStorageUpdatedAt;
    private int ticks;
    private boolean questsReady;
    private boolean questsDirty = true;
    private List<TrackerData.Member> groupMembers = Collections.emptyList();
    private final Map<Integer, String> itemIcons = new ConcurrentHashMap<>();
    private final Set<Integer> pendingIcons = new HashSet<>();

    @Provides
    ClanTrackerConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(ClanTrackerConfig.class);
    }

    @Override
    protected void startUp()
    {
        gson = runeLiteGson.newBuilder().serializeNulls().create();
        panel = new ClanPanel(itemManager);
        panel.setConnectionEnabled(config.enableConnection());
        running = true;
        logoFetchedAt = 0;
        mapIconFetchedAt = 0;
        setSidebarIcon(COMPANION_ICON);
        refreshSidebarIcon();
        int currentGeneration = ++generation;
        worker = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "bowlsofmayo-bridge");
            thread.setDaemon(true);
            return thread;
        });
        worker.scheduleWithFixedDelay(() -> synchronize(currentGeneration), 0, 5, TimeUnit.SECONDS);
        xpWorker = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "bowlsofmayo-live-xp");
            thread.setDaemon(true);
            return thread;
        });
        nextXpAttempt = 0;
        xpWorker.scheduleWithFixedDelay(() -> sendLiveXp(currentGeneration), 0, 250, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void shutDown()
    {
        running = false;
        generation++;
        flushLoot(true);
        if (worker != null)
        {
            worker.shutdownNow();
        }
        if (xpWorker != null)
        {
            xpWorker.shutdownNow();
        }
        mapManager.removeIf(GroupMapPoint.class::isInstance);
        NavigationButton current = button;
        SwingUtilities.invokeLater(() -> {
            if (current != null)
            {
                toolbar.removeNavigation(current);
            }
        });
        button = null;
        logo = null;
        mapIcon = null;
        logoHash = 0;
        if (!events.isEmpty())
        {
            LOG.warn("BowlsofMayo stopped with {} unsent alerts; they remain queued until re-enabled in this client", events.size());
        }
        snapshot = null;
        groupMembers = Collections.emptyList();
        resetAccount();
    }

    private void resetAccount()
    {
        liveXp.clear();
        account = null;
        bank = null;
        bankUpdatedAt = 0;
        groupStorage = null;
        groupStorageUpdatedAt = 0;
        ticks = 0;
        questsReady = false;
        questsDirty = true;
        quests.clear();
        kills.clear();
        loot.clear();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.LOGGED_IN)
        {
            liveXp.clearBaseline();
        }
        if (event.getGameState() == GameState.LOGIN_SCREEN)
        {
            flushLoot(true);
            resetAccount();
            snapshot = null;
            mapManager.removeIf(GroupMapPoint.class::isInstance);
        }
        else if (event.getGameState() != GameState.LOGGED_IN)
        {
            snapshot = null;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (!config.enableConnection())
        {
            return;
        }
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
        {
            return;
        }
        String name = Text.removeTags(client.getLocalPlayer().getName()).replace('\u00a0', ' ');
        if (!name.equals(account))
        {
            resetAccount();
            account = name;
        }
        ticks++;
        for (Skill skill : Skill.values())
        {
            if (skill != Skill.OVERALL)
            {
                liveXp.baseline(skill, client.getSkillExperience(skill));
            }
        }
        if (questsDirty)
        {
            checkQuests();
            questsDirty = false;
        }
        flushLoot(false);
        if (ticks == 1 || ticks % 10 == 0)
        {
            captureSnapshot();
            prepareIcons();
            updateMap(groupMembers);
        }
    }

    private void captureSnapshot()
    {
        TrackerData.Member member = new TrackerData.Member();
        member.username = account;
        member.observedAt = System.currentTimeMillis();
        member.bankShared = config.shareBank();
        member.questPoints = client.getVarpValue(VarPlayerID.QP);
        for (Skill skill : Skill.values())
        {
            if (skill == Skill.OVERALL)
            {
                continue;
            }
            TrackerData.Stat stat = new TrackerData.Stat();
            stat.level = client.getRealSkillLevel(skill);
            stat.xp = client.getSkillExperience(skill);
            member.stats.put(skill.name(), stat);
        }
        if (config.shareLocation())
        {
            WorldPoint point = WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation());
            member.position = new TrackerData.Position();
            member.position.x = point.getX();
            member.position.y = point.getY();
            member.position.plane = point.getPlane();
            member.position.world = client.getWorld();
        }
        if (config.shareBank() && bank != null)
        {
            member.bank = bank;
            member.bankUpdatedAt = bankUpdatedAt;
        }
        if (config.shareBank() && groupStorage != null)
        {
            member.groupStorage = groupStorage;
            member.groupStorageUpdatedAt = groupStorageUpdatedAt;
        }
        snapshot = member;
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        if (account == null || client.getGameState() != GameState.LOGGED_IN ||
            event.getSkill() == null || event.getSkill() == Skill.OVERALL)
        {
            return;
        }
        liveXp.observe(account, event, client.getRealSkillLevel(event.getSkill()),
            config.shareLiveXp(), System.currentTimeMillis());
    }

    private void sendLiveXp(int expectedGeneration)
    {
        if (!active(expectedGeneration) || !config.enableConnection() || !config.shareLiveXp() || !liveXp.hasPending() ||
            System.currentTimeMillis() < nextXpAttempt)
        {
            return;
        }
        try
        {
            liveXp.send(new BridgeClient(http, config.serverUrl(), config.memberToken()), gson,
                System.currentTimeMillis());
        }
        catch (IOException | IllegalArgumentException error)
        {
            nextXpAttempt = System.currentTimeMillis() + 5000;
            LOG.warn("BowlsofMayo live XP sync failed: {}", error.getMessage());
            showStatus("Live XP not synced: " + error.getMessage());
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (!config.shareBank() || account == null || client.getGameState() != GameState.LOGGED_IN ||
            (event.getContainerId() != InventoryID.BANK.getId() &&
                event.getContainerId() != InventoryID.GROUP_STORAGE.getId()))
        {
            return;
        }
        if (event.getContainerId() == InventoryID.GROUP_STORAGE.getId())
        {
            groupStorage = readItems(event.getItemContainer());
            groupStorageUpdatedAt = System.currentTimeMillis();
        }
        else
        {
            bank = readItems(event.getItemContainer());
            bankUpdatedAt = System.currentTimeMillis();
        }
        captureSnapshot();
    }

    private List<TrackerData.BankItem> readItems(ItemContainer container)
    {
        List<TrackerData.BankItem> result = new ArrayList<>();
        for (Item item : container.getItems())
        {
            if (item.getId() > 0 && item.getQuantity() > 0)
            {
                TrackerData.BankItem entry = new TrackerData.BankItem();
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

    private static List<TrackerData.BankItem> combinedItems(List<TrackerData.BankItem> personal,
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

    private void prepareIcons()
    {
        if (!config.shareBank())
        {
            return;
        }
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

    private void checkQuests()
    {
        // Login establishes a baseline; old completions must not generate new alerts.
        for (Quest quest : Quest.values())
        {
            QuestState state = quest.getState(client);
            QuestState previous = quests.put(quest, state);
            if (questsReady && config.questAlerts() && previous != null &&
                previous != QuestState.FINISHED && state == QuestState.FINISHED)
            {
                TrackerData.Event event = event("quest");
                event.quest = quest.getName();
                enqueue(event);
            }
        }
        questsReady = true;
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        questsDirty = true;
    }

    @Subscribe
    public void onChatMessage(ChatMessage message)
    {
        if (account == null)
        {
            return;
        }
        String text = Text.removeTags(message.getMessage());
        if (message.getType() == ChatMessageType.CLAN_GIM_CHAT && config.shareChat() &&
            FeatureParser.samePlayer(Text.removeTags(message.getName()), account))
        {
            // Relay only the sender's own messages to avoid duplicates across group clients.
            TrackerData.Event event = event("chat");
            event.message = text;
            enqueue(event);
        }
        if (message.getType() != ChatMessageType.GAMEMESSAGE)
        {
            return;
        }
        FeatureParser.Kill kill = FeatureParser.parseKill(text);
        if (kill == null)
        {
            return;
        }
        FeatureParser.Kill previous = kills.put(FeatureParser.sourceKey(kill.boss), kill);
        if (config.bossAlerts() && (previous == null || previous.kc != kill.kc))
        {
            TrackerData.Event event = event("boss");
            event.id = "boss:" + FeatureParser.sourceKey(kill.boss) + ":" + kill.kc;
            event.boss = kill.boss;
            event.kc = kill.kc;
            enqueue(event);
        }
    }

    @Subscribe
    public void onLootReceived(LootReceived received)
    {
        if (account == null || !config.dropAlerts() || received.getType().name().equals("PLAYER"))
        {
            return;
        }
        TrackerData.Event event = event("loot");
        event.source = received.getName();
        event.items = new ArrayList<>();
        for (ItemStack item : received.getItems())
        {
            if (item.getId() <= 0 || item.getQuantity() <= 0)
            {
                continue;
            }
            int price = (int) Math.min(Integer.MAX_VALUE, Math.max(0, itemManager.getItemPrice(item.getId())));
            if ((long) price * item.getQuantity() < config.minimumDropValue())
            {
                continue;
            }
            TrackerData.BankItem entry = new TrackerData.BankItem();
            entry.id = item.getId();
            entry.qty = item.getQuantity();
            entry.name = itemManager.getItemComposition(item.getId()).getName();
            entry.price = (long) price;
            event.items.add(entry);
        }
        if (!event.items.isEmpty())
        {
            loot.add(new PendingLoot(event, ticks + 3));
        }
    }

    private void flushLoot(boolean force)
    {
        loot.removeIf(pending -> {
            if (!force && pending.tick > ticks)
            {
                return false;
            }
            FeatureParser.Kill kill = kills.get(FeatureParser.sourceKey(pending.event.source));
            if (kill != null && System.currentTimeMillis() - kill.observedAt < 15000)
            {
                pending.event.kc = kill.kc;
            }
            enqueue(pending.event);
            return true;
        });
    }

    private TrackerData.Event event(String type)
    {
        TrackerData.Event event = new TrackerData.Event();
        event.id = UUID.randomUUID().toString();
        event.username = account;
        event.type = type;
        return event;
    }

    private void enqueue(TrackerData.Event event)
    {
        if (!config.enableConnection())
        {
            return;
        }
        if (events.size() >= 2000)
        {
            LOG.error("BowlsofMayo alert queue full; cannot queue {} alert", event.type);
            showStatus("Alert queue full! Check bridge connection.");
            return;
        }
        events.add(event);
    }

    private boolean active(int expectedGeneration)
    {
        return running && generation == expectedGeneration;
    }

    private void synchronize(int expectedGeneration)
    {
        int expectedAccessRevision = accessRevision;
        if (!config.enableConnection())
        {
            clearGroupData("Connection disabled - no data sent.", expectedGeneration, expectedAccessRevision);
            return;
        }
        if (!active(expectedGeneration))
        {
            return;
        }
        try
        {
            BridgeClient bridge = new BridgeClient(http, config.serverUrl(), config.memberToken());
            TrackerData.Member current = snapshot;
            if (current != null && config.enableConnection() && accessRevision == expectedAccessRevision)
            {
                bridge.post("/v1/snapshot", gson.toJson(current));
            }
            for (int count = 0; count < 50 && !events.isEmpty() && active(expectedGeneration) &&
                config.enableConnection() && accessRevision == expectedAccessRevision; count++)
            {
                TrackerData.Event event = events.peek();
                bridge.post("/v1/events", gson.toJson(event));
                if (!active(expectedGeneration))
                {
                    return;
                }
                events.remove(event);
            }
            if (!config.enableConnection() || accessRevision != expectedAccessRevision)
            {
                return;
            }
            TrackerData.Group group = gson.fromJson(bridge.get("/v1/group"), TrackerData.Group.class);
            if (group == null || group.members == null)
            {
                throw new IOException("Bridge returned an invalid group response");
            }
            if (current != null && current.bankShared && config.enableConnection() &&
                accessRevision == expectedAccessRevision)
            {
                TrackerData.Icons upload = new TrackerData.Icons();
                upload.username = current.username;
                for (TrackerData.BankItem item : combinedItems(current.bank, current.groupStorage))
                {
                    String image = itemIcons.get(item.id);
                    if (image != null && (group.icons == null || !group.icons.containsKey(item.id)))
                    {
                        upload.icons.put(item.id, image);
                        if (upload.icons.size() == 32)
                        {
                            break;
                        }
                    }
                }
                if (!upload.icons.isEmpty())
                {
                    bridge.post("/v1/icons", gson.toJson(upload));
                }
            }
            SwingUtilities.invokeLater(() -> {
                if (active(expectedGeneration) && accessRevision == expectedAccessRevision)
                {
                    panel.update(group);
                }
            });
            clientThread.invokeLater(() -> {
                if (active(expectedGeneration) && accessRevision == expectedAccessRevision)
                {
                    groupMembers = group.members;
                    updateMap(group.members);
                }
            });
            if (active(expectedGeneration) && config.enableConnection() && accessRevision == expectedAccessRevision &&
                System.currentTimeMillis() - mapIconFetchedAt > MAP_ICON_REFRESH_MILLIS)
            {
                mapIconFetchedAt = System.currentTimeMillis();
                fetchMapIcon(bridge, expectedGeneration);
            }
            if (active(expectedGeneration))
            {
                showStatus("");
            }
            if (config.enableConnection() && accessRevision == expectedAccessRevision &&
                config.sidebarIcon() == SidebarIcon.GROUP_LOGO &&
                System.currentTimeMillis() - logoFetchedAt > LOGO_REFRESH_MILLIS)
            {
                logoFetchedAt = System.currentTimeMillis();
                fetchLogo(bridge, expectedGeneration);
            }
        }
        catch (IOException | IllegalArgumentException | com.google.gson.JsonParseException error)
        {
            LOG.warn("BowlsofMayo bridge sync failed: {}", error.getMessage());
            clearGroupData("Not synced: " + error.getMessage(), expectedGeneration, expectedAccessRevision);
        }
    }

    private void clearGroupData(String message, int expectedGeneration, int expectedAccessRevision)
    {
        SwingUtilities.invokeLater(() -> {
            if (active(expectedGeneration) && accessRevision == expectedAccessRevision)
            {
                panel.clear(message);
                panel.setConnectionEnabled(config.enableConnection());
            }
        });
        clientThread.invokeLater(() -> {
            if (active(expectedGeneration) && accessRevision == expectedAccessRevision)
            {
                groupMembers = Collections.emptyList();
                mapManager.removeIf(GroupMapPoint.class::isInstance);
            }
        });
    }

    private void updateMap(List<TrackerData.Member> members)
    {
        mapManager.removeIf(GroupMapPoint.class::isInstance);
        if (!config.showMap() || client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }
        for (TrackerData.Member member : members)
        {
            TrackerData.Position position = member.position;
            if (position == null || System.currentTimeMillis() - member.updatedAt > 30000 ||
                FeatureParser.samePlayer(member.username, account))
            {
                continue;
            }
            GroupMapPoint point = new GroupMapPoint(
                new WorldPoint(position.x, position.y, position.plane), mapMarker());
            point.setName(member.username);
            point.setTooltip(member.username + " - World " + position.world +
                (position.world == client.getWorld() ? "" : " (different world)"));
            point.setSnapToEdge(true);
            point.setJumpOnClick(true);
            mapManager.add(point);
        }

        Player localPlayer = client.getLocalPlayer();
        if (localPlayer != null)
        {
            WorldPoint position = WorldPoint.fromLocalInstance(client, localPlayer.getLocalLocation());
            GroupMapPoint point = new GroupMapPoint(position, mapMarker());
            point.setName("You");
            point.setTooltip((account == null ? localPlayer.getName() : account) +
                " - World " + client.getWorld());
            point.setSnapToEdge(true);
            point.setJumpOnClick(true);
            mapManager.add(point);
        }
    }

    private void showStatus(String status)
    {
        SwingUtilities.invokeLater(() -> {
            if (running)
            {
                panel.setStatus(status);
            }
        });
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!"bowlsofmayo".equals(event.getGroup()))
        {
            return;
        }
        switch (event.getKey())
        {
            case "enableConnection":
                accessRevision++;
                snapshot = null;
                events.clear();
                clientThread.invokeLater(this::resetAccount);
                clearGroupData(config.enableConnection() ? "Waiting for sync." :
                    "Connection disabled - no data sent.", generation, accessRevision);
                break;
            case "shareLiveXp":
                clientThread.invokeLater(liveXp::clear);
                break;
            case "serverUrl":
            case "memberToken":
                accessRevision++;
                clearGroupData("Access changed - waiting for sync.", generation, accessRevision);
                logo = null;
                mapIcon = null;
                mapIconFetchedAt = 0;
                logoHash = 0;
                logoFetchedAt = 0;
                refreshSidebarIcon();
                break;
            case "sidebarIcon":
            case "sidebarItemId":
                logoFetchedAt = 0;
                refreshSidebarIcon();
                break;
            default:
                break;
        }
    }

    private void refreshSidebarIcon()
    {
        if (!running)
        {
            return;
        }
        SidebarIcon choice = config.sidebarIcon();
        if (choice == SidebarIcon.COMPANION_SHIELD)
        {
            setSidebarIcon(COMPANION_ICON);
            return;
        }
        if (choice == SidebarIcon.GROUP_LOGO)
        {
            BufferedImage current = logo;
            setSidebarIcon(current != null ? current : COMPANION_ICON);
            return;
        }
        if (choice == SidebarIcon.DOT)
        {
            setSidebarIcon(marker());
            return;
        }
        int itemId = choice == SidebarIcon.CUSTOM_ITEM ? config.sidebarItemId() : choice.itemId();
        AsyncBufferedImage image = itemManager.getImage(itemId, choice.quantity(), choice.quantity() > 1);
        image.onLoaded(() -> {
            if (running && config.sidebarIcon() == choice)
            {
                setSidebarIcon(iconImage(image));
            }
        });
    }

    private void fetchLogo(BridgeClient bridge, int expectedGeneration)
    {
        try
        {
            byte[] bytes = bridge.getBytes("/logo", LOGO_LIMIT);
            int hash = Arrays.hashCode(bytes);
            if (hash == logoHash && logo != null)
            {
                return;
            }

            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null)
            {
                throw new IOException("unsupported image format");
            }
            logo = iconImage(image);
            logoHash = hash;
            if (active(expectedGeneration) && config.sidebarIcon() == SidebarIcon.GROUP_LOGO)
            {
                setSidebarIcon(logo);
            }
        }
        catch (IOException error)
        {
            LOG.debug("Group logo unavailable: {}", error.getMessage());
        }
    }

    private BufferedImage mapMarker()
    {
        BufferedImage current = mapIcon;
        return current != null ? current : marker();
    }

    private void fetchMapIcon(BridgeClient bridge, int expectedGeneration) throws IOException
    {
        byte[] bytes = bridge.getBytes("/map-icon", 32768);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null || image.getWidth() > 32 || image.getHeight() > 32)
        {
            throw new IOException("Map icon must be a PNG no larger than 32 x 32");
        }
        clientThread.invokeLater(() -> {
            if (active(expectedGeneration))
            {
                mapIcon = image;
                updateMap(groupMembers);
            }
        });
    }

    private void setSidebarIcon(BufferedImage icon)
    {
        SwingUtilities.invokeLater(() -> {
            if (!running || panel == null)
            {
                return;
            }
            NavigationButton previous = button;
            button = NavigationButton.builder().tooltip("Group Iron Companion")
                .icon(icon).priority(7).panel(panel).build();
            if (previous != null)
            {
                toolbar.removeNavigation(previous);
            }
            toolbar.addNavigation(button);
        });
    }

    static BufferedImage iconImage(BufferedImage source)
    {
        int minX = source.getWidth(), minY = source.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < source.getHeight(); y++)
        {
            for (int x = 0; x < source.getWidth(); x++)
            {
                if ((source.getRGB(x, y) >>> 24) != 0)
                {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0)
        {
            return marker();
        }
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        BufferedImage current = source.getSubimage(minX, minY, width, height);
        while (Math.max(current.getWidth(), current.getHeight()) > ICON_SIZE * 2)
        {
            int w = Math.max(1, current.getWidth() / 2);
            int h = Math.max(1, current.getHeight() / 2);
            current = draw(current, w, h, w, h, 0, 0);
        }
        double scale = (double) ICON_SIZE / Math.max(current.getWidth(), current.getHeight());
        int w = Math.max(1, (int) Math.round(current.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(current.getHeight() * scale));
        return draw(current, ICON_SIZE, ICON_SIZE, w, h, (ICON_SIZE - w) / 2, (ICON_SIZE - h) / 2);
    }

    private static BufferedImage draw(BufferedImage source, int canvasW, int canvasH, int w, int h, int x, int y)
    {
        BufferedImage result = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(source, x, y, w, h, null);
        graphics.dispose();
        return result;
    }

    private static BufferedImage marker()
    {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.CYAN);
        graphics.fillOval(1, 1, 14, 14);
        graphics.setColor(Color.BLACK);
        graphics.drawOval(1, 1, 14, 14);
        graphics.dispose();
        return image;
    }

    private static final class GroupMapPoint extends WorldMapPoint
    {
        private GroupMapPoint(WorldPoint point, BufferedImage image)
        {
            super(point, image);
        }
    }

    private static final class PendingLoot
    {
        private final TrackerData.Event event;
        private final int tick;

        private PendingLoot(TrackerData.Event event, int tick)
        {
            this.event = event;
            this.tick = tick;
        }
    }
}
