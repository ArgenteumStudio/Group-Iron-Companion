package com.bowlsofmayo;

import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import okhttp3.OkHttpClient;

final class SyncChecks
{
    static void run() throws Exception
    {
        List<Throwable> uiErrors = new CopyOnWriteArrayList<>();
        Thread.UncaughtExceptionHandler[] original = new Thread.UncaughtExceptionHandler[1];
        SwingUtilities.invokeAndWait(() -> {
            original[0] = Thread.currentThread().getUncaughtExceptionHandler();
            Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> uiErrors.add(error));
        });
        try
        {
            checkSharingChanges();
            checkAlertOptOut();
            checkAccessChanges();
            checkEventFailures();
            checkInFlightChange();
            checkSnapshotCaptureChange();
            checkIndependentStatuses();
            checkOptionalMapFailure();
            checkStatusReset();
            checkRestartDiscardsAlerts();
            checkOptionalBankIconFailure();
            checkMixedXpExpiry();
            checkStopDuringUpload();
            checkXpAccessChangeBeforeSend();
            assert uiErrors.isEmpty() : "Asynchronous UI failures: " + uiErrors;
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> Thread.currentThread().setUncaughtExceptionHandler(original[0]));
        }
        System.out.println("Sync regression checks passed (opt-out, restart, access changes, retries, optional icons, XP expiry).");
    }

    private static void checkIndependentStatuses() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.seed();
            fixture.xpStatus = 500;
            fixture.sendXp();
            assert fixture.status().contains("Live XP not synced");
            fixture.sync();
            assert fixture.status().contains("Live XP not synced") : "Group success must not hide an XP failure";
            fixture.xpStatus = 200;
            fixture.sendXpAfterBackoff();
            assert !fixture.status().contains("Live XP") : "Successful XP retry must clear its own warning";

            fixture.seed();
            fixture.events().add(event("bad", "chat"));
            fixture.eventStatus = 500;
            fixture.sync();
            assert fixture.status().contains("Alerts waiting for retry");
            fixture.sendXpAfterBackoff();
            assert fixture.status().contains("Alerts waiting for retry") : "XP success must not clear alert errors";
            fixture.eventStatus = 200;
            fixture.sync();
            assert fixture.status().isEmpty();

            fixture.seed();
            fixture.xpStatus = 500;
            fixture.sendXpAfterBackoff();
            LiveXpTracker xp = (LiveXpTracker) get(fixture.plugin, "liveXp");
            xp.batch(System.currentTimeMillis()).drops.forEach(drop -> drop.observedAt -= 26000);
            fixture.sendXpAfterBackoff();
            assert fixture.status().contains("expired") : "Expired drops must not be reported as delivered";
            fixture.sync();
            assert fixture.status().contains("expired");
            fixture.settings.xp = false;
            fixture.change("shareLiveXp");
            assert fixture.status().isEmpty() : "Disabling XP must clear XP-specific warnings";

            fixture.events().add(event("bad", "chat"));
            fixture.eventStatus = 500;
            fixture.groupResponse = "{}";
            fixture.sync();
            assert fixture.status().contains("Alerts waiting for retry") && fixture.status().contains("Not synced:") :
                "A group failure must not suppress a new alert failure";
            fixture.eventStatus = 200;
            fixture.sync();
            assert !fixture.status().contains("Alerts waiting") && fixture.status().contains("Not synced:") :
                "Alert recovery must clear its warning even while group downloads fail";
        }
    }

    private static void checkOptionalMapFailure() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.groupResponse = "{\"members\":[{\"username\":\"Teammate\"}]}";
            fixture.mapStatus = 500;
            fixture.sync();
            fixture.drainClientTasks();
            ClanPanel panel = (ClanPanel) get(fixture.plugin, "panel");
            javax.swing.JComboBox<?> selector = (javax.swing.JComboBox<?>) get(panel, "selector");
            assert selector.getItemCount() == 2 : "Map failure must not clear valid member/storage choices";
            assert ((List<?>) get(fixture.plugin, "groupMembers")).size() == 1;
            assert fixture.status().contains("Map icon unavailable");
            fixture.mapStatus = 200;
            set(fixture.plugin, "mapIconFetchedAt", 0L);
            fixture.sync();
            fixture.drainClientTasks();
            assert fixture.status().isEmpty() : "Map recovery must clear the image warning";
            assert selector.getItemCount() == 2;
        }
    }

    private static void checkStatusReset() throws Exception
    {
        for (String key : Arrays.asList("enableConnection", "memberToken", "serverUrl"))
        {
            try (Fixture fixture = new Fixture())
            {
                fixture.seed();
                fixture.xpStatus = 500;
                fixture.sendXp();
                assert fixture.status().contains("Live XP not synced");
                if (key.equals("enableConnection")) fixture.settings.enabled = false;
                fixture.change(key);
                assert !fixture.status().contains("Live XP") : "Access/consent changes must reset old errors";
            }
        }
    }

    private static void checkRestartDiscardsAlerts() throws Exception
    {
        for (String change : Arrays.asList("none", "memberToken", "serverUrl"))
        {
            try (Fixture fixture = new Fixture(); Fixture otherServer = new Fixture())
            {
                fixture.change("memberToken");
                fixture.seed();
                for (String type : Arrays.asList("boss", "loot", "quest"))
                {
                    fixture.events().add(event("old-" + type, type));
                }
                fixture.queueLoot(event("pending-loot", "loot"));
                fixture.plugin.shutDown();
                assert fixture.events().isEmpty() : "Stopping must discard every unsent alert type";
                assert get(fixture.plugin, "snapshot") == null;
                assert !((LiveXpTracker) get(fixture.plugin, "liveXp")).hasPending();
                assert ((List<?>) get(fixture.plugin, "loot")).isEmpty();
                Method enqueue = ClanTrackerPlugin.class.getDeclaredMethod("enqueue", TrackerData.Event.class);
                enqueue.setAccessible(true);
                enqueue.invoke(fixture.plugin, event("stopped-callback", "chat"));
                assert fixture.events().isEmpty() : "Callbacks after shutdown must not refill the queue";
                if (change.equals("memberToken")) fixture.settings.token = "b".repeat(48);
                if (change.equals("serverUrl")) fixture.settings.url = otherServer.settings.url;
                fixture.restart();
                set(fixture.plugin, "account", "New Player");
                fixture.drainClientTasks();
                assert get(fixture.plugin, "account").equals("New Player") :
                    "A reset queued before shutdown must not reset the new lifecycle";
                assert !((Boolean) get(fixture.plugin, "accessResetPending"));
                assert fixture.uploads.isEmpty() && otherServer.uploads.isEmpty() :
                    "Restart must not replay old alerts, even with unchanged access";
                fixture.events().add(event("new", "chat"));
                fixture.sync();
                List<String> uploads = change.equals("serverUrl") ? otherServer.uploads : fixture.uploads;
                assert uploads.size() == 1 && uploads.get(0).contains("\"id\":\"new\"") :
                    "New alerts must still be delivered after restart";
                SwingUtilities.invokeAndWait(() -> {
                    assert fixture.sidebar.getTabCount() == 1 : "Restart must replace, not duplicate, navigation";
                });
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void checkOptionalBankIconFailure() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.snapshot("Old Player");
            BankSnapshots banks = (BankSnapshots) get(fixture.plugin, "bankSnapshots");
            ((java.util.Map<Integer, String>) get(banks, "itemIcons")).put(995, "synthetic-icon");
            fixture.groupResponse = "{\"members\":[{\"username\":\"Teammate\",\"bank\":[]}]}";
            fixture.iconStatus = 500;
            LiveXpTracker xp = (LiveXpTracker) get(fixture.plugin, "liveXp");
            xp.baseline(Skill.MINING, 1000);
            xp.observe("Old Player", new StatChanged(Skill.MINING, 1005, 9, 9), 9, true, System.currentTimeMillis());
            fixture.xpStatus = 500;
            fixture.sendXp();
            fixture.sync();
            fixture.drainClientTasks();
            javax.swing.JComboBox<?> selector = (javax.swing.JComboBox<?>) get(get(fixture.plugin, "panel"), "selector");
            assert selector.getItemCount() == 2 && ((List<?>) get(fixture.plugin, "groupMembers")).size() == 1 :
                "Optional icon upload failure must retain successfully downloaded group data";
            assert fixture.status().contains("Bank icons not synced") && !fixture.status().contains("Not synced:");
            fixture.iconStatus = 200;
            fixture.sync();
            assert !fixture.status().contains("Bank icons") && fixture.status().contains("Live XP not synced") :
                "Icon recovery must clear only its own warning";
            assert selector.getItemCount() == 2;
            fixture.xpStatus = 200;
            fixture.sendXpAfterBackoff();
            assert fixture.status().isEmpty();
        }
    }

    private static void checkMixedXpExpiry() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.seed();
            LiveXpTracker xp = (LiveXpTracker) get(fixture.plugin, "liveXp");
            long now = System.currentTimeMillis();
            String expiredId = xp.batch(now).drops.get(0).id;
            xp.batch(now).drops.get(0).observedAt -= 26000;
            xp.observe("Old Player", new StatChanged(Skill.MINING, 1040, 9, 9), 9, true, now);
            fixture.xpStatus = 500;
            fixture.sendXp();
            assert fixture.status().contains("expired") && fixture.status().contains("Live XP not synced") :
                "Expiration must be reported even when the remaining fresh batch fails";
            assert fixture.uploads.stream().noneMatch(value -> value.contains(expiredId)) :
                "Expired live drops must never be transmitted";
            assert xp.hasPending() : "Failed fresh drops must remain queued";
            fixture.xpStatus = 200;
            fixture.sendXpAfterBackoff();
            assert !xp.hasPending();
            assert fixture.status().contains("expired") && !fixture.status().contains("Live XP not synced") :
                "Delivery recovery must not erase the lost-drop notification";
            fixture.sync();
            assert fixture.status().contains("expired") : "Group recovery must not erase expiration";
            fixture.settings.xp = false;
            fixture.change("shareLiveXp");
            assert fixture.status().isEmpty() : "Changing XP consent must reset the expiration notification";
        }
    }

    private static void checkStopDuringUpload() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.seed();
            fixture.afterSnapshot = fixture.plugin::shutDown;
            fixture.sync();
            assert fixture.uploads.size() == 1 : "Only the already-started snapshot may finish";
            assert fixture.events().isEmpty() && fixture.groupReads == 0 :
                "Stopping during an upload must discard alerts and prevent subsequent old-lifecycle work";
            fixture.afterSnapshot = null;
            fixture.settings.token = "b".repeat(48);
            fixture.restart();
            assert fixture.uploads.size() == 1 : "Restart must not replay discarded in-flight session data";
        }
    }

    private static void checkXpAccessChangeBeforeSend() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.seed();
            fixture.settings.onTokenRead = () -> {
                fixture.settings.onTokenRead = null;
                fixture.settings.token = "b".repeat(48);
                fixture.change("memberToken");
            };
            fixture.sendXp();
            assert fixture.uploads.isEmpty() : "Access changes during request setup must invalidate the XP batch";
            assert !((LiveXpTracker) get(fixture.plugin, "liveXp")).hasPending();
        }
    }

    private static void checkSharingChanges() throws Exception
    {
        for (String key : Arrays.asList("shareBank", "shareLocation", "shareChat", "bossAlerts",
            "dropAlerts", "questAlerts", "shareLiveXp", "enableConnection"))
        {
            try (Fixture fixture = new Fixture())
            {
                fixture.seed();
                fixture.settings.disable(key);
                fixture.change(key);
                fixture.sync();
                assert fixture.uploads.stream().noneMatch(value -> value.startsWith("/v1/snapshot")) :
                    key + " must invalidate cached snapshots";
                boolean chatDisabled = key.equals("shareChat") || key.equals("enableConnection");
                assert fixture.uploads.stream().anyMatch(value -> value.contains("old chat")) == !chatDisabled :
                    "Unrelated sharing choices must be preserved: " + key;
                if (key.equals("shareBank"))
                {
                    fixture.drainClientTasks();
                    assert get(fixture.plugin, "bank") == null && get(fixture.plugin, "groupStorage") == null :
                        "Changing bank sharing must forget previously collected containers";
                    fixture.settings.bank = true;
                    fixture.change(key);
                    fixture.sync();
                    assert fixture.uploads.stream().noneMatch(value -> value.startsWith("/v1/snapshot")) :
                        "Re-enabling sharing must not restore an old snapshot";
                }
            }
        }
        try (Fixture fixture = new Fixture())
        {
            fixture.seed();
            fixture.settings.bank = false;
            fixture.settings.location = false;
            fixture.settings.chat = false;
            fixture.sync();
            assert fixture.uploads.stream().noneMatch(value -> value.contains("\"bank\":[{") ||
                value.contains("\"x\":3200") || value.contains("old chat")) :
                "Send-time consent checks must protect cached data even before config callbacks run";
        }
    }

    private static void checkAccessChanges() throws Exception
    {
        for (String key : Arrays.asList("memberToken", "serverUrl"))
        {
            try (Fixture fixture = new Fixture(); Fixture otherServer = new Fixture())
            {
                fixture.seed();
                fixture.settings.token = "b".repeat(48);
                boolean serverChanged = key.equals("serverUrl");
                if (serverChanged)
                {
                    fixture.settings.url = otherServer.settings.url;
                }
                fixture.change(key);
                assert get(fixture.plugin, "snapshot") == null : "Access changes must invalidate snapshots immediately";
                assert fixture.events().isEmpty() : "Access changes must discard old alerts";
                LiveXpTracker xp = (LiveXpTracker) get(fixture.plugin, "liveXp");
                assert !xp.hasPending() : "Access changes must discard old live XP immediately";
                fixture.sync();
                fixture.sendXp();
                assert fixture.uploads.isEmpty() && otherServer.uploads.isEmpty() :
                    "No old data may be uploaded with new credentials";
                Method enqueue = ClanTrackerPlugin.class.getDeclaredMethod("enqueue", TrackerData.Event.class);
                enqueue.setAccessible(true);
                enqueue.invoke(fixture.plugin, event("stale-callback", "chat"));
                assert fixture.events().isEmpty() : "Old client callbacks must not refill the queue while access is resetting";
                fixture.drainClientTasks();
                assert get(fixture.plugin, "account") == null : "Access changes must reset account state";
                fixture.snapshot("New Player");
                fixture.sync();
                List<String> uploads = serverChanged ? otherServer.uploads : fixture.uploads;
                List<String> authorization = serverChanged ? otherServer.authorization : fixture.authorization;
                assert uploads.stream().anyMatch(value -> value.contains("New Player"));
                assert uploads.stream().noneMatch(value -> value.contains("Old Player"));
                assert authorization.stream().allMatch(value -> value.equals("Bearer " + fixture.settings.token));
            }
        }
    }

    private static void checkAlertOptOut() throws Exception
    {
        String[] keys = {"shareChat", "bossAlerts", "dropAlerts", "questAlerts"};
        String[] types = {"chat", "boss", "loot", "quest"};
        for (int i = 0; i < keys.length; i++)
        {
            try (Fixture fixture = new Fixture())
            {
                for (String type : types) fixture.events().add(event(type, type));
                fixture.settings.disable(keys[i]);
                fixture.change(keys[i]);
                String disabledType = types[i];
                assert fixture.events().size() == 3 : "Opt-out must remove only the affected alert type";
                assert fixture.events().stream().noneMatch(event -> event.type.equals(disabledType));
                fixture.sync();
                assert fixture.uploads.size() == 3 : "Unrelated alerts must still be sent";
            }
        }
        try (Fixture fixture = new Fixture())
        {
            fixture.seed();
            LiveXpTracker xp = (LiveXpTracker) get(fixture.plugin, "liveXp");
            fixture.settings.disable("shareLiveXp");
            fixture.change("shareLiveXp");
            assert !xp.hasPending() : "Disabling live XP must clear old drops immediately";
            fixture.sendXp();
            assert fixture.uploads.isEmpty() : "Disabling live XP must stop uploads";
            fixture.drainClientTasks();
            assert !xp.hasPending();
            fixture.settings.xp = true;
            fixture.change("shareLiveXp");
            fixture.drainClientTasks();
            xp.baseline(Skill.MINING, 2000);
            xp.observe("Old Player", new StatChanged(Skill.MINING, 2010, 9, 9), 9, true, System.currentTimeMillis());
            fixture.sendXp();
            assert fixture.uploads.size() == 1 && fixture.uploads.get(0).contains("\"amount\":10") :
                "Re-enabling live XP must send only newly observed drops";
        }
    }

    private static void checkEventFailures() throws Exception
    {
        for (int code : new int[]{400, 403, 413, 422, 401, 429, 500})
        {
            try (Fixture fixture = new Fixture())
            {
                fixture.eventStatus = code;
                fixture.events().add(event("bad", "chat"));
                fixture.events().add(event("good", "chat"));
                fixture.sync();
                assert fixture.groupReads == 1 : "Rejected alert must not block group refresh for HTTP " + code;
                boolean permanent = code == 400 || code == 403 || code == 413 || code == 422;
                assert fixture.events().size() == (permanent ? 0 : 2) :
                    "Permanent rejection must discard only rejected alerts; temporary errors must retain the queue";
                fixture.eventStatus = 200;
                fixture.sync();
                assert fixture.events().isEmpty() : "Temporary failures must retry successfully";
                assert fixture.groupReads == 2;
            }
        }
    }

    private static void checkInFlightChange() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.seed();
            fixture.afterSnapshot = () -> {
                try { fixture.change("memberToken"); }
                catch (Exception error) { throw new AssertionError(error); }
            };
            fixture.sync();
            assert fixture.uploads.size() == 1 : "Already-started request may finish, but stale work must stop";
            assert fixture.groupReads == 0 : "Old access revision must not fetch or display group data";
        }
    }

    private static void checkSnapshotCaptureChange() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.settings.location = false;
            fixture.settings.bank = false;
            fixture.snapshot("Old Player");
            java.util.concurrent.atomic.AtomicBoolean changed = new java.util.concurrent.atomic.AtomicBoolean();
            net.runelite.api.Client client = (net.runelite.api.Client) java.lang.reflect.Proxy.newProxyInstance(
                net.runelite.api.Client.class.getClassLoader(), new Class<?>[]{net.runelite.api.Client.class},
                (proxy, method, arguments) -> {
                    switch (method.getName())
                    {
                        case "getVarpValue": return 0;
                        case "getSkillExperience": return 1000;
                        case "getRealSkillLevel":
                            if (changed.compareAndSet(false, true))
                            {
                                fixture.settings.chat = false;
                                fixture.change("shareChat");
                            }
                            return 9;
                        default: throw new AssertionError("Unexpected client access: " + method.getName());
                    }
                });
            set(fixture.plugin, "client", client);
            Method capture = ClanTrackerPlugin.class.getDeclaredMethod("captureSnapshot");
            capture.setAccessible(true);
            capture.invoke(fixture.plugin);
            assert changed.get() && get(fixture.plugin, "snapshot") == null :
                "A snapshot started before a settings change must not be published afterward";
        }
    }

    private static TrackerData.Event event(String id, String type)
    {
        TrackerData.Event event = new TrackerData.Event();
        event.id = id;
        event.username = "Old Player";
        event.type = type;
        event.message = "old chat";
        return event;
    }

    private static void set(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object get(Object target, String name) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static final class Settings implements ClanTrackerConfig
    {
        String url;
        String token = "a".repeat(48);
        boolean enabled = true;
        boolean bank = true;
        boolean location = true;
        boolean chat = true;
        boolean boss = true;
        boolean loot = true;
        boolean quest = true;
        boolean xp = true;
        Runnable onTokenRead;
        void disable(String key)
        {
            switch (key)
            {
                case "enableConnection": enabled = false; break;
                case "shareBank": bank = false; break;
                case "shareLocation": location = false; break;
                case "shareChat": chat = false; break;
                case "bossAlerts": boss = false; break;
                case "dropAlerts": loot = false; break;
                case "questAlerts": quest = false; break;
                case "shareLiveXp": xp = false; break;
                default: throw new AssertionError("Unexpected setting: " + key);
            }
        }
        public boolean enableConnection() { return enabled; }
        public boolean shareBank() { return bank; }
        public boolean shareLocation() { return location; }
        public boolean shareChat() { return chat; }
        public boolean bossAlerts() { return boss; }
        public boolean dropAlerts() { return loot; }
        public boolean questAlerts() { return quest; }
        public boolean shareLiveXp() { return xp; }
        public String serverUrl() { return url; }
        public String memberToken()
        {
            if (onTokenRead != null) onTokenRead.run();
            return token;
        }
    }

    private static final class Fixture implements AutoCloseable
    {
        final ClanTrackerPlugin plugin = new ClanTrackerPlugin();
        final Settings settings = new Settings();
        final List<String> uploads = new CopyOnWriteArrayList<>();
        final List<String> authorization = new CopyOnWriteArrayList<>();
        final HttpServer server;
        final javax.swing.JTabbedPane sidebar = new javax.swing.JTabbedPane();
        volatile int eventStatus = 200;
        volatile int xpStatus = 200;
        volatile int mapStatus = 200;
        volatile int iconStatus = 200;
        volatile String groupResponse = "{\"members\":[]}";
        volatile int groupReads;
        volatile Runnable afterSnapshot;
        boolean started;

        Fixture() throws Exception
        {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (exchange.getRequestMethod().equals("POST"))
                {
                    uploads.add(path + " " + body);
                    authorization.add(exchange.getRequestHeaders().getFirst("Authorization"));
                }
                int status = path.equals("/v1/events") && body.contains("\"id\":\"bad\"") ? eventStatus :
                    path.equals("/v1/xp-drops") ? xpStatus : path.equals("/map-icon") ? mapStatus :
                    path.equals("/v1/icons") ? iconStatus : 200;
                if (path.equals("/v1/group")) groupReads++;
                if (path.equals("/v1/snapshot") && afterSnapshot != null) afterSnapshot.run();
                byte[] response = (status != 200 ? "{\"error\":\"Synthetic rejection\"}" :
                    path.equals("/v1/group") ? groupResponse : "{}").getBytes(StandardCharsets.UTF_8);
                if (path.equals("/map-icon") && status == 200)
                {
                    try (java.io.InputStream input = ClanTrackerPlugin.class.getResourceAsStream("companion-icon.png"))
                    {
                        response = input.readAllBytes();
                    }
                }
                exchange.sendResponseHeaders(status, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            settings.url = "http://127.0.0.1:" + server.getAddress().getPort();
            set(plugin, "config", settings);
            set(plugin, "gson", new GsonBuilder().serializeNulls().create());
            set(plugin, "runeLiteGson", new GsonBuilder().create());
            set(plugin, "configManager", TestServices.configManager());
            set(plugin, "http", new OkHttpClient());
            set(plugin, "clientThread", new ClientThread());
            set(plugin, "mapManager", new net.runelite.client.ui.overlay.worldmap.WorldMapPointManager());
            SwingUtilities.invokeAndWait(() -> {
                try
                {
                    set(plugin, "panel", new ClanPanel(null));
                    java.lang.reflect.Constructor<net.runelite.client.ui.ClientUI> uiConstructor =
                        net.runelite.client.ui.ClientUI.class.getDeclaredConstructor(
                            net.runelite.client.config.RuneLiteConfig.class,
                            net.runelite.client.input.MouseManager.class,
                            net.runelite.api.Client.class,
                            net.runelite.client.config.ConfigManager.class,
                            javax.inject.Provider.class,
                            net.runelite.client.eventbus.EventBus.class,
                            boolean.class, String.class);
                    uiConstructor.setAccessible(true);
                    net.runelite.client.ui.ClientUI ui = uiConstructor.newInstance(
                        null, null, null, null, null, null, false, "Sync tests");
                    set(ui, "sidebar", sidebar);
                    java.lang.reflect.Constructor<net.runelite.client.ui.ClientToolbar> toolbarConstructor =
                        net.runelite.client.ui.ClientToolbar.class.getDeclaredConstructor(net.runelite.client.ui.ClientUI.class);
                    toolbarConstructor.setAccessible(true);
                    set(plugin, "toolbar", toolbarConstructor.newInstance(ui));
                }
                catch (Exception error) { throw new AssertionError(error); }
            });
            set(plugin, "running", true);
            server.start();
        }

        void snapshot(String username) throws Exception
        {
            TrackerData.Member snapshot = new TrackerData.Member();
            snapshot.username = username;
            snapshot.bankShared = true;
            snapshot.bank = new ArrayList<>();
            TrackerData.BankItem item = new TrackerData.BankItem();
            item.id = 995;
            item.qty = 100;
            item.name = "Coins";
            snapshot.bank.add(item);
            snapshot.position = new TrackerData.Position();
            snapshot.position.x = 3200;
            set(plugin, "snapshot", snapshot);
            set(plugin, "account", username);
            set(plugin, "bank", snapshot.bank);
            set(plugin, "groupStorage", snapshot.bank);
        }

        void seed() throws Exception
        {
            snapshot("Old Player");
            events().add(event("old", "chat"));
            LiveXpTracker xp = (LiveXpTracker) get(plugin, "liveXp");
            xp.clear();
            xp.baseline(Skill.MINING, 1000);
            xp.observe("Old Player", new StatChanged(Skill.MINING, 1035, 9, 9), 9, true, System.currentTimeMillis());
        }

        @SuppressWarnings("unchecked")
        void queueLoot(TrackerData.Event event) throws Exception
        {
            java.lang.reflect.Constructor<?> constructor = Class.forName(
                ClanTrackerPlugin.class.getName() + "$PendingLoot")
                .getDeclaredConstructor(TrackerData.Event.class, int.class);
            constructor.setAccessible(true);
            ((List<Object>) get(plugin, "loot")).add(constructor.newInstance(event, 3));
        }

        @SuppressWarnings("unchecked")
        Queue<TrackerData.Event> events() throws Exception
        {
            return (Queue<TrackerData.Event>) get(plugin, "events");
        }

        void change(String key)
        {
            ConfigChanged event = new ConfigChanged();
            event.setGroup("bowlsofmayo");
            event.setKey(key);
            plugin.onConfigChanged(event);
        }

        void sync() throws Exception
        {
            Method sync = ClanTrackerPlugin.class.getDeclaredMethod("synchronize", int.class);
            sync.setAccessible(true);
            sync.invoke(plugin, get(plugin, "generation"));
            SwingUtilities.invokeAndWait(() -> {});
        }

        void sendXp() throws Exception
        {
            Method send = ClanTrackerPlugin.class.getDeclaredMethod("sendLiveXp", int.class);
            send.setAccessible(true);
            send.invoke(plugin, get(plugin, "generation"));
            SwingUtilities.invokeAndWait(() -> {});
        }

        void sendXpAfterBackoff() throws Exception
        {
            set(plugin, "nextXpAttempt", 0L);
            sendXp();
        }

        void restart() throws Exception
        {
            plugin.startUp();
            started = true;
            ((java.util.concurrent.ScheduledExecutorService) get(plugin, "worker"))
                .submit(() -> {}).get(5, java.util.concurrent.TimeUnit.SECONDS);
            ((java.util.concurrent.ScheduledExecutorService) get(plugin, "xpWorker"))
                .submit(() -> {}).get(5, java.util.concurrent.TimeUnit.SECONDS);
            SwingUtilities.invokeAndWait(() -> {});
        }

        String status() throws Exception
        {
            String[] text = new String[1];
            SwingUtilities.invokeAndWait(() -> {
                try
                {
                    ClanPanel panel = (ClanPanel) get(plugin, "panel");
                    text[0] = ((javax.swing.text.JTextComponent) get(panel, "status")).getText();
                }
                catch (Exception error)
                {
                    throw new AssertionError(error);
                }
            });
            return text[0];
        }

        @SuppressWarnings("unchecked")
        void drainClientTasks() throws Exception
        {
            ClientThread clientThread = (ClientThread) get(plugin, "clientThread");
            Queue<java.util.function.BooleanSupplier> tasks =
                (Queue<java.util.function.BooleanSupplier>) get(clientThread, "invokes");
            java.util.function.BooleanSupplier task;
            while ((task = tasks.poll()) != null)
            {
                boolean completed = task.getAsBoolean();
                assert completed : "Unexpected deferred client task";
            }
            SwingUtilities.invokeAndWait(() -> {});
        }

        public void close() throws IOException
        {
            if (started)
            {
                plugin.shutDown();
            }
            server.stop(0);
            try
            {
                SwingUtilities.invokeAndWait(() -> {});
                SwingUtilities.invokeAndWait(() -> {});
            }

            catch (InterruptedException error)
            {
                Thread.currentThread().interrupt();
                throw new IOException(error);
            }
            catch (java.lang.reflect.InvocationTargetException error)
            {
                throw new IOException(error.getCause());
            }
        }
    }
}
