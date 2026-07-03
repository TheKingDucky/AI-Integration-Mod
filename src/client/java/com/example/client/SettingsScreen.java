/*package com.example.client;

import com.example.client.settingsscreens.ChatSettingsScreen;
import com.example.client.settingsscreens.GeneralSettingsScreen;
import com.example.client.settingsscreens.PandaSettingsScreen;
import com.example.client.settingsscreens.TurtleSettingsScreen;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class SettingsScreen extends Screen {

    // --- Button sizing / layout constants ---
    private static int BUTTON_WIDTH = 220;
    private static int BUTTON_HEIGHT = 20;
    private static int BUTTON_SPACING = 26;
    private static int BUTTON_START_Y = 80; // pushed slightly lower to make room for big title
    private static int BUTTON_CENTER_OFFSET = 0;

    // Title settings
    private static final String TITLE_TEXT = "Ducky Mod";
    private static int TITLE_X = 20;
    private static int TITLE_Y = 20;
    private static float TITLE_SCALE = 2.0f;
    private static int TITLE_COLOR = 0xFFFFFFFF;

    // NOTE: we no longer store the pandasEnabled here as primary state;
    // PandaSettingsScreen will call ClientInit.setPandaEnabled(...) directly.

    private Button turtlesOpenButton;
    private Button pandasOpenButton;
    private Button chatOpenButton;
    private Button GeneralOpenButton;// renamed from otherOpenButton

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_NAME = "ducky-settings.json";

    public SettingsScreen(Component title) {
        super(title);
        loadConfig(); // still load to preserve any file-based defaults if you add them later
    }

    @Override
    protected void init() {
        super.init();

        final int centerX = this.width / 2 + BUTTON_CENTER_OFFSET;
        final int startY = BUTTON_START_Y;
        final int spacing = BUTTON_SPACING;

        // MAIN FEATURE BUTTONS: open their respective secondary screens
        pandasOpenButton = Button.builder(Component.literal("Pandas (open)"), (btn) -> {
            // open Panda-specific settings screen
            this.minecraft.setScreen(new PandaSettingsScreen(Component.literal("Panda Settings")));
        }).bounds(centerX - BUTTON_WIDTH / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(pandasOpenButton);

        chatOpenButton = Button.builder(Component.literal("Chatting (open)"), (btn) -> {
            // open Chatting-specific settings screen (formerly Other)
            this.minecraft.setScreen(new ChatSettingsScreen(Component.literal("Chatting")));
        }).bounds(centerX - BUTTON_WIDTH / 2, startY + spacing, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(chatOpenButton);

        GeneralOpenButton = Button.builder(Component.literal("General (open)"), (btn) -> {
            // open Chatting-specific settings screen (formerly Other)
            this.minecraft.setScreen(new GeneralSettingsScreen(Component.literal("General")));
        }).bounds(centerX - BUTTON_WIDTH / 2, startY + spacing + spacing, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(GeneralOpenButton);

        turtlesOpenButton = Button.builder(Component.literal("Turtles (open)"), (btn) -> {
            // open Panda-specific settings screen
            this.minecraft.setScreen(new TurtleSettingsScreen(Component.literal("Turtle Settings")));
        }).bounds(centerX - BUTTON_WIDTH / 2, startY + spacing + spacing + spacing, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(turtlesOpenButton);





        // Done button: close (still saves config if you implement save here)
        this.addRenderableWidget(Button.builder(Component.literal("Done"), (btn) -> {
            saveConfig();
            this.minecraft.setScreen(null);
        }).bounds(centerX - BUTTON_WIDTH / 2, startY + spacing * 5, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        // Draw title (simple, guaranteed visible)
        graphics.drawString(this.font, TITLE_TEXT, TITLE_X, TITLE_Y, TITLE_COLOR, false);
    }

    private void loadConfig() {
        try {
            Path cfg = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_NAME);
            if (Files.exists(cfg)) {
                try (Reader r = Files.newBufferedReader(cfg)) {
                    ConfigData d = GSON.fromJson(r, ConfigData.class);
                    if (d != null) {
                        // keep for future use: you could initialize UI state from file here
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveConfig() {
        try {
            Path cfg = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_NAME);
            Files.createDirectories(cfg.getParent());
            try (Writer w = Files.newBufferedWriter(cfg)) {
                // Save defaults for now — extend with fields later
                GSON.toJson(new ConfigData(true, false), w);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ConfigData {
        boolean pandasEnabled;
        boolean someOtherFeature;
        ConfigData() {}
        ConfigData(boolean p, boolean o) {
            this.pandasEnabled = p;
            this.someOtherFeature = o;
        }
    }
}*/
package com.example.client;

