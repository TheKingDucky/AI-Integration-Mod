package com.example.general;

import com.example.ConfigClass;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Simple HUD timer that triggers when the chat shows "Warping...".
 *
 * Register once from client init:
 *   com.example.general.CooldownWarpDetector.register();
 */
public final class CooldownWarpDetector {
    private CooldownWarpDetector() {}



    // timing constants (seconds)
    private static final double DELAY_SECONDS = 0.5;       // <--- delay before the timer appears
    private static final double THRESHOLD_SECONDS = 1.5;   // when timer switches to static text
    private static final double STATIC_DISPLAY_SECONDS = 5.0; // how long static text remains

    // state
    private static volatile boolean pending = false;      // waiting for delay to expire
    private static volatile long pendingStartNano = 0L;   // when pending started

    private static volatile boolean active = false;       // whether a notification is active (timer visible)
    private static volatile boolean showingStatic = false; // whether currently showing static text
    private static volatile long startNano = 0L;          // when visible timer started
    private static volatile long staticStartNano = 0L;    // when static text started

    // static text to show after threshold
    private static final String STATIC_TEXT = "Warp Ready!";

    public static void register() {
        // listen for game messages (the in-game overlay/chat messages)
        ClientReceiveMessageEvents.GAME.register((Component message, boolean overlay) -> {
            if (!ConfigClass.INSTANCE.warpCooldownEnabled || message == null) return;

            String raw = message.getString();
            if (raw == null) return;

            // trigger on exact substring "Warping..."
            if (raw.contains("Warping...")) {
                // start or restart the pending/notification sequence
                System.out.println("[CooldownWarpDetector] WARP DETECTED");
                pending = true;
                pendingStartNano = System.nanoTime();

                // reset visible state so the effect restarts after the delay
                active = false;
                showingStatic = false;
                startNano = 0L;
                staticStartNano = 0L;
            }
        });

        // HUD render: show timer and static text while active (or handle pending delay)
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("ducky-mod", "cooldown-warp-detector"),
                (graphics, tickCounter) -> {
            if (!ConfigClass.INSTANCE.warpCooldownEnabled) return;

            Minecraft client = Minecraft.getInstance();
            if (client == null) return;

            Font font = client.font;
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();

            // move lower so it's more visible (center-ish)
            int x = screenWidth / 2;
            int y = screenHeight / 4; // try 1/3 down from top

            long now = System.nanoTime();

            // If we are pending, check whether the delay elapsed; if so, start the visible timer
            if (pending && !active) {
                double pendingElapsed = (now - pendingStartNano) / 1_000_000_000.0;
                if (pendingElapsed >= DELAY_SECONDS) {
                    // start visible timer now
                    active = true;
                    showingStatic = false;
                    startNano = now;
                    // clear pending state
                    pending = false;
                    pendingStartNano = 0L;
                    System.out.println("[CooldownWarpDetector] pending expired — starting visible timer at " + startNano);
                } else {
                    // not yet reached delay → draw nothing
                    return;
                }
            }

            // If not active, nothing to draw
            if (!active) return;

            if (!showingStatic) {
                double elapsed = (now - startNano) / 1_000_000_000.0; // seconds as double

                if (elapsed >= THRESHOLD_SECONDS) {
                    // switch to static text
                    showingStatic = true;
                    staticStartNano = now;
                    // draw background box + static text
                    String text = STATIC_TEXT;
                    int textWidth = font.width(text);
                    int textHeight = font.lineHeight;
                    int left = x - textWidth / 2 - 6;
                    int top = y - 4;
                    int right = x + textWidth / 2 + 6;
                    int bottom = y + textHeight + 4;

                    // semi-transparent black background (ARGB)
                    graphics.fill(left, top, right, bottom, 0x80000000);
                    // white text with shadow (use the overload with shadow if available)
                    try {
                        graphics.centeredText(font, text, x, y, 0xFFFFFFFF);
                    } catch (Throwable ignored) {
                        // fallback to no-shadow overload
                        graphics.centeredText(font, text, x, y, 0xFFFFFFFF);
                    }

                    System.out.println("[CooldownWarpDetector] switching to STATIC_TEXT at " + staticStartNano + " draw at (" + x + "," + y + ")");
                    return;
                }

                // round down to tenths of a second (0.1 increments)
                double tenths = Math.floor(elapsed * 10.0) / 10.0;
                // format with one decimal
                String timeText = String.format(Locale.ROOT, "%.1f s", tenths);

                // optional prefix label
                String label = "Warp timer: ";
                String full = label + timeText;

                // compute background box for the text
                int textWidth = font.width(full);
                int textHeight = font.lineHeight;
                int left = x - textWidth / 2 - 6;
                int top = y - 4;
                int right = x + textWidth / 2 + 6;
                int bottom = y + textHeight + 4;

                // draw background rectangle
                graphics.fill(left, top, right, bottom, 0x80000000); // semi-transparent black

                // draw the text in bright color and shadow
                try {
                    graphics.centeredText(font, full, x, y, 0xFF00FF00); // bright green with shadow
                } catch (Throwable ignored) {
                    graphics.centeredText(font, full, x, y, 0xFF00FF00);
                }

                // debug log so you can confirm coordinates + text
                System.out.println("[CooldownWarpDetector] draw timer: \"" + full + "\" at (" + x + "," + y + ")");
            } else {
                // showing static text; check how long it's been shown
                double staticElapsed = (now - staticStartNano) / 1_000_000_000.0;

                String text = STATIC_TEXT;
                int textWidth = font.width(text);
                int textHeight = font.lineHeight;
                int left = x - textWidth / 2 - 6;
                int top = y - 4;
                int right = x + textWidth / 2 + 6;
                int bottom = y + textHeight + 4;

                graphics.fill(left, top, right, bottom, 0x80000000);
                try {
                    graphics.centeredText(font, text, x, y, 0xFFFFFFFF);
                } catch (Throwable ignored) {
                    graphics.centeredText(font, text, x, y, 0xFFFFFFFF);
                }

                System.out.println("[CooldownWarpDetector] draw static: \"" + text + "\" at (" + x + "," + y + "), elapsed=" + staticElapsed);

                if (staticElapsed >= STATIC_DISPLAY_SECONDS) {
                    // end notification
                    active = false;
                    showingStatic = false;
                    startNano = 0L;
                    staticStartNano = 0L;
                    pending = false;
                    pendingStartNano = 0L;
                    System.out.println("[CooldownWarpDetector] static display finished — clearing state");
                }
            }
        });
    }
}