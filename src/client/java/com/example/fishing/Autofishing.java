package com.example.fishing;

import com.example.ConfigClass;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;

import java.util.Random;

/**
 * Automates fishing on Fabric 1.26.1.2 (official Mojang mappings, unobfuscated):
 *  1. Waits for the bobber (FishingHook) to dip (a fish biting).
 *  2. Waits a random delay, then reels in (right-click again).
 *  3. Waits another random delay, then casts again (right-click).
 *
 * This class follows the same pattern as your other feature classes
 * (PandaTracker, TurtleTracker, etc.): everything is static, so you call
 * it directly as Autofishing.register() / Autofishing.setEnabled(...)
 * with no instance to create or manage yourself.
 */
public class Autofishing {

    // --- Tunable delay ranges (in ticks, 20 ticks = 1 second) ---
    // Reeling in needs to happen FAST after a bite - vanilla only gives you
    // a short window before the hook pops back out empty-handed. 2-6 ticks
    // is ~0.1-0.3s, which is close to instant but still slightly randomized.
    private static final int MIN_REEL_DELAY_TICKS = 2;
    private static final int MAX_REEL_DELAY_TICKS = 4;

    private static final int MIN_CAST_DELAY_TICKS = 7;   // 2s
    private static final int MAX_CAST_DELAY_TICKS = 15;   // 4s

    // How long to wait after casting before we even start checking whether
    // the bobber has settled. Fixed at 20 ticks (1 second) - no more
    // "settled" guesswork here, that's handled by the phase below.
    private static final int WAIT_TICKS_BEFORE_WATCHING = 20;

    // --- Settle-detection tuning ---
    // On fast-reel worlds the bobber sometimes hasn't finished rising to the
    // surface by the time WAIT_TICKS_BEFORE_WATCHING elapses. Instead of
    // immediately trusting the timer, we require the bobber's Y position to
    // stay under the bite-sensitivity threshold for several consecutive
    // ticks in a row before we start treating movement as a real bite.
    private static final int MIN_SETTLE_STABLE_TICKS = 3;

    // Failsafe: if the bobber never settles cleanly (choppy water, waves,
    // whatever), don't wait forever - just start watching for bites anyway
    // after this many extra ticks past the initial wait.
    private static final int MAX_ADDITIONAL_SETTLE_TICKS = 40; // +2s cap

    // How much the bobber's Y position has to change tick-to-tick for us to
    // treat it as movement (used both to detect "still settling" and to
    // detect an actual bite). Lives on ConfigClass so the settings screen
    // slider can read/write it live. Default value is set wherever
    // ConfigClass.INSTANCE.fishingSensitivity is initialized (e.g. 0.01D).

    private enum State {
        IDLE,
        WAITING_FOR_BITE,
        WAITING_TO_REEL,
        WAITING_TO_CAST
    }

    // --- All state is now static, since there's only ever one fishing bot ---
    private static final Random random = new Random();
    private static State state = State.IDLE;
    private static int ticksUntilNextAction = 0;
    private static double lastBobberY = Double.NaN;
    //private static boolean enabled = true;
    private static boolean registered = false;
    private static int ticksSinceCast = 0;

    // Settle-phase tracking (reset on every new cast)
    private static boolean settled = false;
    private static int stableTickCount = 0;
    private static int settleTicksElapsed = 0;

    // Set to true temporarily to print state-change messages to chat.
    // Turn this off once everything's working - it's noisy.
    private static final boolean DEBUG = true;

    private static void debug(String message) {
        if (!DEBUG) return;
        /*Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal("[AutoFish] " + message));
        }*/
        System.out.println("[AutoFish] " + message);
    }

    // Private constructor - this class is never instantiated, just like
    // your other static-pattern feature classes.
    private Autofishing() {}

    /** Call once from ClientInit.onInitializeClient(), same as your other features. */
    public static void register() {
        if (registered) return; // guard against double-registration
        registered = true;
        ClientTickEvents.START_CLIENT_TICK.register(Autofishing::onClientTick);
    }

    public static void setEnabled(boolean isEnabled) {
        ConfigClass.INSTANCE.fishingEnabled = isEnabled;
        debug("AutoFisher enabled = " + isEnabled);
        if (!isEnabled) {
            state = State.IDLE;
            lastBobberY = Double.NaN;
        }
    }

    public static boolean isEnabled() {
        return ConfigClass.INSTANCE.fishingEnabled;
    }

    private static void onClientTick(Minecraft client) {
        if (!ConfigClass.INSTANCE.fishingEnabled) return;

        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            state = State.IDLE;
            return;
        }

