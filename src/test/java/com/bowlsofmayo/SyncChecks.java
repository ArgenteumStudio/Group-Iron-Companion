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
        checkSharingChanges();
        checkAlertOptOut();
        checkAccessChanges();
        checkEventFailures();
        checkInFlightChange();
        checkSnapshotCaptureChange();
        System.out.println("Sync regression checks passed (opt-out, access changes, rejected alerts, retries, in-flight changes).");
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
                Fixture receiver = serverChanged ? otherServer : fixture;
                assert receiver.uploads.stream().anyMatch(value -> value.contains("New Player"));
                assert receiver.uploads.stream().noneMatch(value -> value.contains("Old Player"));
                assert receiver.authorization.stream().allMatch(value -> value.equals("Bearer " + fixture.settings.token));
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
        public String memberToken() { return token; }
    }

    private static final class Fixture implements AutoCloseable
    {
        final ClanTrackerPlugin plugin = new ClanTrackerPlugin();
        final Settings settings = new Settings();
        final List<String> uploads = new CopyOnWriteArrayList<>();
        final List<String> authorization = new CopyOnWriteArrayList<>();
        final HttpServer server;
        volatile int eventStatus = 200;
        volatile int groupReads;
        volatile Runnable afterSnapshot;

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
                int status = path.equals("/v1/events") && body.contains("\"id\":\"bad\"") ? eventStatus : 200;
                if (path.equals("/v1/group")) groupReads++;
                if (path.equals("/v1/snapshot") && afterSnapshot != null) afterSnapshot.run();
                byte[] response = (status != 200 ? "{\"error\":\"Synthetic rejection\"}" :
                    path.equals("/v1/group") ? "{\"members\":[]}" : "{}").getBytes(StandardCharsets.UTF_8);
                if (path.equals("/map-icon"))
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
            set(plugin, "http", new OkHttpClient());
            set(plugin, "clientThread", new ClientThread());
            set(plugin, "mapManager", new net.runelite.client.ui.overlay.worldmap.WorldMapPointManager());
            SwingUtilities.invokeAndWait(() -> {
                try { set(plugin, "panel", new ClanPanel(null)); }
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
            xp.baseline(Skill.MINING, 1000);
            xp.observe("Old Player", new StatChanged(Skill.MINING, 1035, 9, 9), 9, true, System.currentTimeMillis());
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
            try
            {
                // This headless fixture has no toolbar; suppress navigation refresh only.
                set(plugin, "running", false);
                plugin.onConfigChanged(event);
            }
            catch (Exception error)
            {
                throw new AssertionError(error);
            }
            finally
            {
                try { set(plugin, "running", true); }
                catch (Exception error) { throw new AssertionError(error); }
            }
        }

        void sync() throws Exception
        {
            Method sync = ClanTrackerPlugin.class.getDeclaredMethod("synchronize", int.class);
            sync.setAccessible(true);
            sync.invoke(plugin, 0);
            SwingUtilities.invokeAndWait(() -> {});
        }

        void sendXp() throws Exception
        {
            Method send = ClanTrackerPlugin.class.getDeclaredMethod("sendLiveXp", int.class);
            send.setAccessible(true);
            send.invoke(plugin, 0);
            SwingUtilities.invokeAndWait(() -> {});
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
        }

        public void close() throws IOException
        {
            server.stop(0);
        }
    }
}
