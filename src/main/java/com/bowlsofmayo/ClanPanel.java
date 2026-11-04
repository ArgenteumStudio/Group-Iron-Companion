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
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
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
    private final JPanel trackerCard = new JPanel(new BorderLayout(0, 5));
    private final JLabel balanceLabel = new JLabel("BALANCE");
    private final JLabel deathCoffer = new JLabel("Open Death's Coffer to sync");
    private final JLabel deathCofferThreshold = new JLabel();
    private final JButton bankToggle = new JButton("Banks and group storage  \u25bc");
    private final JComboBox<String> selector = new JComboBox<>();
    private final JTextField search = new JTextField();
    private final JLabel searchLabel = new JLabel("Search bank");
    private final JLabel note = new JLabel();
    private final JPanel contents = new JPanel();
    private JScrollPane bankScroll;
    private boolean bankExpanded = true;
    private List<TrackerData.Member> members = Collections.emptyList();
    private TrackerData.GroupStorage groupStorage;
    private boolean banksAllowed = true;
    private boolean storageAllowed = true;
    private boolean populatingSelector;
    private String renderedKey;
    private Integer selectedTab;
    private final java.util.Map<String, java.util.Set<Integer>> collapsedTabs = new java.util.HashMap<>();
    private final javax.swing.Timer searchDebounce = new javax.swing.Timer(200, event -> renderBank());

    public ClanPanel(ItemManager itemManager)
    {
        this.itemManager = itemManager;
        setLayout(new BorderLayout(0, 8));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        status.setFont(new JLabel().getFont());
        status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        status.setOpaque(false);
        status.setEditable(false);
        status.setFocusable(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        privacy.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        privacy.setFont(status.getFont());
        privacy.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        privacy.setOpaque(false);
        privacy.setEditable(false);
        privacy.setFocusable(false);
        privacy.setLineWrap(true);
        privacy.setWrapStyleWord(true);
        privacy.setSize(PANEL_WIDTH - 2 * BORDER_OFFSET, Short.MAX_VALUE);
        privacy.setMaximumSize(privacy.getPreferredSize());
        JLabel trackerTitle = new JLabel("DEATH'S COFFER");
        trackerTitle.setForeground(ColorScheme.BRAND_ORANGE);
        trackerTitle.setFont(trackerTitle.getFont().deriveFont(java.awt.Font.BOLD, 10f));
        balanceLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        balanceLabel.setFont(balanceLabel.getFont().deriveFont(java.awt.Font.BOLD, 10f));
        deathCoffer.setForeground(Color.WHITE);
        deathCoffer.setFont(deathCoffer.getFont().deriveFont(java.awt.Font.BOLD, 15f));
        deathCofferThreshold.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        deathCofferThreshold.setFont(deathCofferThreshold.getFont().deriveFont(java.awt.Font.PLAIN, 10f));
        trackerCard.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        trackerCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, ColorScheme.BRAND_ORANGE),
            BorderFactory.createEmptyBorder(7, 9, 7, 8)));
        JPanel trackerDetails = new JPanel();
        trackerDetails.setLayout(new BoxLayout(trackerDetails, BoxLayout.Y_AXIS));
        trackerDetails.setOpaque(false);
        trackerDetails.add(balanceLabel);
        trackerDetails.add(deathCoffer);
        trackerDetails.add(javax.swing.Box.createVerticalStrut(3));
        trackerDetails.add(deathCofferThreshold);
        trackerCard.add(trackerTitle, BorderLayout.NORTH);
        trackerCard.add(trackerDetails, BorderLayout.CENTER);
        trackerCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, trackerCard.getPreferredSize().height));
        bankToggle.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.LIGHT_GRAY_COLOR),
            BorderFactory.createEmptyBorder(8, 2, 6, 2)));
        bankToggle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        bankToggle.setFont(bankToggle.getFont().deriveFont(java.awt.Font.BOLD));
        bankToggle.setContentAreaFilled(false);
        bankToggle.setFocusPainted(false);
        bankToggle.setHorizontalAlignment(SwingConstants.LEFT);
        header.add(status);
        header.add(privacy);
        header.add(trackerCard);
        header.add(javax.swing.Box.createVerticalStrut(8));
        header.add(bankToggle);
        header.add(javax.swing.Box.createVerticalStrut(6));
        header.add(selector);
        searchLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        searchLabel.setFont(searchLabel.getFont().deriveFont(java.awt.Font.PLAIN, 11f));
        search.setToolTipText("Search bank items by name");
        search.setMaximumSize(new Dimension(Integer.MAX_VALUE, search.getPreferredSize().height));
        header.add(searchLabel);
        header.add(search);
        note.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        note.setFont(note.getFont().deriveFont(java.awt.Font.PLAIN, 10f));
        header.add(note);
        setStatus(status.getText());
        for (java.awt.Component component : header.getComponents())
        {
            ((javax.swing.JComponent) component).setAlignmentX(LEFT_ALIGNMENT);
        }
        add(header, BorderLayout.NORTH);
        contents.setLayout(new BoxLayout(contents, BoxLayout.Y_AXIS));
        contents.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bankScroll = new JScrollPane(contents);
        bankScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(bankScroll, BorderLayout.CENTER);
        bankToggle.setAlignmentX(LEFT_ALIGNMENT);
        bankToggle.setHorizontalAlignment(SwingConstants.LEFT);
        bankToggle.addActionListener(event -> setBankExpanded(!bankExpanded));
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

    void setDeathCofferValue(long value)
    {
        deathCoffer.setText(String.format("%,d gp", value));
        deathCoffer.setToolTipText("Last observed balance. Open Death's Coffer to refresh.");
    }

    void clearDeathCofferValue()
    {
        deathCoffer.setText("Open Death's Coffer to sync");
        deathCoffer.setToolTipText("No saved balance for this character. Open Death's Coffer to sync.");
    }

    void setDeathCofferThreshold(int threshold)
    {
        deathCofferThreshold.setText(String.format("Low alert at or below %,d gp", threshold));
    }

    void setDeathCofferVisible(boolean visible)
    {
        deathCoffer.setVisible(visible);
        revalidate();
        repaint();
    }

    private void setBankExpanded(boolean expanded)
    {
        bankExpanded = expanded;
        String arrow = expanded ? "\u25bc" : "\u25b6";
        bankToggle.setText("Banks and group storage  " + arrow);
        selector.setVisible(expanded);
        searchLabel.setVisible(expanded);
        search.setVisible(expanded);
        note.setVisible(expanded);
        bankScroll.setVisible(expanded);
        bankToggle.setToolTipText(expanded ? "Hide saved bank snapshots" : "Show saved bank snapshots");
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
                else
                {
                    selectedTab = null;
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
            .append('|').append(selected).append('|').append(query).append('|').append(selectedTab)
            .append('|').append(collapsedTabs.get(selected)).append('|');
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
        collapsedTabs.clear();
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
        String selected = (String) selector.getSelectedItem();
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        if (viewKey(selected, query).equals(renderedKey))
        {
            return;
        }
        contents.removeAll();
        note.setText(!banksAllowed && !storageAllowed ? "Bank viewing is restricted." : "No group data yet.");
        note.setToolTipText(null);
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
        renderedKey = viewKey(selected, query);
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
            if (tab == null || !isTabCollapsed(tab))
            {
                contents.add(itemGrid(matches.subList(start, end)));
            }
            start = end;
        }
    }

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
        if (tab != null)
        {
            button.setToolTipText(label + " - right-click to " + (isTabCollapsed(tab) ? "expand" : "collapse"));
            button.addMouseListener(new java.awt.event.MouseAdapter()
            {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent event)
                {
                    if (SwingUtilities.isRightMouseButton(event))
                    {
                        toggleTab(tab);
                    }
                }
            });
        }
        button.setFocusPainted(false);
        button.setMargin(new java.awt.Insets(0, 0, 0, 0));
        button.setPreferredSize(new Dimension(40, 38));
        boolean active = java.util.Objects.equals(tab, selectedTab);
        button.setBackground(active ? new Color(92, 77, 56) : new Color(62, 53, 41));
        button.setBorder(BorderFactory.createMatteBorder(1, 1, active ? 3 : 1, 1,
            active ? ColorScheme.BRAND_ORANGE : new Color(115, 96, 74)));
        AsyncBufferedImage image = icon == null ? null : itemManager.getImage(icon.id);
        if (image != null)
        {
            image.addTo(button);
        }
        else
        {
            button.setText(icon == null ? "\u221e" : String.valueOf(tab));
            button.setForeground(ColorScheme.BRAND_ORANGE);
        }
        button.addActionListener(event -> {
            selectedTab = tab;
            renderBank();
        });
        if (tab != null && isTabCollapsed(tab))
        {
            button.setBorder(BorderFactory.createDashedBorder(ColorScheme.BRAND_ORANGE));
        }
        return button;
    }

    private boolean isTabCollapsed(int tab)
    {
        java.util.Set<Integer> collapsed = collapsedTabs.get((String) selector.getSelectedItem());
        return collapsed != null && collapsed.contains(tab);
    }

    private void toggleTab(int tab)
    {
        String owner = (String) selector.getSelectedItem();
        java.util.Set<Integer> collapsed = collapsedTabs.computeIfAbsent(owner, key -> new java.util.TreeSet<>());
        if (!collapsed.remove(tab))
        {
            collapsed.add(tab);
        }
        renderBank();
    }

    private JComponent tabHeader(int tab)
    {
        JLabel header = new JLabel((isTabCollapsed(tab) ? "\u25b6 " : "\u25bc ") +
            (tab == 0 ? "Untabbed" : "Tab " + tab));
        header.setToolTipText("Right-click to " + (isTabCollapsed(tab) ? "expand" : "collapse"));
        header.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event)
            {
                if (SwingUtilities.isRightMouseButton(event))
                {
                    toggleTab(tab);
                }
            }
        });
        header.setForeground(ColorScheme.BRAND_ORANGE);
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
            grid.add(new ItemTile(item, itemManager.getImage(item.id)));
        }
        // Keep the final row from stretching a few items across the whole bank.
        while (grid.getComponentCount() % 4 != 0)
        {
            JPanel empty = new JPanel();
            empty.setOpaque(false);
            grid.add(empty);
        }
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, ((items.size() + 3) / 4) * 50));
        return grid;
    }

    // One painted component per item avoids nested labels/panels for large banks.
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
