package com.bowlsofmayo;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FeatureParser
{
    private static final Pattern KILL = Pattern.compile(
        "^Your (.+?) (?:(?:kill|harvest|completion|success|subdued) )?count is:\\s*([0-9,]+)\\b",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPLETION = Pattern.compile(
        "^Your (?:completion count for|completed|subdued) (.+?) (?:count )?is:\\s*([0-9,]+)\\b",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern DIARY = Pattern.compile(
        "Congratulations! You have completed all of the (easy|medium|hard|elite) tasks in the (.+?) area",
        Pattern.CASE_INSENSITIVE);

    /** Returns e.g. "Varrock Hard" for an achievement diary tier completion, otherwise null. */
    static String parseDiary(String message)
    {
        Matcher matcher = DIARY.matcher(message);
        if (!matcher.find())
        {
            return null;
        }
        String tier = matcher.group(1).toLowerCase(Locale.ROOT);
        return matcher.group(2).trim() + " " + Character.toUpperCase(tier.charAt(0)) + tier.substring(1);
    }

    static Kill parseKill(String message)
    {
        // Prefixed completion messages first, so "completed"/"subdued" never becomes part of the boss name.
        Matcher matcher = COMPLETION.matcher(message);
        if (!matcher.find())
        {
            matcher = KILL.matcher(message);
            if (!matcher.find())
            {
                return null;
            }
        }
        if (matcher.group(1).toLowerCase(Locale.ROOT).endsWith(" lap"))
        {
            return null;
        }
        String digits = matcher.group(2).replace(",", "");
        if (digits.length() > 10)
        {
            return null;
        }
        long count = Long.parseLong(digits);
        if (count < 1 || count > Integer.MAX_VALUE)
        {
            return null;
        }
        return new Kill(matcher.group(1), (int) count);
    }

    static boolean samePlayer(String left, String right)
    {
        return left != null && right != null && normalize(left).equals(normalize(right));
    }

    private static String normalize(String value)
    {
        return value.replace('\u00a0', ' ').replace('_', ' ').trim().toLowerCase(Locale.ROOT);
    }

    static String sourceKey(String value)
    {
        String key = normalize(value).replaceAll("[^a-z0-9]", "");
        switch (key)
        {
            case "thegauntlet": return "gauntlet";
            case "thecorruptedgauntlet": return "corruptedgauntlet";
            case "theleviathan": return "leviathan";
            case "thewhisperer": return "whisperer";
            case "barrowschest": return "barrows";
            default: return key;
        }
    }

    /** Reward chests whose count message accompanies a loot drop rather than a boss kill. */
    static boolean isChestCount(String value)
    {
        return "lunarchest".equals(sourceKey(value));
    }

    static final class Kill
    {
        final String boss;
        final int kc;
        final long observedAt = System.currentTimeMillis();

        Kill(String boss, int kc)
        {
            this.boss = boss;
            this.kc = kc;
        }
    }
}
