package com.bowlsofmayo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;

final class DeathCofferChecks
{
    static void run() throws Exception
    {
        assert ClanTrackerPlugin.parseDeathCofferBalance("Coffer: <col=ffffff>1,234,567</col> coins") == 1234567;
        assert ClanTrackerPlugin.parseDeathCofferBalance("Coffer: <col=ffffff>1</col> coin") == 1;
        assert ClanTrackerPlugin.parseDeathCofferBalance("Coffer: <col=ffffff>Empty</col>") == 0;
        assert ClanTrackerPlugin.parseDeathCofferBalance("Coffer: 0 coins") == 0;
        assert ClanTrackerPlugin.parseDeathCofferBalance("Coffer: 2,147,483,647 coins") == Integer.MAX_VALUE;
        assert ClanTrackerPlugin.parseDeathCofferBalance("Coffer: 2,147,483,648 coins") == null;
        assert ClanTrackerPlugin.parseDeathCofferBalance("Coffer: 9999999999999999999 coins") == null;
        assert ClanTrackerPlugin.parseDeathCofferBalance("Coffer: -1 coins") == null;
        assert ClanTrackerPlugin.parseDeathCofferBalance("Other interface: 123 coins") == null;
        assert ClanTrackerPlugin.parseDeathCofferBalance(null) == null;
        Fixture fixture = new Fixture();
        fixture.tick();
        fixture.assertBalance(Integer.MIN_VALUE, "Open Death's Coffer to sync");
        fixture.open = true;
        fixture.text = "Coffer: <col=ffffff>1,234,567</col> coins";
        fixture.tick();
        fixture.assertBalance(1234567, "1,234,567 gp");
        assert fixture.manager.getRSProfileConfiguration("bowlsofmayo", "deathCofferLastBalance", int.class)
            .equals(1234567) : "Confirmed balance must persist in character configuration";
        fixture.open = false;
        VarbitChanged unrelated = new VarbitChanged();
        unrelated.setVarpId(261);
        unrelated.setValue(0);
        fixture.plugin.onVarbitChanged(unrelated);
        for (int i = 0; i < 600; i++) fixture.tick();
        fixture.assertBalance(1234567, "1,234,567 gp");

        fixture.reset();
        fixture.tick();
        fixture.assertBalance(1234567, "1,234,567 gp");
        fixture.plugin = fixture.plugin();
        fixture.tick();
        fixture.assertBalance(1234567, "1,234,567 gp");

        set(fixture.manager, "rsProfileKey", "rsprofile.other");
        fixture.name = "Other Player";
        fixture.tick();
        fixture.assertBalance(Integer.MIN_VALUE, "Open Death's Coffer to sync");
        fixture.open = true;
        fixture.text = "Coffer: 50 coins";
        fixture.tick();
        fixture.assertBalance(50, "50 gp");
        fixture.text = "Coffer: <col=ffffff>Empty</col>";
        fixture.tick();
        fixture.assertBalance(0, "0 gp");
        fixture.open = false;
        fixture.reset();
        fixture.tick();
        fixture.assertBalance(0, "0 gp");

        set(fixture.manager, "rsProfileKey", "rsprofile.test");
        fixture.name = "Test Player";
        fixture.tick();
        fixture.assertBalance(1234567, "1,234,567 gp");
        fixture.open = true;
        fixture.hidden = true;
        fixture.text = "Coffer: Empty";
        fixture.tick();
        fixture.assertBalance(1234567, "1,234,567 gp");
        fixture.hidden = false;
        fixture.text = "Loading...";
        fixture.tick();
        fixture.assertBalance(1234567, "1,234,567 gp");
        fixture.enabled = false;
        fixture.text = "Coffer: Empty";
        fixture.tick();
        fixture.assertBalance(1234567, "1,234,567 gp");
        fixture.enabled = true;
        fixture.tick();
        fixture.assertBalance(0, "0 gp");
        System.out.println("Death's Coffer checks passed (temporary varp isolation, 600 closed ticks, saved profiles, restart, hidden UI, confirmed zero).");
    }

    private static final class Fixture
    {
        final ConfigManager manager = TestServices.configManager();
        final ClanPanel panel = new ClanPanel(null);
        String text = "";
        String name = "Test Player";
        boolean open;
        boolean hidden;
        boolean enabled = true;
        final Widget balance = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(),
            new Class<?>[]{Widget.class}, (proxy, method, arguments) -> {
                if (method.getName().equals("getText")) return text;
                if (method.getName().equals("isHidden")) return hidden;
                throw new AssertionError("Unexpected balance access: " + method.getName());
            });
        final Widget coffer = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(),
            new Class<?>[]{Widget.class}, (proxy, method, arguments) -> {
                if (method.getName().equals("isHidden")) return hidden;
                if (method.getName().equals("getChild") && (int) arguments[0] == 0) return balance;
                throw new AssertionError("Unexpected coffer access: " + method.getName());
            });
        final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
            new Class<?>[]{Player.class}, (proxy, method, arguments) -> {
                if (method.getName().equals("getName")) return name;
                throw new AssertionError("Unexpected player access: " + method.getName());
            });
        final Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(),
            new Class<?>[]{Client.class}, (proxy, method, arguments) -> {
                switch (method.getName())
                {
                    case "getGameState": return GameState.LOGGED_IN;
                    case "getLocalPlayer": return player;
                    case "getItemContainer": return null;
                    case "getWidget":
                        if ((int) arguments[0] == InterfaceID.SharedBank.FRAME)
                        {
                            return null;
                        }
                        assert (int) arguments[0] == InterfaceID.DeathCoffer.LEFT;
                        return open ? coffer : null;
                    default: throw new AssertionError("Must not read temporary varps: " + method.getName());
                }
            });
        ClanTrackerPlugin plugin = plugin();

        Fixture() throws Exception {}

        ClanTrackerPlugin plugin() throws Exception
        {
            ClanTrackerPlugin result = new ClanTrackerPlugin();
            set(result, "config", new ClanTrackerConfig() {
                public boolean trackDeathCoffer() { return enabled; }
                public boolean gimStorageReminder() { return false; }
                public boolean notifyDeathCofferLow() { return false; }
            });
            set(result, "configManager", manager);
            set(result, "client", client);
            set(result, "panel", panel);
            set(result, "running", true);
            return result;
        }

        void tick()
        {
            plugin.onGameTick(new GameTick());
        }

        void reset() throws Exception
        {
            Method reset = ClanTrackerPlugin.class.getDeclaredMethod("resetAccount");
            reset.setAccessible(true);
            reset.invoke(plugin);
        }

        void assertBalance(int value, String label) throws Exception
        {
            assert get(plugin, "deathCofferValue").equals(value);
            SwingUtilities.invokeAndWait(() -> {
                try
                {
                    assert ((JLabel) get(panel, "deathCoffer")).getText().equals(label) :
                        "Unexpected coffer label for " + name;
                }
                catch (Exception error)
                {
                    throw new AssertionError(error);
                }
            });
        }
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
}
