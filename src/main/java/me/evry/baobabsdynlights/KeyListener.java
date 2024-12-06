package me.evry.baobabsdynlights;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import me.evry.baobabsdynlights.data.KeyBehaviour;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class KeyListener implements NativeKeyListener {

    private static final Object pressLock = new Object();
    private static final Object releaseLock = new Object();

    public static void init() {
        try {
            GlobalScreen.registerNativeHook();
        } catch (NativeHookException e) {
            BaobabsDynLights.LOGGER.error(e.getMessage(), e);
        }
        GlobalScreen.addNativeKeyListener(new KeyListener());
    }

    public synchronized void nativeKeyPressed(NativeKeyEvent event) {
        if (!Minecraft.getInstance().isWindowActive() || Minecraft.getInstance().gui.getChat().isChatFocused()) {
            return;
        }

        int key = event.getRawCode();

        KeyBehaviour kb = BaobabsDynLights.keyChains.get(key);
        if (kb == null) {
            return;
        }

        switch (kb.actionType()) {
            case "hold" -> {
                Thread t = BaobabsDynLights.activeActions.get(key);
                if (t == null) {
                    t = Thread.startVirtualThread(() -> {
                        while (!Thread.interrupted()) {
                            BaobabsDynLights.instance.makeActions(kb);
                        }
                    });
                    BaobabsDynLights.activeActions.put(key, t);
                }
            }
            case "single" -> {
                Thread.startVirtualThread(() -> {
                    BaobabsDynLights.instance.makeActions(kb);
                });
            }
            case "auto" -> {
                Thread t = BaobabsDynLights.activeActions.get(key);
                if (t != null) {
                    BaobabsDynLights.activeActions.remove(key).interrupt();

                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.sendSystemMessage(Component.literal(key + " deactivated"));
                    }
                } else {
                    t = Thread.startVirtualThread(() -> {
                        while (!Thread.interrupted()) {
                            BaobabsDynLights.instance.makeActions(kb);
                        }
                    });
                    BaobabsDynLights.activeActions.put(key, t);

                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.sendSystemMessage(Component.literal(key + " activated"));
                    }
                }
            }
        }
    }

    public synchronized void nativeKeyReleased(NativeKeyEvent event) {
        if (!Minecraft.getInstance().isWindowActive() || Minecraft.getInstance().gui.getChat().isChatFocused()) {
            return;
        }

        int key = event.getRawCode();

        KeyBehaviour kb = BaobabsDynLights.keyChains.get(key);
        if (kb == null || !kb.actionType().equals("hold")) {
            return;
        }

        Thread t = BaobabsDynLights.activeActions.remove(key);
        if (t != null) {
            t.interrupt();
        }
    }

    public void nativeKeyTyped(NativeKeyEvent e) {
    }
}