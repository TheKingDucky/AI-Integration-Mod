
package com.example.client;


import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;


public final class ModClient {

    private static KeyMapping OPEN_SETTINGS_KEY;

    private static volatile boolean pendingOpenRequested = false;


    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(
                    Identifier.fromNamespaceAndPath("ducky", "controls")
            );

    private ModClient() {}


    public static void register() {

        OPEN_SETTINGS_KEY = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        "key.ducky.open_settings",
                        InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_O,
                        CATEGORY
                )
        );



        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (OPEN_SETTINGS_KEY == null) return;

            if (pendingOpenRequested) {
                pendingOpenRequested = false;
                client.setScreen(new SettingsScreen(Component.literal("Mod Settings")));
            }

            while (OPEN_SETTINGS_KEY.consumeClick()) {
                client.setScreen(new SettingsScreen(Component.literal("Mod Settings")));
            }

        });
    }


    public static void requestOpenSettings() {
        pendingOpenRequested = true;
    }

    public static void openSettings() {
        Minecraft.getInstance().setScreen(
                new SettingsScreen(Component.literal("Mod Settings"))
        );
    }
}