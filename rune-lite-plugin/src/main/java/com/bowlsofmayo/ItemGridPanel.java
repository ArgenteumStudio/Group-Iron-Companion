package com.bowlsofmayo;

import net.runelite.client.ui.PluginPanel;
import net.runelite.client.game.ItemManager;
import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;

public class ItemGridPanel extends PluginPanel
{
    private final ItemManager itemManager;
    private final JPanel grid;

    @Inject
    public ItemGridPanel(ItemManager itemManager)
    {
        this.itemManager = itemManager;

        setLayout(new BorderLayout());
        grid = new JPanel(new GridLayout(0, 4, 6, 6));
        grid.setBackground(new Color(52, 44, 31));
        JScrollPane sp = new JScrollPane(grid);
        sp.setBorder(null);
        add(sp, BorderLayout.CENTER);
    }

    public void updateInventoryDisplay(Map<Integer, Integer> inventory, Map<Integer, Integer> equipment)
    {
        SwingUtilities.invokeLater(() -> {
            grid.removeAll();

            if (inventory != null) {
                for (Map.Entry<Integer, Integer> e : inventory.entrySet()) {
                    int id = e.getKey();
                    int qty = e.getValue();
                    grid.add(makeTile(id, qty));
                }
            }

            // show equipment after inventory
            if (equipment != null && equipment.size() > 0) {
                // separator
                JLabel sep = new JLabel("Worn Equipment");
                sep.setForeground(Color.WHITE);
                sep.setHorizontalAlignment(SwingConstants.CENTER);
                grid.add(sep);
                for (Map.Entry<Integer, Integer> e : equipment.entrySet()) {
                    int id = e.getKey();
                    int qty = e.getValue();
                    grid.add(makeTile(id, qty));
                }
            }

            grid.revalidate();
            grid.repaint();
        });
    }

    private JComponent makeTile(int itemId, int qty)
    {
        JPanel tile = new JPanel(new BorderLayout());
        tile.setBackground(new Color(38, 33, 26));
        tile.setBorder(BorderFactory.createLineBorder(new Color(80,70,55)));

        BufferedImage img = itemManager.getImage(itemId);
        JLabel icon = new JLabel();
        if (img != null) icon.setIcon(new ImageIcon(img));
        else icon.setText("?");
        icon.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel qtyLabel = new JLabel(String.valueOf(qty));
        qtyLabel.setForeground(Color.WHITE);
        qtyLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        qtyLabel.setBorder(BorderFactory.createEmptyBorder(2,4,2,4));

        tile.add(icon, BorderLayout.CENTER);
        tile.add(qtyLabel, BorderLayout.SOUTH);
        tile.setPreferredSize(new Dimension(64,64));
        return tile;
    }
}
