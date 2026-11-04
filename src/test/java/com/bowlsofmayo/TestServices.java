package com.bowlsofmayo;

import com.google.gson.GsonBuilder;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;

final class TestServices
{
    private static final ScheduledExecutorService EXECUTOR = (ScheduledExecutorService) Proxy.newProxyInstance(
        ScheduledExecutorService.class.getClassLoader(), new Class<?>[]{ScheduledExecutorService.class},
        (proxy, method, arguments) -> {
            if (method.getName().equals("scheduleWithFixedDelay") || method.getName().equals("submit"))
            {
                return null;
            }
            throw new AssertionError("Unexpected background task: " + method.getName());
        });

    static ConfigManager configManager() throws Exception
    {
        Constructor<ConfigManager> constructor = ConfigManager.class.getDeclaredConstructor(
            String.class, ScheduledExecutorService.class, EventBus.class, Client.class,
            com.google.gson.Gson.class, Class.forName("net.runelite.client.config.ConfigClient"),
            Class.forName("net.runelite.client.config.ProfileManager"),
            Class.forName("net.runelite.client.account.SessionManager"));
        constructor.setAccessible(true);
        ConfigManager manager = constructor.newInstance(null, EXECUTOR, new EventBus(), null,
            new GsonBuilder().create(), null, null, null);
        Field profile = ConfigManager.class.getDeclaredField("rsProfileKey");
        profile.setAccessible(true);
        profile.set(manager, "rsprofile.test");
        Field data = ConfigManager.class.getDeclaredField("rsProfileConfigProfile");
        data.setAccessible(true);
        Constructor<?> dataConstructor = data.getType().getDeclaredConstructor(java.io.File.class);
        dataConstructor.setAccessible(true);
        Path config = Files.createTempFile("companion-config-check", ".properties");
        try
        {
            Files.writeString(config, "killcount.rsprofile.test.zulrah=127\n" +
                "killcount.rsprofile.test.vorkath=0\nkillcount.rsprofile.test.brutus=-1\n");
            data.set(manager, dataConstructor.newInstance(config.toFile()));
        }
        finally
        {
            Files.delete(config);
        }
        return manager;
    }

    static ItemManager itemManager(Client client) throws Exception
    {
        ClientThread clientThread = new ClientThread();
        Field clientField = ClientThread.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(clientThread, client);
        Constructor<ItemManager> constructor = ItemManager.class.getDeclaredConstructor(
            Client.class, ScheduledExecutorService.class, ClientThread.class, EventBus.class,
            Class.forName("net.runelite.client.game.ItemClient"), RuneLiteConfig.class);
        constructor.setAccessible(true);
        return constructor.newInstance(client, EXECUTOR, clientThread, new EventBus(), null, new RuneLiteConfig() {});
    }
}
