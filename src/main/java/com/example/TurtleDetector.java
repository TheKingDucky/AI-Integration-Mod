package com.example;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class TurtleDetector {
    private TurtleDetector() {}

    public static List<Entity> findTurtlesNearPlayer(Player player, Level level, double radius) {
        if (player == null || level == null) return List.of();

        double minY = 38; // <-- CHANGE THIS to whatever Y level you want

        double px = player.getX();
        double pz = player.getZ();

        // Only search above minY
        AABB box = new AABB(
                px - radius, minY, pz - radius,
                px + radius, level.getMaxY(), pz + radius
        );

        List<Turtle> turtles = level.getEntitiesOfClass(
                Turtle.class,
                box,
                (Predicate<Turtle>) t -> t.getY() >= minY
        );

        // Convert to List<Entity> (fixes type mismatch cleanly)
        return new ArrayList<Entity>(turtles);
    }
}