package com.bowlsofmayo;

public enum SidebarIcon
{
    COMPANION_SHIELD("Companion shield", -1, 1),
    GROUP_LOGO("Group logo", -1, 1),
    DOT("Simple dot", -1, 1),
    BOWL("Bowl", 1923, 1),
    COINS("Coins", 995, 10000),
    ABYSSAL_WHIP("Abyssal whip", 4151, 1),
    DRAGON_PICKAXE("Dragon pickaxe", 11920, 1),
    FIRE_CAPE("Fire cape", 6570, 1),
    TWISTED_BOW("Twisted bow", 20997, 1),
    CUSTOM_ITEM("Custom item ID", -1, 1);

    private final String label;
    private final int itemId;
    private final int quantity;

    SidebarIcon(String label, int itemId, int quantity)
    {
        this.label = label;
        this.itemId = itemId;
        this.quantity = quantity;
    }

    int itemId()
    {
        return itemId;
    }

    int quantity()
    {
        return quantity;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
