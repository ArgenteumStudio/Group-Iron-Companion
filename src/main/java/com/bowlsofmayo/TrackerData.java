package com.bowlsofmayo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TrackerData
{
    static final class Stat
    {
        int level;
        int xp;
    }

    static final class Position
    {
        int x;
        int y;
        int plane;
        int world;
    }

    static final class BankItem
    {
        int id;
        int qty;
        String name;
        Long price;
        Integer highAlch;
        // 1-9 for bank tabs, 0 for untabbed items, null for sources without tabs.
        Integer tab;
    }

    static final class Member
    {
        String username;
        Map<String, Stat> stats = new LinkedHashMap<>();
        Position position;
        List<BankItem> bank;
        boolean bankShared;
        long bankUpdatedAt;
        List<BankItem> groupStorage;
        long groupStorageUpdatedAt;
        long observedAt;
        Integer questPoints;
        Map<String, Integer> killCounts;
        long updatedAt;
    }

    static final class Group
    {
        List<Member> members;
        Map<Integer, String> icons;
        GroupStorage groupStorage;
        Viewer viewer;
    }

    static final class GroupStorage
    {
        List<BankItem> items;
        long updatedAt;
        String username;
    }

    static final class Viewer
    {
        Permissions permissions;
    }

    static final class Permissions
    {
        boolean banks = true;
        boolean storage = true;
    }

    static final class Icons
    {
        String username;
        Map<Integer, String> icons = new LinkedHashMap<>();
    }

    static final class Event
    {
        String id;
        String username;
        String type;
        String message;
        String boss;
        Integer kc;
        String source;
        String quest;
        String diary;
        List<BankItem> items;
    }
}
