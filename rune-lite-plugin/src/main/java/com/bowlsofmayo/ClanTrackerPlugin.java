package com.bowlsofmayo;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.api.events.SkillExperienceChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.navigation.NavigationButton;
import net.runelite.client.ui.PluginToolbar;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.game.ItemManager;
import net.runelite.api.ItemContainer;
import net.runelite.api.InventoryID;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.RuneLite;
import javax.swing.ImageIcon;
import net.runelite.client.callback.ClientThread;

import javax.inject.Named;
import net.runelite.client.config.ConfigManager;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import javax.inject.Inject;
import net.runelite.api.Client;

@PluginDescriptor(
    name = "BowlsofMayo Clan Tracker"
)
public class ClanTrackerPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private OverlayManager overlayManager;

    private PositionOverlay positionOverlay;

    @Inject
    private ItemManager itemManager;

    @Inject
    private ClanTrackerConfig config;

    @Inject
    private ClientToolbar clientToolbar;

    private NavigationButton navButton;
    private ItemGridPanel clanPanel;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ScheduledExecutorService executor;

    private ScheduledFuture<?> pollTask;

    @Override
    protected void startUp() throws Exception
    {
        // plugin startup
        positionOverlay = new PositionOverlay(client, this);
        overlayManager.add(positionOverlay);
        startPolling();

        // create and add panel button (tiled)
        clanPanel = new ItemGridPanel(itemManager);
        final ImageIcon icon = new ImageIcon(new byte[0]);
        navButton = NavigationButton.builder()
            .tooltip("BowlsofMayo")
            .icon(icon)
            .priority(7)
            .panel(clanPanel)
            .build();
        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown() throws Exception
    {
        // plugin shutdown
        overlayManager.remove(positionOverlay);
        stopPolling();
        if (navButton != null) clientToolbar.removeNavigation(navButton);
    }

    private void startPolling()
    {
        stopPolling();
        pollTask = executor.scheduleAtFixedRate(() -> {
            try {
                sendPosition();
                fetchPositions();
            } catch (Throwable t) {
                // ignore
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    private void stopPolling()
    {
        if (pollTask != null) {
            pollTask.cancel(true);
            pollTask = null;
        }
    }

    private void sendPosition()
    {
        if (client.getLocalPlayer() == null) return;
        WorldPoint wp = client.getLocalPlayer().getWorldLocation();
        String username = client.getLocalPlayer().getName();
        String clan = escape(config.clanName());
        String json = String.format("{\"clan\":\"%s\",\"username\":\"%s\",\"x\":%d,\"y\":%d,\"plane\":%d}", clan, escape(username), wp.getX(), wp.getY(), wp.getPlane());
        postJson(config.serverUrl() + "/position", json);
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged ev)
    {
        try {
            ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
            ItemContainer equip = client.getItemContainer(InventoryID.EQUIPMENT);
            java.util.Map<Integer,Integer> invMap = new java.util.HashMap<>();
            java.util.Map<Integer,Integer> eqMap = new java.util.HashMap<>();
            if (inv != null) {
                int[] ids = inv.getItemIds();
                int[] qty = inv.getItemQuantities();
                for (int i=0;i<ids.length;i++) {
                    int id = ids[i];
                    if (id <= 0) continue;
                    invMap.put(id, invMap.getOrDefault(id,0)+qty[i]);
                }
            }
            if (equip != null) {
                int[] ids = equip.getItemIds();
                int[] qty = equip.getItemQuantities();
                for (int i=0;i<ids.length;i++) {
                    int id = ids[i];
                    if (id <= 0) continue;
                    eqMap.put(id, eqMap.getOrDefault(id,0)+qty[i]);
                }
            }
            // update UI on client thread
            clientThread.invokeLater(() -> clanPanel.updateInventoryDisplay(invMap, eqMap));
            // send to server
            String clan = escape(config.clanName());
            String username = client.getLocalPlayer() != null ? escape(client.getLocalPlayer().getName()) : "unknown";
            StringBuilder sb = new StringBuilder();
            sb.append("{\"clan\":\"").append(clan).append("\",\"username\":\"").append(username).append('\"');
            sb.append(",\"inventory\":[");
            boolean first=true;
            for (java.util.Map.Entry<Integer,Integer> e : invMap.entrySet()) {
                if (!first) sb.append(','); first=false;
                sb.append(String.format("{\"id\":%d,\"qty\":%d}", e.getKey(), e.getValue()));
            }
            sb.append("],\"equipment\":[");
            first=true;
            for (java.util.Map.Entry<Integer,Integer> e : eqMap.entrySet()) {
                if (!first) sb.append(','); first=false;
                sb.append(String.format("{\"id\":%d,\"qty\":%d}", e.getKey(), e.getValue()));
            }
            sb.append("]}");
            postJson(config.serverUrl() + "/inventory", sb.toString());
        } catch (Exception ex) {
            // ignore
        }
    }

    private void fetchPositions()
    {
        try {
            String clanEnc = java.net.URLEncoder.encode(config.clanName(), "UTF-8");
            java.net.URL url = new java.net.URL(config.serverUrl() + "/positions/" + clanEnc);
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(2000);
            con.setReadTimeout(2000);
            int code = con.getResponseCode();
            if (code == 200) {
                try (java.io.InputStream is = con.getInputStream()) {
                    java.util.Scanner s = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A");
                    String body = s.hasNext() ? s.next() : "";
                    positionOverlay.updatePositions(body);
                }
            }
            con.disconnect();
        } catch (Exception e) {
            // ignore
        }
    }

    @Subscribe
    public void onSkillExperienceChanged(SkillExperienceChanged event)
    {
        String json = String.format("{\"type\":\"xp\",\"skill\":\"%s\",\"xp\":%d}", event.getSkill().name(), event.getSkillExperience());
        sendEvent(json);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        String msg = escape(event.getMessage());
        String json = String.format("{\"type\":\"chat\",\"message\":\"%s\"}", msg);
        sendEvent(json);
    }

    private void sendEvent(String json)
    {
        try
        {
            java.net.URL url = new java.net.URL(config.serverUrl() + "/event");
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json; utf-8");
            con.setDoOutput(true);
            try (java.io.OutputStream os = con.getOutputStream())
            {
                byte[] input = json.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            int code = con.getResponseCode();
            con.disconnect();
        }
        catch (Exception e)
        {
            // ignore for now
        }
    }

    private String escape(String s)
    {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
