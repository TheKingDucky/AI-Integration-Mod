package com.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigClass {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve("yourmodid.json");

    public static ConfigClass INSTANCE = new ConfigClass();

    public boolean chatEnabled = true;
    public boolean pandaEnabled = false;
    public boolean turtleEnabled = false;
    public boolean fishingEnabled = false;
    public boolean chatMessageSenderEnabled = true;
    public boolean warpCooldownEnabled = true;
    public boolean sameLobbyDetectorEnabled = true;
    public boolean turtleOne = false;
    public double fishingSensitivity = 0.01D; // default, matches the old constant

    public static void load() {
        if (Files.exists(PATH)) {
            try {
                String json = Files.readString(PATH, StandardCharsets.UTF_8);
                ConfigClass loaded = GSON.fromJson(json, ConfigClass.class);
                if (loaded != null) {
                    INSTANCE = loaded;
                    return;
                }
            } catch (Exception e) {
                System.out.println("Could not load config, using defaults.");
            }
        }

        INSTANCE = new ConfigClass();
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(INSTANCE), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}