package com.bowlsofmayo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.OverlayLayout;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

public class ClanPanel extends PluginPanel
{
    private static final String GROUP_STORAGE = "Group storage";
    private final ItemManager itemManager;
    private final JTextArea status = new JTextArea("Configure bridge URL and member token");
    private final JTextArea privacy = new JTextArea("Connection is opt-in. Uploads go to " +
        "your configured server operator, who can read, store and share them. " +
        "Alerts may also go to Discord. Disabling does not delete past data.");
    private final JComboBox<String> selector = new JComboBox<>();
    private final JTextField search = new JTextField();
    private final JLabel note = new JLabel();
    private final JPanel contents = new JPanel();
    private List<TrackerData.Member> members = Collections.emptyList();
    private TrackerData.GroupStorage groupStorage;
    private boolean banksAllowed = true;
    private boolean storageAllowed = true;

    public ClanPanel(ItemManager itemManager)
    {
        this.itemManager = itemManager;
        setLayout(new BorderLayout(0, 8));
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        status.setFont(new JLabel().getFont());
        status.setForeground(new JLabel().getForeground());
        status.setOpaque(false);
        status.setEditable(false);
        status.setFocusable(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        privacy.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        privacy.setFont(status.getFont());
        privacy.setForeground(status.getForeground());
        privacy.setOpaque(false);
        privacy.setEditable(false);
        privacy.setFocusable(false);
        privacy.setLineWrap(true);
        privacy.setWrapStyleWord(true);
        privacy.setSize(PANEL_WIDTH - 2 * BORDER_OFFSET, Short.MAX_VALUE);
        privacy.setMaximumSize(privacy.getPreferredSize());
        header.add(status);
        header.add(privacy);
        header.add(selector);
        search.setToolTipText("Search bank items by name");
        header.add(new JLabel("Search bank"));
        header.add(search);
        header.add(note);
        setStatus(status.getText());
        for (java.awt.Component component : header.getComponents())
        {
            ((javax.swing.JComponent) component).setAlignmentX(LEFT_ALIGNMENT);
        }
        add(header, BorderLayout.NORTH);
        contents.setLayout(new BoxLayout(contents, BoxLayout.Y_AXIS));
        contents.setBackground(new Color(70, 60, 45));
        JScrollPane scroll = new JScrollPane(contents);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
        selector.addActionListener(event -> renderBank());
        search.getDocument().addDocumentListener(new DocumentListener()
        {
            public void insertUpdate(DocumentEvent event) { renderBank(); }
            public void removeUpdate(DocumentEvent event) { renderBank(); }
            public void changedUpdate(DocumentEvent event) { renderBank(); }
        });
    }

    void setConnectionEnabled(boolean enabled)
    {
        privacy.setVisible(!enabled);
        revalidate();
        repaint();
    }

    void setStatus(String message)
    {
        status.setText(message);
        status.setToolTipText(message.isEmpty() ? null : message);
        status.setVisible(!message.isEmpty());
        status.setSize(PANEL_WIDTH - 2 * BORDER_OFFSET, Short.MAX_VALUE);
        status.setMaximumSize(status.getPreferredSize());
        revalidate();
    }

    void update(TrackerData.Group group)
    {
        String selected = (String) selector.getSelectedItem();
        this.members = group.members == null ? Collections.emptyList() : group.members;
        this.groupStorage = group.groupStorage;
        TrackerData.Permissions permissions = group.viewer == null ? null : group.viewer.permissions;
        banksAllowed = permissions == null || permissions.banks;
        storageAllowed = permissions == null || permissions.storage;
        selector.removeAllItems();
        if (banksAllowed)
        {
            for (TrackerData.Member member : members)
            {
                selector.addItem(member.username);
            }
        }
        if (storageAllowed)
        {
            selector.addItem(GROUP_STORAGE);
        }
        for (int i = 0; i < selector.getItemCount(); i++)
        {
            if (selector.getItemAt(i).equals(selected))
            {
                selector.setSelectedIndex(i);
                break;
            }
        }
        renderBank();
    }

    void clear(String message)
    {
        members = Collections.emptyList();
        groupStorage = null;
        selector.removeAllItems();
        contents.removeAll();
        note.setText("Group data unavailable.");
        note.setToolTipText(null);
        setStatus(message);
        contents.revalidate();
        contents.repaint();
    }

    private void renderBank()
    {
        contents.removeAll();
        note.setText(!banksAllowed && !storageAllowed ? "Bank viewing is restricted." : "No group data yet.");
        note.setToolTipText(null);
        String selected = (String) selector.getSelectedItem();
        if (storageAllowed && GROUP_STORAGE.equals(selected))
        {
            if (groupStorage == null || groupStorage.items == null)
            {
                note.setText("Group storage not synced yet.");
                contents.add(new JLabel("<html>Enable Share bank and open<br>Group Ironman storage.</html>"));
            }
            else
            {
                renderItems(groupStorage.items, groupStorage.updatedAt, true, groupStorage.username);
            }
        }
        else if (banksAllowed)
        {
            for (TrackerData.Member member : members)
            {
                if (!member.username.equals(selected))
                {
                    continue;
                }
                if (member.bank == null)
                {
                    note.setText("No shared bank snapshot.");
                    contents.add(new JLabel("Enable sharing and open your bank."));
                }
                else
                {
                    renderItems(member.bank, member.bankUpdatedAt, false, null);
                }
                break;
            }
        }
        contents.revalidate();
        contents.repaint();
    }

    private void renderItems(List<TrackerData.BankItem> items, long updatedAt, boolean shared, String publisher)
    {
        String timestamp = DateTimeFormatter.ofPattern("MMM d HH:mm:ss")
            .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(updatedAt));
        note.setText((shared ? "Storage" : "Bank") + " last seen: " + timestamp);
        note.setToolTipText("Saved snapshot, not live access. Items cannot be moved." +
            (shared && publisher != null ? " Synced by " + publisher + "." : ""));
        if (shared && publisher != null)
        {
            contents.add(new JLabel("Synced by " + publisher));
        }
        JPanel grid = new JPanel(new GridLayout(0, 4, 4, 4));
        grid.setOpaque(false);
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        int count = 0;
        for (TrackerData.BankItem item : items)
        {
            if (item.name.toLowerCase(Locale.ROOT).contains(query))
            {
                grid.add(itemTile(item));
                count++;
            }
        }
        // Pad the final row so GridLayout never stretches a few items across the bank.
        if (count > 0)
        {
            while (grid.getComponentCount() % 4 != 0)
            {
                JPanel empty = new JPanel();
                empty.setOpaque(false);
                grid.add(empty);
            }
            grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, ((count + 3) / 4) * 50));
            contents.add(grid);
        }
        else
        {
            contents.add(new JLabel(items.isEmpty() ? (shared ? "Empty group storage." : "Empty bank.") : "No matching items."));
        }
    }

    private JPanel itemTile(TrackerData.BankItem item)
    {
        JPanel tile = new JPanel();
        tile.setLayout(new OverlayLayout(tile));
        tile.setBackground(new Color(57, 48, 36));
        tile.setBorder(BorderFactory.createLineBorder(new Color(105, 88, 63)));
        tile.setPreferredSize(new Dimension(42, 46));
        tile.setToolTipText(item.name + " x " + String.format("%,d", item.qty));
        JLabel quantity = new JLabel(item.qty > 1 ? quantity(item.qty) : "");
        quantity.setForeground(item.qty >= 10000000 ? Color.GREEN : item.qty >= 100000 ? Color.WHITE : Color.YELLOW);
        quantity.setAlignmentX(0.5f);
        quantity.setAlignmentY(0.5f);
        quantity.setHorizontalAlignment(SwingConstants.LEFT);
        quantity.setVerticalAlignment(SwingConstants.TOP);
        quantity.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        JLabel icon = new JLabel();
        icon.setAlignmentX(0.5f);
        icon.setAlignmentY(0.5f);
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        icon.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        AsyncBufferedImage image = itemManager.getImage(item.id);
        if (image != null)
        {
            image.addTo(icon);
        }
        else
        {
            icon.setText("?");
            tile.setToolTipText(item.name + " - image unavailable");
        }
        tile.add(quantity);
        tile.add(icon);
        return tile;
    }

    static String quantity(int qty)
    {
        if (qty >= 10000000) return (qty / 1000000) + "M";
        if (qty >= 100000) return (qty / 1000) + "K";
        return Integer.toString(qty);
    }
}