        switch (state) {
            case IDLE -> tryStartWatching(player);
            case WAITING_FOR_BITE -> watchForBite(player);
            case WAITING_TO_REEL -> countDown(() -> reelIn(player));
            case WAITING_TO_CAST -> countDown(() -> castRod(player));
        }
    }

    private static void tryStartWatching(LocalPlayer player) {
        FishingHook hook = player.fishing;
        if (hook != null) {
            lastBobberY = hook.getY();
            ticksSinceCast = 0;
            resetSettleTracking();
            state = State.WAITING_FOR_BITE;
            debug("Detected cast line out, waiting " + WAIT_TICKS_BEFORE_WATCHING + " ticks before checking for settle.");
        }
    }

    private static void resetSettleTracking() {
        settled = false;
        stableTickCount = 0;
        settleTicksElapsed = 0;
    }

    private static void watchForBite(LocalPlayer player) {
        FishingHook hook = player.fishing;
        if (hook == null) {
            debug("Line disappeared while watching (snagged/despawned/cleared).");
            state = State.IDLE;
            lastBobberY = Double.NaN;
            return;
        }

        double currentY = hook.getY();

        // Phase 1: flat wait after casting before we even start looking at
        // bobber movement.
        if (ticksSinceCast < WAIT_TICKS_BEFORE_WATCHING) {
            ticksSinceCast++;
            lastBobberY = currentY;
            return;
        }

        double deltaY = currentY - lastBobberY;
        lastBobberY = currentY;

        // Phase 2: wait for the bobber to actually settle at the surface
        // before we start treating movement as a bite. Without this, a
        // bobber that's still rising from the cast (common on fast-reel
        // worlds) can get misread as a bite the instant the timer expires.
        if (!settled) {
            if (Math.abs(deltaY) > ConfigClass.INSTANCE.fishingSensitivity) {
                // Still moving noticeably -> not settled yet, reset the streak.
                stableTickCount = 0;
            } else {
                stableTickCount++;
            }

            settleTicksElapsed++;

            if (stableTickCount >= MIN_SETTLE_STABLE_TICKS) {
                settled = true;
                debug("Bobber settled after " + settleTicksElapsed + " extra tick(s), now watching for bites.");
            } else if (settleTicksElapsed >= MAX_ADDITIONAL_SETTLE_TICKS) {
                // Failsafe so we never get stuck if the bobber never fully
                // stops moving (waves, lag, etc.).
                settled = true;
                debug("Settle timeout hit (" + settleTicksElapsed + " ticks); watching for bites anyway.");
            }
            return; // don't let settling motion count as a bite
        }

        // Phase 3: actively watching for a real bite.
        if (Math.abs(deltaY) > ConfigClass.INSTANCE.fishingSensitivity) {
            debug("Bite detected! deltaY=" + deltaY);
            ticksUntilNextAction = randomBetween(MIN_REEL_DELAY_TICKS, MAX_REEL_DELAY_TICKS);
            state = State.WAITING_TO_REEL;
        }
    }

    private static void countDown(Runnable onZero) {
        if (ticksUntilNextAction > 0) {
            ticksUntilNextAction--;
            return;
        }
        onZero.run();
    }

    private static void reelIn(LocalPlayer player) {
        debug("Reeling in now.");
        useRod(player);
        ticksUntilNextAction = randomBetween(MIN_CAST_DELAY_TICKS, MAX_CAST_DELAY_TICKS);
        state = State.WAITING_TO_CAST;
        lastBobberY = Double.NaN;
    }

    private static void castRod(LocalPlayer player) {
        if (!isHoldingFishingRod(player)) {
            debug("No fishing rod in hand - stopping.");
            state = State.IDLE;
            return;
        }
        debug("Casting rod now.");
        useRod(player);
        state = State.WAITING_FOR_BITE;
        lastBobberY = Double.NaN;
        ticksSinceCast = 0;
        resetSettleTracking();
    }

    private static boolean isHoldingFishingRod(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        return main.getItem() instanceof FishingRodItem || off.getItem() instanceof FishingRodItem;
    }

    private static void useRod(LocalPlayer player) {
        Minecraft client = Minecraft.getInstance();
        if (client.gameMode == null) return;

        InteractionHand hand = player.getMainHandItem().getItem() instanceof FishingRodItem
                ? InteractionHand.MAIN_HAND
                : InteractionHand.OFF_HAND;

        client.gameMode.useItem(player, hand);
        player.swing(hand);
    }

    private static int randomBetween(int minInclusive, int maxInclusive) {
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }
}