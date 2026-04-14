package com.bowlsofmayo;

import net.runelite.client.ui.PluginPanel;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.inject.Inject;
import java.awt.Dimension;
import java.util.Map;

public class ClanPanel extends PluginPanel
{
    private final Client client;
    private final ItemManager itemManager;

    private final JTextArea inventoryArea;
    private final JTextArea equipmentArea;

    @Inject
    public ClanPanel(Client client, ItemManager itemManager)
    {
        this.client = client;
        this.itemManager = itemManager;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JLabel invLabel = new JLabel("Inventory");
        inventoryArea = new JTextArea();
        inventoryArea.setEditable(false);
        inventoryArea.setLineWrap(true);
        JScrollPane invScroll = new JScrollPane(inventoryArea);
        invScroll.setPreferredSize(new Dimension(250, 200));

        JLabel eqLabel = new JLabel("Worn Equipment");
        equipmentArea = new JTextArea();
        equipmentArea.setEditable(false);
        equipmentArea.setLineWrap(true);
        JScrollPane eqScroll = new JScrollPane(equipmentArea);
        eqScroll.setPreferredSize(new Dimension(250, 150));

        add(invLabel);
        add(invScroll);
        add(eqLabel);
        add(eqScroll);
    }

    public void updateInventoryDisplay(Map<Integer, Integer> inventory, Map<Integer, Integer> equipment)
    {
        StringBuilder invText = new StringBuilder();
        if (inventory != null) {
            inventory.forEach((id, qty) -> {
                String name = itemManager.getItemComposition(id).getName();
                invText.append(name).append(" x").append(qty).append('\n');
            });
        }
        inventoryArea.setText(invText.toString());

        StringBuilder eqText = new StringBuilder();
        if (equipment != null) {
            equipment.forEach((id, qty) -> {
                String name = itemManager.getItemComposition(id).getName();
                eqText.append(name).append(" x").append(qty).append('\n');
            });
        }
        equipmentArea.setText(eqText.toString());
    }
}
