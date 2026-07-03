package com.example.general;

import com.example.ConfigClass;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



/**
 * ChatWarpDetector
 *
 * - Detects the 3-message warp sequence and extracts the "mini..." token.
 * - Keeps tokens for 4 minutes; duplicate within 3 minutes -> trigger.
 * - When duplicate detected:
 *     - prints a quiet log line,
 *     - displays 3 immediate client-only chat messages,
 *     - shows an on-screen overlay for 3 seconds,
 *     - schedules one more client-only chat message 2 seconds later.
 *
 * The whole detector can be turned on/off via ChatWarpDetector.setEnabled(false/true).
 */
public final class SameLobbyDetector {
    private SameLobbyDetector() {}

    // ---- enable toggle ----
    private static volatile boolean enabled = true;
    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean v) { enabled = v; }

    // time windows (ms)
    private static final long KEEP_MS = 4 * 60_000L;   // 4 minutes
    private static final long DUP_MS  = 3 * 60_000L;   // 3 minutes
    private static final long SEQ_WINDOW_MS = 8_000L;  // max time between sequence messages

    // overlay / notification durations
    private static final long OVERLAY_MS = 3_000L;     // overlay lasts 3 seconds
    private static final long FOLLOWUP_DELAY_MS = 3_000L; // 2 second follow-up

    // regex to extract the "mini..." token (mini followed by non-space chars)
    private static final Pattern MINI_PATTERN = Pattern.compile("\\b(mini\\S+)\\b", Pattern.CASE_INSENSITIVE);

    // simple timestamped log of tokens (oldest first)
    private static final Deque<Entry> tokens = new ArrayDeque<>();

    // state for detecting the 3-message sequence (timestamps of last seen markers)
    private static volatile long lastWarpToMurkAt = 0L;
    private static volatile long lastWarpingDotsAt = 0L;

    // overlay text state
    private static volatile String overlayText = null;
    private static volatile long overlayExpiresAt = 0L;

    // scheduled actions to run later on client tick
    private static final Deque<Delayed> delayed = new ArrayDeque<>();

    private static boolean listenersRegistered = false;

    private static final class Entry {
        final String token;
        final long time;
        Entry(String token, long time) { this.token = token; this.time = time; }
    }

    private static final class Delayed {
        final Runnable action;
        final long runAt;
        Delayed(Runnable action, long runAt) { this.action = action; this.runAt = runAt; }
    }

    public static void register() {
        // avoid double-registering listeners if register() called more than once
        if (!listenersRegistered) {
            listenersRegistered = true;

            // HUD overlay renderer
            HudElementRegistry.attachElementBefore(
                    VanillaHudElements.CHAT,
                    Identifier.fromNamespaceAndPath("ducky-mod", "same-lobby-detector"),
                    (graphics, tickCounter) -> {
                long now = System.currentTimeMillis();
                if (overlayText != null && now < overlayExpiresAt) {
                    Minecraft client = Minecraft.getInstance();
                    if (client != null) {
                        Font font = client.font;
                        String text = overlayText;
                        int screenWidth = client.getWindow().getGuiScaledWidth();
                        int screenHeight = client.getWindow().getGuiScaledHeight();
                        int x = screenWidth / 2;
                        int y = screenHeight / 3;
                        int color = 0xFFFF0000; // red
                        graphics.centeredText(font, text, x, y, color);
                    }
                }
            });

            // client tick for delayed actions
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (delayed.isEmpty()) return;
                long now = System.currentTimeMillis();
                List<Delayed> run = new LinkedList<>();
                synchronized (delayed) {
                    while (!delayed.isEmpty() && delayed.peekFirst().runAt <= now) {
                        run.add(delayed.removeFirst());
                    }
                }
                for (Delayed d : run) {
                    try {
                        d.action.run();
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            });
        }

        // main chat listener (GAME messages, Hypixel-style)
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            // Respect the enabled flag: if detector is disabled do nothing.
            if (!enabled) return;

            if (message == null) return;

            String text = message.getString().trim(); // stripped of § codes
            long now = System.currentTimeMillis();

            // --- sequence detection ---
            if (text.equalsIgnoreCase("Warping to Murk")) {
                lastWarpToMurkAt = now;
                return;
            }

            if (text.equalsIgnoreCase("Warping...")) {
                lastWarpingDotsAt = now;
                return;
            }

            // third message: likely "Sending to server miniXXXXX..." (case-insensitive)
            if (text.toLowerCase().startsWith("sending to server")) {

                // ensure the prior messages happened recently and in order
                if (lastWarpToMurkAt == 0L || lastWarpingDotsAt == 0L) {
                    return;
                }
                if (!(lastWarpToMurkAt <= lastWarpingDotsAt && lastWarpingDotsAt <= now)) {
                    return;
                }
                if ((now - lastWarpToMurkAt) > SEQ_WINDOW_MS || (now - lastWarpingDotsAt) > SEQ_WINDOW_MS) {
                    return;
                }

                // extract mini token
                Matcher m = MINI_PATTERN.matcher(text);
                if (!m.find()) return; // no mini token
                String miniToken = m.group(1);

                // quiet log print: sequence seen
                System.out.println("[ChatWarpDetector] Detected mini token: " + miniToken);

                // clean old entries
                cleanOld(now);

                // check whether the token already exists within DUP_MS (3 minutes)
                boolean foundRecentDuplicate = false;
                for (Entry e : tokens) {
                    if (e.token.equalsIgnoreCase(miniToken) && (now - e.time) <= DUP_MS) {
                        foundRecentDuplicate = true;
                        break;
                    }
                }

                // add this occurrence to the log
                tokens.addLast(new Entry(miniToken, now));
                cleanOld(now);

                // reset sequence timestamps to avoid double-detection from same sequence
                lastWarpToMurkAt = 0L;
                lastWarpingDotsAt = 0L;

                if (foundRecentDuplicate) {
                    // quiet duplicate log
                    System.out.println("[ChatWarpDetector] Duplicate mini token within 3 minutes: " + miniToken);

                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null && mc.player != null) {
                        // show 3 immediate client-only chat messages (your existing behavior)
                        mc.player.sendSystemMessage(Component.literal("SAME LOBBY DETECTED AS LAST 3 MINUTES"));
                        mc.player.sendSystemMessage(Component.literal("SAME LOBBY DETECTED AS LAST 3 MINUTES"));
                        mc.player.sendSystemMessage(Component.literal("SAME LOBBY DETECTED AS LAST 3 MINUTES"));

                        // send a short party message (defensive null check)
                        if (mc.player.connection != null && ConfigClass.INSTANCE.chatMessageSenderEnabled) {
                            mc.player.connection.sendChat("/pc Duplicate Lobby Detected, Warping Hub In 2s");
                        }

                        // set overlay text for 3 seconds
                        overlayText = "SAME LOBBY DETECTED";
                        overlayExpiresAt = System.currentTimeMillis() + OVERLAY_MS;

                        // schedule one more client-only message after 2 seconds
                        schedule(() -> {
                            Minecraft mcInner = Minecraft.getInstance();
                            if (mcInner != null && mcInner.player != null && mcInner.player.connection != null && ConfigClass.INSTANCE.chatMessageSenderEnabled==true) {
                                mcInner.player.connection.sendChat("/warp hub");
                            }
                        }, System.currentTimeMillis() + FOLLOWUP_DELAY_MS);
                    }
                }
            }
        });
    }

    // schedule a runnable to run at given epoch millis (thread-safe small queue)
    private static void schedule(Runnable r, long runAt) {
        synchronized (delayed) {
            delayed.addLast(new Delayed(r, runAt));
        }
    }

    // remove entries older than KEEP_MS
    private static void cleanOld(long now) {
        long cutoff = now - KEEP_MS;
        Iterator<Entry> it = tokens.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (e.time < cutoff) it.remove();
            else break; // entries are ordered by time, so we can stop early
        }
    }
}