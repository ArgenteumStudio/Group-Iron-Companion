package com.bowlsofmayo;

import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import net.runelite.api.Perspective;

import javax.inject.Inject;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.Point;
import java.util.Map;
import java.util.HashMap;

public class PositionOverlay extends Overlay
{
    private final Client client;
    private final ClanTrackerPlugin plugin;
    // parsed positions: username -> {x,y,plane}
    private final Map<String, int[]> positions = new HashMap<>();

    @Inject
    public PositionOverlay(Client client, ClanTrackerPlugin plugin)
    {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.MED);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    public void updatePositions(String json)
    {
        positions.clear();
        try {
            org.json.JSONObject obj = new org.json.JSONObject(json);
            for (String key : obj.keySet()) {
                org.json.JSONObject p = obj.getJSONObject(key);
                int x = p.getInt("x");
                int y = p.getInt("y");
                int plane = p.optInt("plane", 0);
                positions.put(key, new int[]{x,y,plane});
            }
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public java.awt.Dimension render(Graphics2D g)
    {
        if (client.getLocalPlayer() == null) return null;
        WorldPoint localWp = client.getLocalPlayer().getWorldLocation();
        for (Map.Entry<String,int[]> e : positions.entrySet()) {
            String name = e.getKey();
            int[] v = e.getValue();
            WorldPoint wp = new WorldPoint(v[0], v[1], v[2]);
            LocalPoint lp = LocalPoint.fromWorld(client, wp);
            if (lp == null) continue;
            Point canvasPoint = Perspective.localToCanvas(client, lp, client.getPlane());
            if (canvasPoint == null) continue;
            OverlayUtil.renderTextLocation(g, new java.awt.Point(canvasPoint.x, canvasPoint.y), name, Color.CYAN);
            g.setColor(new Color(0, 200, 255, 200));
            g.fillOval(canvasPoint.x-4, canvasPoint.y-4, 8, 8);
        }
        return null;
    }
}
