package com.bowlsofmayo;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.client.game.ItemManager;

final class SidebarChecks
{
    static void run() throws Exception
    {
        AtomicInteger definitions = new AtomicInteger();
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
            (proxy, method, arguments) -> {
                if (method.getName().equals("isClientThread")) return false;
                if (method.getName().equals("getItemDefinition"))
                {
                    definitions.incrementAndGet();
                    int id = (int) arguments[0];
                    return Proxy.newProxyInstance(ItemComposition.class.getClassLoader(),
                        new Class<?>[]{ItemComposition.class}, (definition, member, values) -> {
                            switch (member.getName())
                            {
                                case "getName": return "Item " + id;
                                case "getNote":
                                case "getPlaceholderTemplateId": return -1;
                                case "getHaPrice": return 10;
                                case "isTradeable": return id != 4151;
                                default: throw new AssertionError("Unexpected item access: " + member.getName());
                            }
                        });
                }
                throw new AssertionError("Unexpected client access: " + method.getName());
            });
        ItemManager itemManager = TestServices.itemManager(client);
        checkTabCapture(itemManager);
        checkTradeableView(itemManager, definitions);
        SwingUtilities.invokeAndWait(() -> checkLargeBank(itemManager));
        System.out.println("Sidebar regression checks passed (800 items, unchanged-sync reuse, debounce, tabs, permissions, tradeable filter).");
    }

    private static void checkTabCapture(ItemManager itemManager)
    {
        Item[] items = {new Item(995, 10), new Item(1000, 0), new Item(4151, 1), new Item(1001, 1)};
        ItemContainer container = (ItemContainer) Proxy.newProxyInstance(ItemContainer.class.getClassLoader(),
            new Class<?>[]{ItemContainer.class}, (proxy, method, arguments) -> {
                if (method.getName().equals("getItems")) return items;
                throw new AssertionError("Unexpected container access: " + method.getName());
            });
        BankSnapshots snapshots = new BankSnapshots();
        List<TrackerData.BankItem> bank = snapshots.readItems(itemManager, container, new int[]{2, 1});
        assert bank.size() == 3 : "Placeholders should not upload";
        assert bank.get(0).tab == 1 && bank.get(1).tab == 2 && bank.get(2).tab == 0 :
            "Placeholder slots must still count toward tab boundaries";
        assert snapshots.readItems(itemManager, container).stream().allMatch(item -> item.tab == null) :
            "Group storage has no personal bank tabs";
    }

    private static void checkTradeableView(ItemManager itemManager, AtomicInteger definitions) throws Exception
    {
        ClanTrackerPlugin plugin = new ClanTrackerPlugin();
        set(plugin, "itemManager", itemManager);
        TrackerData.Group group = group(2);
        group.members.get(0).bank.get(0).id = 995;
        group.members.get(0).bank.get(1).id = 4151;
        group.groupStorage = new TrackerData.GroupStorage();
        group.groupStorage.items = group.members.get(0).bank;
        group.groupStorage.username = "Test Player";
        Method filter = ClanTrackerPlugin.class.getDeclaredMethod("tradeableView", TrackerData.Group.class);
        filter.setAccessible(true);
        TrackerData.Group view = (TrackerData.Group) filter.invoke(plugin, group);
        assert view.members.get(0).bank.size() == 1 && view.groupStorage.items.size() == 1;
        assert view.members.get(0).bank.get(0).tab == 1 : "Filtering must preserve bank tabs";
        assert group.members.get(0).bank.size() == 2 : "Sidebar filtering must not change uploaded bank data";
        int reads = definitions.get();
        filter.invoke(plugin, group);
        assert definitions.get() == reads : "Unchanged syncs must reuse cached tradeability";
    }

    private static void checkLargeBank(ItemManager itemManager)
    {
        try
        {
            ClanPanel panel = new ClanPanel(itemManager);
            TrackerData.Group group = group(800);
            panel.update(group);
            JPanel contents = (JPanel) get(panel, "contents");
            JComboBox<?> selector = (JComboBox<?>) get(panel, "selector");
            assert tiles(contents) == 800 : "All bank items must be visible initially";
            assert tab(contents, "Tab 1 (Item 10000)") != null && tab(contents, "Tab 4 (Item 10600)") != null;
            Component first = contents.getComponent(0);
            AtomicInteger selectorChanges = new AtomicInteger();
            selector.addActionListener(event -> selectorChanges.incrementAndGet());
            for (int i = 0; i < 20; i++) panel.update(group(800));
            assert contents.getComponent(0) == first : "Unchanged polling must reuse the existing bank UI";
            assert selectorChanges.get() == 0 : "Unchanged polling must not rebuild the selector";
            tab(contents, "Tab 2 (Item 10200)").doClick(0);
            assert tiles(contents) == 200 : "A bank tab must show only its items";
            panel.update(group);
            assert Integer.valueOf(2).equals(get(panel, "selectedTab")) : "Polling must preserve selected tab";
            tab(contents, "All items").doClick(0);
            rightClick(tab(contents, "Tab 2 (Item 10200)"));
            assert tiles(contents) == 600 : "Right-click must collapse only that tab";
            panel.update(group(800));
            assert tiles(contents) == 600 : "Polling must preserve collapsed tabs";
            tab(contents, "Tab 2 (Item 10200)").doClick(0);
            assert tiles(contents) == 0 : "Left-click must not reopen a collapsed tab";
            rightClick(tab(contents, "Tab 2 (Item 10200)"));
            assert tiles(contents) == 200 : "Right-click must expand the selected tab";
            tab(contents, "All items").doClick(0);
            rightClick(tab(contents, "Tab 1 (Item 10000)"));
            selector.setSelectedItem("Other Player");
            selector.setSelectedItem("Test Player");
            assert tiles(contents) == 600 : "Collapse state must be scoped to each member";
            rightClick(tab(contents, "Tab 1 (Item 10000)"));
            assert tiles(contents) == 800;

            JTextField search = (JTextField) get(panel, "search");
            search.setText("Item 10000");
            assert tiles(contents) == 800 : "Typing must defer the expensive rebuild";
            Timer timer = (Timer) get(panel, "searchDebounce");
            assert timer.isRunning() && timer.getInitialDelay() == 200 && !timer.isRepeats();
            fire(timer);
            assert tiles(contents) == 1 : "Debounced search must show the matching item";
            search.setText("");
            fire(timer);
            assert tiles(contents) == 800;

            group.members.get(0).bankUpdatedAt++;
            group.members.get(0).bank.get(0).qty++;
            first = contents.getComponent(0);
            panel.update(group);
            assert contents.getComponent(0) != first : "A new bank snapshot must rebuild";
            group.viewer = new TrackerData.Viewer();
            group.viewer.permissions = new TrackerData.Permissions();
            group.viewer.permissions.banks = false;
            group.viewer.permissions.storage = false;
            panel.update(group);
            assert selector.getItemCount() == 0 && tiles(contents) == 0 : "Revoked permissions must clear bank data";
            group.viewer = null;
            panel.update(group);
            assert tiles(contents) == 800;
            tab(contents, "Tab 1 (Item 10000)").doClick(0);
            selector.setSelectedItem("Other Player");
            assert get(panel, "selectedTab") == null : "Changing member must reset tab selection";
            search.setText("pending");
            panel.clear("Disconnected");
            assert !timer.isRunning() && contents.getComponentCount() == 0 && selector.getItemCount() == 0;
            panel.update(group);
            search.setText("");
            fire(timer);
            assert tiles(contents) == 800 : "Cleared data must render again on reconnection";
        }
        catch (Exception error)
        {
            throw new AssertionError(error);
        }
    }

    private static TrackerData.Group group(int size)
    {
        TrackerData.Member member = new TrackerData.Member();
        member.username = "Test Player";
        member.bankUpdatedAt = 123456;
        member.bank = new ArrayList<>();
        for (int i = 0; i < size; i++)
        {
            TrackerData.BankItem item = new TrackerData.BankItem();
            item.id = 10000 + i;
            item.name = "Item " + item.id;
            item.qty = 1;
            item.tab = i / 200 + 1;
            member.bank.add(item);
        }
        TrackerData.Member other = new TrackerData.Member();
        other.username = "Other Player";
        TrackerData.Group group = new TrackerData.Group();
        group.members = Arrays.asList(member, other);
        return group;
    }

    private static void fire(Timer timer)
    {
        timer.stop();
        for (java.awt.event.ActionListener listener : timer.getActionListeners())
        {
            listener.actionPerformed(new java.awt.event.ActionEvent(timer, 0, "search"));
        }
    }

    private static void rightClick(JButton button)
    {
        button.dispatchEvent(new java.awt.event.MouseEvent(button, java.awt.event.MouseEvent.MOUSE_CLICKED,
            System.currentTimeMillis(), 0, 5, 5, 1, true, java.awt.event.MouseEvent.BUTTON3));
    }

    private static JButton tab(Container container, String label)
    {
        for (Component component : container.getComponents())
        {
            if (component instanceof JButton && label.equals(((JButton) component).getAccessibleContext().getAccessibleName()))
            {
                return (JButton) component;
            }
            if (component instanceof Container)
            {
                JButton found = tab((Container) component, label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int tiles(Container container)
    {
        int count = 0;
        for (Component component : container.getComponents())
        {
            if (component.getClass().getSimpleName().equals("ItemTile")) count++;
            if (component instanceof Container) count += tiles((Container) component);
        }
        return count;
    }

    private static Object get(Object instance, String name) throws Exception
    {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static void set(Object instance, String name, Object value) throws Exception
    {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }
}
