
package com.example.turtle;

import com.example.ConfigClass;
import com.example.TurtleDetector;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

//import static com.example.client.settingsscreens.TurtleSettingsScreen.turtleOne;

public final class TurtleTracker {
    private TurtleTracker() {}

    private static final double SCAN_RADIUS = 128.0;
    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final int COORD_MESSAGE_DELAY_TICKS = 8;
    private static final int SECOND_MESSAGE_DELAY_TICKS = 40;
    private static final int COORD_START_DELAY_AFTER_WARP_TICKS = 20;

    private static boolean turtlesDetected = false;
    private static boolean previouslyDetected = false;
    private static int turtleCount = 0;

    private static final Queue<String> messageQueue = new ArrayDeque<>();
    private static boolean queuePending = false;
    private static int ticksUntilNextMessage = 0;

    private static boolean secondMessagePending = false;
    private static int secondMessageTicksRemaining = 0;
    private static String secondMessageCommand = null;

    private static int tickCounter = 0;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!ConfigClass.INSTANCE.turtleEnabled) {
                tickCounter = 0;
                turtlesDetected = false;
                previouslyDetected = false;
                queuePending = false;
                messageQueue.clear();
                ticksUntilNextMessage = 0;
                secondMessagePending = false;
                secondMessageTicksRemaining = 0;
                secondMessageCommand = null;
                return;
            }

            tickCounter++;

            if (secondMessagePending) {
                secondMessageTicksRemaining--;
                if (secondMessageTicksRemaining <= 0) {
                    secondMessagePending = false;

                    if (secondMessageCommand != null && client.player != null && ConfigClass.INSTANCE.chatMessageSenderEnabled) {
                        client.player.connection.sendChat(secondMessageCommand);
                    }

                    if (!messageQueue.isEmpty()) {
                        queuePending = true;
                        ticksUntilNextMessage = COORD_START_DELAY_AFTER_WARP_TICKS;
                    }
                }
            }

            if (queuePending) {
                ticksUntilNextMessage--;
                if (ticksUntilNextMessage <= 0) {
                    String next = messageQueue.poll();
                    if (next != null && client.player != null && ConfigClass.INSTANCE.chatMessageSenderEnabled) {
                        client.player.connection.sendChat(next);
                    }
                    if (messageQueue.isEmpty()) {
                        queuePending = false;
                        ticksUntilNextMessage = 0;
                    } else {
                        ticksUntilNextMessage = COORD_MESSAGE_DELAY_TICKS;
                    }
                }
            }

            if (tickCounter < SCAN_INTERVAL_TICKS) return;
            tickCounter = 0;

            Player player = client.player;
            Level level = client.level;
            if (player == null || level == null) {
                turtlesDetected = false;
                previouslyDetected = false;
                queuePending = false;
                messageQueue.clear();
                ticksUntilNextMessage = 0;
                secondMessagePending = false;
                secondMessageTicksRemaining = 0;
                secondMessageCommand = null;
                return;
            }

            List<?> found = TurtleDetector.findTurtlesNearPlayer(player, level, SCAN_RADIUS);
            boolean nowFound = found != null && !found.isEmpty();
            turtleCount = nowFound ? found.size() : 0;
            turtlesDetected = nowFound;

            if (!previouslyDetected && nowFound) {
                if (ConfigClass.INSTANCE.chatMessageSenderEnabled) {
                    if (turtleCount == 1 && ConfigClass.INSTANCE.turtleOne == true) {
                        client.player.connection.sendChat("/pc Turtle found: 1 nearby! Going back to hub in 2 Seconds");
                        secondMessageCommand = "/warp hub";
                    } else {
                        client.player.connection.sendChat("/pc Turtles found: " + turtleCount + " nearby! Warping in 2 Seconds");
                        secondMessageCommand = "/p warp";
                    }

                    messageQueue.clear();
                    for (int i = 0; i < turtleCount; i++) {
                    //  for (int i = 0; i < 1; i++) {
                        Entity e = (Entity) found.get(i);
                        int xi = (int) Math.round(e.getX());
                        int yi = (int) Math.round(e.getY());
                        int zi = (int) Math.round(e.getZ());
                        String coordMsg = "/pc x: " + xi + ", y: " + yi + ", z: " + zi;
                        messageQueue.add(coordMsg);
                    }

                    secondMessagePending = true;
                    secondMessageTicksRemaining = SECOND_MESSAGE_DELAY_TICKS;
                }
            }

            previouslyDetected = nowFound;
        });

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("ducky-mod", "turtle-tracker"),
                (graphics, tickCounter) -> {
            if (!ConfigClass.INSTANCE.turtleEnabled || !turtlesDetected) return;

            Minecraft client = Minecraft.getInstance();
            Font font = client.font;

            String text = "TURTLES DETECTED: " + turtleCount;

            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();

            int x = screenWidth / 2;
            int y = screenHeight / 3;

            int color = 0xFFFF0000;

            graphics.centeredText(font, text, x, y, color);
        });
    }

    public static void setEnabled(boolean enabled) {
        if (!enabled) {
            turtlesDetected = false;
            previouslyDetected = false;
            queuePending = false;
            messageQueue.clear();
            ticksUntilNextMessage = 0;
            secondMessagePending = false;
            secondMessageTicksRemaining = 0;
            secondMessageCommand = null;
            tickCounter = 0;
        }
    }

    public static boolean isEnabled() {
        return ConfigClass.INSTANCE.turtleEnabled;
    }

    // ✅ ADDED METHOD (this is the ONLY new part)
    public static void sendTurtleCoords() {
        if (!ConfigClass.INSTANCE.turtleEnabled) return;
        if (!ConfigClass.INSTANCE.chatMessageSenderEnabled) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        List<?> found = TurtleDetector.findTurtlesNearPlayer(client.player, client.level, SCAN_RADIUS);
        if (found == null || found.isEmpty()) return;

        messageQueue.clear();
        queuePending = false;

        for (int i = 0; i < found.size(); i++) {
            Entity e = (Entity) found.get(i);

            int xi = (int) Math.round(e.getX());
            int yi = (int) Math.round(e.getY());
            int zi = (int) Math.round(e.getZ());

            String coordMsg = "/pc x: " + xi + ", y: " + yi + ", z: " + zi;
            messageQueue.add(coordMsg);
        }

        if (!messageQueue.isEmpty()) {
            queuePending = true;
            ticksUntilNextMessage = 0;
        }
    }
}