import com.example.client.settingsscreens.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;  // ← renamed from GuiGraphics
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class SettingsScreen extends Screen {

    private static int BUTTON_WIDTH = 220;
    private static int BUTTON_HEIGHT = 20;
    private static int BUTTON_SPACING = 26;
    private static int BUTTON_START_Y = 80;
    private static int BUTTON_CENTER_OFFSET = 0;

    private static final String TITLE_TEXT = "Ducky Mod";
    private static int TITLE_X = 20;
    private static int TITLE_Y = 20;
    private static float TITLE_SCALE = 2.0f;
    private static int TITLE_COLOR = 0xFFFFFFFF;

    private Button turtlesOpenButton;
    private Button pandasOpenButton;
    private Button chatOpenButton;
    private Button GeneralOpenButton;
    private Button fishingOpenButton;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_NAME = "ducky-settings.json";

    public SettingsScreen(Component title) {
        super(title);
        loadConfig();
    }

    @Override
    protected void init() {
        super.init();

        final int centerX = this.width / 2 + BUTTON_CENTER_OFFSET;
        final int startY = BUTTON_START_Y;
        final int spacing = BUTTON_SPACING;

        pandasOpenButton = Button.builder(Component.literal("Pandas (open)"), (btn) -> {
            this.minecraft.setScreen(new PandaSettingsScreen(Component.literal("Panda Settings")));
        }).bounds(centerX - BUTTON_WIDTH / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(pandasOpenButton);

        chatOpenButton = Button.builder(Component.literal("Chatting (open)"), (btn) -> {
            this.minecraft.setScreen(new ChatSettingsScreen(Component.literal("Chatting")));
        }).bounds(centerX - BUTTON_WIDTH / 2, startY + spacing * 1, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(chatOpenButton);

        GeneralOpenButton = Button.builder(Component.literal("General (open)"), (btn) -> {
            this.minecraft.setScreen(new GeneralSettingsScreen(Component.literal("General")));
        }).bounds(centerX - BUTTON_WIDTH / 2, startY + spacing * 2, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(GeneralOpenButton);

        turtlesOpenButton = Button.builder(Component.literal("Turtles (open)"), (btn) -> {
            this.minecraft.setScreen(new TurtleSettingsScreen(Component.literal("Turtle Settings")));
        }).bounds(centerX - BUTTON_WIDTH / 2, startY + spacing * 3, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(turtlesOpenButton);

        fishingOpenButton = Button.builder(Component.literal("Fishing (open)"), (btn) -> {
            this.minecraft.setScreen(new FishingSettingsScreen(Component.literal("Fishing Settings")));
        }).bounds(centerX - BUTTON_WIDTH / 2, startY + spacing * 4, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(fishingOpenButton);

        this.addRenderableWidget(Button.builder(Component.literal("Done"), (btn) -> {
            saveConfig();
            this.minecraft.setScreen(null);
        }).bounds(centerX - BUTTON_WIDTH / 2, startY + spacing * 6, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    @Override
    public void extractRenderState (GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {  // ← updated type
        super.extractRenderState (graphics, mouseX, mouseY, delta);
        graphics.text(this.font, TITLE_TEXT, TITLE_X, TITLE_Y, TITLE_COLOR, false);
    }

    private void loadConfig() {
        try {
            Path cfg = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_NAME);
            if (Files.exists(cfg)) {
                try (Reader r = Files.newBufferedReader(cfg)) {
                    ConfigData d = GSON.fromJson(r, ConfigData.class);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveConfig() {
        try {
            Path cfg = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_NAME);
            Files.createDirectories(cfg.getParent());
            try (Writer w = Files.newBufferedWriter(cfg)) {
                GSON.toJson(new ConfigData(true, false), w);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ConfigData {
        boolean pandasEnabled;
        boolean someOtherFeature;
        ConfigData() {}
        ConfigData(boolean p, boolean o) {
            this.pandasEnabled = p;
            this.someOtherFeature = o;
        }
    }
}