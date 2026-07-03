package com.example.panda;

import com.example.ConfigClass;
import com.example.PandaDetector;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

//import static com.example.client.settingsscreens.GeneralSettingsScreen.ChatMessageSenderEnabled;

public final class PandaTracker {
    private PandaTracker() {}

    private static final double SCAN_RADIUS = 128.0;
    private static final int SCAN_INTERVAL_TICKS = 20;         // scan every ~1s
    private static final int SECOND_MESSAGE_DELAY_TICKS = 40;  // 2 seconds

    // feature toggle: when false, no scanning, no chat messages, no HUD
    //private static volatile boolean pandaEnabled = false;

    // state
    private static boolean pandasDetected = false;        // current scan result
    private static boolean previouslyDetected = false;    // previous scan result
    private static int pandaCount = 0;

    // message scheduling
    private static boolean secondMessagePending = false;

    private static int secondMessageTicksRemaining = 0;
    // <-- NEW: stores which second message to send after delay
    private static String secondMessageToSend = null;

    private static int tickCounter = 0;

    /** Register the tracker — call once from ClientInit.onInitializeClient(). */
    public static void register() {
        // periodic scanner
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // if feature disabled, reset state and do nothing
            if (!ConfigClass.INSTANCE.pandaEnabled) {
                tickCounter = 0;
                pandasDetected = false;
                previouslyDetected = false;
                secondMessagePending = false;
                secondMessageTicksRemaining = 0;
                secondMessageToSend = null; // reset the queued message
                return;
            }

            // increment every client tick
            tickCounter++;

            // tick down the scheduled second message every client tick (so delay is real-time ticks)
            if (secondMessagePending) {
                secondMessageTicksRemaining--;
                if (secondMessageTicksRemaining <= 0) {
                    secondMessagePending = false;
                    // send second message only if pandas still present (based on last scan)
                    if (client.player != null && pandasDetected) {
                        if (secondMessageToSend != null) {
                            client.player.connection.sendChat(secondMessageToSend);
                        }
                    }
                    // clear the queued message after attempting to send
                    secondMessageToSend = null;
                }
            }

            // only run the scan logic every SCAN_INTERVAL_TICKS
            if (tickCounter < SCAN_INTERVAL_TICKS) return;
            tickCounter = 0;

            Player player = client.player;
            Level level = client.level;
            if (player == null || level == null) {
                // reset state when not in world
                pandasDetected = false;
                previouslyDetected = false;
                secondMessagePending = false;
                secondMessageToSend = null;
                return;
            }

            List<?> found = PandaDetector.findPandasNearPlayer(player, level, SCAN_RADIUS);
            boolean nowFound = found != null && !found.isEmpty();
            pandaCount = nowFound ? found.size() : 0;
            pandasDetected = nowFound;

            // Transition: not previously detected -> now detected
            if (!previouslyDetected && nowFound) {
                // Choose messages based on how many pandas were found
                if (pandaCount == 1 && ConfigClass.INSTANCE.chatMessageSenderEnabled==true) {
                    // 1) immediate chat message for single panda
                    client.player.connection.sendChat("/pc Panda found: 1 nearby! Going back to hub in 2 Seconds");
                    // schedule single-panda follow-up after delay
                    secondMessageToSend = "/warp hub";
                } else if (pandaCount > 1 && ConfigClass.INSTANCE.chatMessageSenderEnabled==true){
                    // 1) immediate chat message for multiple pandas
                    client.player.connection.sendChat("/pc Pandas found: " + pandaCount + " nearby! Warping in 2 Seconds");
                    // schedule multi-panda follow-up after delay
                        secondMessageToSend = "/p warp";

                }

                // schedule second message after delay (only if still present when timer expires)
                secondMessagePending = true;
                secondMessageTicksRemaining = SECOND_MESSAGE_DELAY_TICKS;
            }

            // update previous state for next scan
            previouslyDetected = nowFound;
        });

        // Render big centered HUD text while pandasDetected == true (unchanged)
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("ducky-mod", "panda-tracker"),
                (graphics, tickCounter) -> {
            if (!ConfigClass.INSTANCE.pandaEnabled || !pandasDetected) return;

            Minecraft client = Minecraft.getInstance();
            Font font = client.font;

            String text = "PANDAS DETECTED: " + pandaCount;

            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();

            int x = screenWidth / 2;
            int y = screenHeight / 3;

            int color = 0xFFFF0000; // red

            graphics.centeredText(font, text, x, y, color);
        });
    }

    // --- Minimal public accessors so commands can toggle the feature ---

    // Toggle panda tracking on or off. *
    public static void setEnabled(boolean enabled) {
        //pandaEnabled = enabled;
        if (!enabled) {
            // clear transient state immediately when disabling
            pandasDetected = false;
            previouslyDetected = false;
            secondMessagePending = false;
            secondMessageTicksRemaining = 0;
            secondMessageToSend = null;
            tickCounter = 0;
        }
    }

    /** Query whether panda tracking is enabled. */
    public static boolean isEnabled() {
        return ConfigClass.INSTANCE.pandaEnabled;
    }
}