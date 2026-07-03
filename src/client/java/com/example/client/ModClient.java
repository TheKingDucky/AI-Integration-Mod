
package com.example.client;

import com.example.turtle.TurtleTracker;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Client helper that registers keybindings and opens the SettingsScreen.
 * Call ModClient.register() from your existing ClientInit.onInitializeClient().
 */
public final class ModClient {

    private static KeyMapping OPEN_SETTINGS_KEY;
    private static KeyMapping SEND_TURTLE_COORDS_KEY;

    // request flag used by commands to safely open GUI next tick
    private static volatile boolean pendingOpenRequested = false;

    // Register a custom KeyMapping.Category
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(
                    Identifier.fromNamespaceAndPath("ducky", "controls")
            );

    private ModClient() {}

    /**
     * Call this from ClientInit.onInitializeClient():
     *
     * com.example.client.ModClient.register();
     */
    public static void register() {

        OPEN_SETTINGS_KEY = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        "key.ducky.open_settings",
                        InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_O,
                        CATEGORY
                )
        );

        SEND_TURTLE_COORDS_KEY = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        "key.ducky.send_turtle_coords",
                        InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_K,
                        CATEGORY
                )
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (OPEN_SETTINGS_KEY == null || SEND_TURTLE_COORDS_KEY == null) return;

            if (pendingOpenRequested) {
                pendingOpenRequested = false;
                client.setScreen(new SettingsScreen(Component.literal("Mod Settings")));
            }

            while (OPEN_SETTINGS_KEY.consumeClick()) {
                client.setScreen(new SettingsScreen(Component.literal("Mod Settings")));
            }

            while (SEND_TURTLE_COORDS_KEY.consumeClick()) {
                TurtleTracker.sendTurtleCoords();

            }
        });
    }

    /**
     * Called by commands (like /ducky) to request opening settings.
     * Safe to call from command handlers.
     */
    public static void requestOpenSettings() {
        pendingOpenRequested = true;
    }

    /** small helper so other code can open the settings screen directly */
    public static void openSettings() {
        Minecraft.getInstance().setScreen(
                new SettingsScreen(Component.literal("Mod Settings"))
        );
    }
}