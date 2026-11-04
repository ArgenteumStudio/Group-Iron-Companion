package com.bowlsofmayo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
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
    private boolean populatingSelector;
    // Identifies the bank view on screen so periodic syncs with unchanged data skip the rebuild.
    private String renderedKey;
    // Bank tab shown in the sidebar; null means every tab, like the in-game "all items" tab.
    private Integer selectedTab;
    private final javax.swing.Timer searchDebounce = new javax.swing.Timer(200, event -> renderBank());

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
        selector.addActionListener(event -> {
            if (!populatingSelector)
            {
                selectedTab = null;
                renderBank();
            }
        });
        searchDebounce.setRepeats(false);
        search.getDocument().addDocumentListener(new DocumentListener()
        {
            public void insertUpdate(DocumentEvent event) { searchDebounce.restart(); }
            public void removeUpdate(DocumentEvent event) { searchDebounce.restart(); }
            public void changedUpdate(DocumentEvent event) { searchDebounce.restart(); }
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
        List<String> options = new java.util.ArrayList<>();
        if (banksAllowed)
        {
            for (TrackerData.Member member : members)
            {
                options.add(member.username);
            }
        }
        if (storageAllowed)
        {
            options.add(GROUP_STORAGE);
        }
        if (!options.equals(selectorItems()))
        {
            populatingSelector = true;
            try
            {
                selector.removeAllItems();
                for (String option : options)
                {
                    selector.addItem(option);
                }
                int index = options.indexOf(selected);
                if (index >= 0)
                {
                    selector.setSelectedIndex(index);
                }
            }
            finally
            {
                populatingSelector = false;
            }
        }
        renderBank();
    }

    private List<String> selectorItems()
    {
        List<String> items = new java.util.ArrayList<>();
        for (int i = 0; i < selector.getItemCount(); i++)
        {
            items.add(selector.getItemAt(i));
        }
        return items;
    }

    private String viewKey(String selected, String query)
    {
        StringBuilder key = new StringBuilder().append(banksAllowed).append('|').append(storageAllowed)
            .append('|').append(selected).append('|').append(query).append('|').append(selectedTab).append('|');
        if (GROUP_STORAGE.equals(selected))
        {
            return groupStorage == null || groupStorage.items == null ? key.append("none").toString()
                : key.append(groupStorage.updatedAt).append('|').append(groupStorage.username)
                    .append('|').append(groupStorage.items.size()).toString();
        }
        for (TrackerData.Member member : members)
        {
            if (member.username.equals(selected))
            {
                return member.bank == null ? key.append("none").toString()
                    : key.append(member.bankUpdatedAt).append('|').append(member.bank.size()).toString();
            }
        }
        return key.append("missing").toString();
    }

    void clear(String message)
    {
        members = Collections.emptyList();
        groupStorage = null;
        renderedKey = null;
        selectedTab = null;
        searchDebounce.stop();
        populatingSelector = true;
        try
        {
            selector.removeAllItems();
        }
        finally
        {
            populatingSelector = false;
        }
        contents.removeAll();
        note.setText("Group data unavailable.");
        note.setToolTipText(null);
        setStatus(message);
        contents.revalidate();
        contents.repaint();
    }

    private void renderBank()
    {
        String key = viewKey((String) selector.getSelectedItem(), search.getText().trim().toLowerCase(Locale.ROOT));
        if (key.equals(renderedKey))
        {
            return;
        }
        renderedKey = key;
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
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        java.util.TreeMap<Integer, TrackerData.BankItem> tabIcons = new java.util.TreeMap<>();
        for (TrackerData.BankItem item : items)
        {
            if (item.tab != null && item.tab > 0)
            {
                tabIcons.putIfAbsent(item.tab, item);
            }
        }
        if (selectedTab != null && !tabIcons.containsKey(selectedTab))
        {
            selectedTab = null;
        }
        if (!tabIcons.isEmpty())
        {
            contents.add(tabStrip(tabIcons));
        }
        List<TrackerData.BankItem> matches = new java.util.ArrayList<>();
        for (TrackerData.BankItem item : items)
        {
            if (item.name.toLowerCase(Locale.ROOT).contains(query) &&
                (selectedTab == null || selectedTab.equals(item.tab)))
            {
                matches.add(item);
            }
        }
        if (matches.isEmpty())
        {
            contents.add(new JLabel(items.isEmpty() ? (shared ? "Empty group storage." : "Empty bank.") : "No matching items."));
            return;
        }
        int start = 0;
        while (start < matches.size())
        {
            Integer tab = matches.get(start).tab;
            int end = start;
            while (end < matches.size() && java.util.Objects.equals(matches.get(end).tab, tab))
            {
                end++;
            }
            if (tab != null && selectedTab == null)
            {
                contents.add(tabHeader(tab));
            }
            contents.add(itemGrid(matches.subList(start, end)));
            start = end;
        }
    }

    // Mirrors the in-game tab bar: an "all items" tab, then each numbered tab drawn with its first item.
    private JComponent tabStrip(java.util.SortedMap<Integer, TrackerData.BankItem> tabIcons)
    {
        JPanel strip = new JPanel(new GridLayout(0, 5, 2, 2));
        strip.setOpaque(false);
        strip.setAlignmentX(LEFT_ALIGNMENT);
        strip.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        strip.add(tabButton(null, "All items", null));
        for (java.util.Map.Entry<Integer, TrackerData.BankItem> entry : tabIcons.entrySet())
        {
            strip.add(tabButton(entry.getKey(), "Tab " + entry.getKey() + " (" + entry.getValue().name + ")", entry.getValue()));
        }
        while (strip.getComponentCount() % 5 != 0)
        {
            JPanel empty = new JPanel();
            empty.setOpaque(false);
            strip.add(empty);
        }
        strip.setMaximumSize(new Dimension(Integer.MAX_VALUE, (strip.getComponentCount() / 5) * 40));
        return strip;
    }

    private JComponent tabButton(Integer tab, String label, TrackerData.BankItem icon)
    {
        JButton button = new JButton();
        button.setToolTipText(label);
        button.getAccessibleContext().setAccessibleName(label);
        button.setFocusPainted(false);
        button.setMargin(new java.awt.Insets(0, 0, 0, 0));
        button.setPreferredSize(new Dimension(40, 38));
        boolean active = java.util.Objects.equals(tab, selectedTab);
        button.setBackground(active ? new Color(92, 77, 56) : new Color(62, 53, 41));
        button.setBorder(BorderFactory.createMatteBorder(1, 1, active ? 3 : 1, 1,
            active ? new Color(255, 152, 31) : new Color(115, 96, 74)));
        AsyncBufferedImage image = icon == null ? null : itemManager.getImage(icon.id);
        if (image != null)
        {
            image.addTo(button);
        }
        else
        {
            button.setText(icon == null ? "\u221e" : String.valueOf(tab));
            button.setForeground(new Color(255, 152, 31));
        }
        button.addActionListener(event -> {
            selectedTab = tab;
            renderBank();
        });
        return button;
    }

    private static JComponent tabHeader(int tab)
    {
        JLabel header = new JLabel(tab == 0 ? "Untabbed" : "Tab " + tab);
        header.setForeground(new Color(255, 152, 31));
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(105, 88, 63)),
            BorderFactory.createEmptyBorder(6, 2, 2, 2)));
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, header.getPreferredSize().height));
        return header;
    }

    private JComponent itemGrid(List<TrackerData.BankItem> items)
    {
        JPanel grid = new JPanel(new GridLayout(0, 4, 4, 4));
        grid.setOpaque(false);
        grid.setAlignmentX(LEFT_ALIGNMENT);
        for (TrackerData.BankItem item : items)
        {
            grid.add(itemTile(item));
        }
        // Pad the final row so GridLayout never stretches a few items across the bank.
        while (grid.getComponentCount() % 4 != 0)
        {
            JPanel empty = new JPanel();
            empty.setOpaque(false);
            grid.add(empty);
        }
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, ((items.size() + 3) / 4) * 50));
        return grid;
    }

    private JComponent itemTile(TrackerData.BankItem item)
    {
        return new ItemTile(item, itemManager.getImage(item.id));
    }

    // One painted component per item keeps large banks cheap to lay out compared with nested panels and labels.
    private static final class ItemTile extends JComponent
    {
        private static final Color BACKGROUND = new Color(57, 48, 36);
        private static final Color BORDER = new Color(105, 88, 63);
        private final AsyncBufferedImage image;
        private final String text;
        private final Color textColor;

        ItemTile(TrackerData.BankItem item, AsyncBufferedImage image)
        {
            this.image = image;
            text = item.qty > 1 ? quantity(item.qty) : "";
            textColor = item.qty >= 10000000 ? Color.GREEN : item.qty >= 100000 ? Color.WHITE : Color.YELLOW;
            setPreferredSize(new Dimension(42, 46));
            setToolTipText(image == null ? item.name + " - image unavailable" : item.name + " x " + String.format("%,d", item.qty));
            if (image != null)
            {
                image.onLoaded(this::repaint);
            }
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            int width = getWidth();
            int height = getHeight();
            g.setColor(BACKGROUND);
            g.fillRect(0, 0, width, height);
            g.setColor(BORDER);
            g.drawRect(0, 0, width - 1, height - 1);
            FontMetrics metrics = g.getFontMetrics();
            if (image != null)
            {
                g.drawImage(image, (width - image.getWidth()) / 2, (height - image.getHeight()) / 2, null);
            }
            else
            {
                g.setColor(Color.WHITE);
                g.drawString("?", (width - metrics.stringWidth("?")) / 2, (height + metrics.getAscent()) / 2);
            }
            if (!text.isEmpty())
            {
                g.setColor(Color.BLACK);
                g.drawString(text, 3, metrics.getAscent() + 2);
                g.setColor(textColor);
                g.drawString(text, 2, metrics.getAscent() + 1);
            }
        }
    }

    static String quantity(int qty)
    {
        if (qty >= 10000000) return (qty / 1000000) + "M";
        if (qty >= 100000) return (qty / 1000) + "K";
        return Integer.toString(qty);
    }
}
