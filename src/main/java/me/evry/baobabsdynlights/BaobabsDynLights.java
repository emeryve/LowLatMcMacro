package me.evry.baobabsdynlights;

import com.mojang.blaze3d.platform.Window;
import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import me.evry.baobabsdynlights.data.KeyBehaviour;
import me.evry.baobabsdynlights.data.Range;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaobabsDynLights implements ModInitializer, ClientLifecycleEvents.ClientStarted {

    public static final String MOD_ID = "baobabsdynlights";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static BaobabsDynLights instance;

    public static Map<Integer, KeyBehaviour> keyChains = new HashMap<>();
    public static ConcurrentHashMap<Integer, Thread> activeActions = new ConcurrentHashMap<>();

    public static Random rand = new Random();
    public static Minecraft mcClient = null;
    public static Window mcWindow = null;
    public static Path mcFolder = null;

    public static ServerboundSwingPacket LKMPacket = new ServerboundSwingPacket(InteractionHand.MAIN_HAND);

    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        instance = this;
        ClientLifecycleEvents.CLIENT_STARTED.register(this);
        LOGGER.info("macro is here");
    }

    @Override
    public void onClientStarted(Minecraft client) {
        mcClient = client;
        mcWindow = mcClient.getWindow();
        mcFolder = mcClient.gameDirectory.toPath();

        applyConfig();
        createConfigWatchService();

        KeyListener.init();
    }

    public void createConfigWatchService() {
        new Thread(() -> {
            try {
                WatchService watchService = FileSystems.getDefault().newWatchService();
                mcFolder.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

                WatchKey key;
                while ((key = watchService.take()) != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.context().toString().equals("macroconfig.txt")) {
                            applyConfig();
                        }
                    }
                    key.reset();
                }
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }).start();
    }

    public void applyConfig() {
        activeActions.forEach((k, v) -> v.interrupt());
        activeActions.clear();

        keyChains.clear();

        File config = new File(mcFolder + "/macroconfig.txt");
        List<String> configs = null;

        try {
            configs = Files.readAllLines(config.toPath());
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        if (configs == null || configs.isEmpty()) {
            return;
        }

        for (String cfg : configs) {
            String[] entry = cfg.split(" ");

            int keyId = Integer.parseInt(entry[0]);
            String actionType = entry[1];

            String[] actionsString = new String[entry.length - 2];
            for (byte i = 2; i < entry.length; i++) {
                actionsString[i - 2] = entry[i];
            }

            Object[] actions = new Object[actionsString.length];
            String action;
            for (int i = 0; i < actionsString.length; i++) {
                action = actionsString[i];

                if (action.equals("r")) {
                    actions[i] = 'r';
                } else if (action.equals("l")) {
                    actions[i] = 'l';
                } else if (action.contains("c")) {
                    actions[i] = 'c';
                } else if (action.contains("-")) {
                    String[] corners = action.split("-");
                    actions[i] = new Range(Integer.parseInt(corners[0]), Integer.parseInt(corners[1]));
                } else {
                    actions[i] = Integer.parseInt(action);
                }
            }

            keyChains.put(keyId, new KeyBehaviour(actionType, actions));
        }
    }

    public void makeActions(KeyBehaviour kb) {
        for (Object action : kb.actions()) {
            switch (action) {
                case Character c -> {
                    if (mcClient.getConnection() == null || mcClient.player == null) {
                        activeActions.forEach((k, v) -> v.interrupt());
                        activeActions.clear();
                        return;
                    }

                    if (c == 'r') {
                        mcClient.getConnection().send(new ServerboundUseItemPacket(
                            InteractionHand.MAIN_HAND, 0, mcClient.player.getYRot(), mcClient.player.getXRot()));
                    } else if (c == 'l') {
                        mcClient.getConnection().send(LKMPacket);
                    } else if (c == 'c') {
                        mcClient.player.sendSystemMessage(Component.literal(kb.actionType() + " action echo"));
                    }
                }
                case Range r -> {
                    try {
                        Thread.sleep(rand.nextInt(r.start(), r.end()));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                case Integer time -> {
                    try {
                        Thread.sleep(time);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                default -> {
                }
            }
        }
    }
}