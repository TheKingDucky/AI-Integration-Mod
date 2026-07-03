package com.example;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Minimal helper that finds Panda entities near a player.
 * Returns the list of found panda entities (client-side instances).
 */
public final class PandaDetector {
    private PandaDetector() {}

    /**
     * Finds pandas in an axis-aligned box centered on the player.
     * Returns a (mutable) list of found Panda instances as Entities (so caller can inspect/format).
     */
    public static List<Entity> findPandasNearPlayer(Player player, Level level, double radius) {
        if (player == null || level == null) return List.of();

        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();

        AABB box = new AABB(px - radius, py - radius, pz - radius, px + radius, py + radius, pz + radius);

        // getEntitiesOfClass returns typed mob instances (Panda). We convert to Entity list to keep API tiny.
        List<Panda> pandas = level.getEntitiesOfClass(Panda.class, box, (Predicate<Panda>) p -> true);
        return new ArrayList<>(pandas);
    }
}