/*
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
    private static int BUTTON_START_Y = 40;
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
    private Button aiOpenButton;

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

        aiOpenButton = Button.builder(Component.literal("AI (open)"), (btn) -> {
            this.minecraft.setScreen(new AiSettingsScreen(Component.literal("AI Settings")));
        }).bounds(centerX - BUTTON_WIDTH / 2, startY + spacing * 5, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(aiOpenButton);

        this.addRenderableWidget(Button.builder(Component.literal("Done"), (btn) -> {
            saveConfig();
            this.minecraft.setScreen(null);
        }).bounds(centerX - BUTTON_WIDTH / 2, startY + spacing * 7, BUTTON_WIDTH, BUTTON_HEIGHT).build());
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
}*/
package com.example.client;

import com.example.client.settingsscreens.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SettingsScreen extends Screen {

    private static int BUTTON_WIDTH = 220;
    private static int BUTTON_HEIGHT = 20;
    private static int BUTTON_SPACING = 26;
    private static int BUTTON_CENTER_OFFSET = 0;

    // Top of the scrollable list area (below the title)
    private static int LIST_TOP = 80;
    // Reserved space at the bottom for the Done button + a little breathing room
    private static int FOOTER_HEIGHT = 120;

    private static final String TITLE_TEXT = "Ducky Mod";
    private static int TITLE_X = 20;
    private static int TITLE_Y = 20;
    private static float TITLE_SCALE = 2.0f;
    private static int TITLE_COLOR = 0xFFFFFFFF;

    // Every "open settings screen" button lives in this list so we can scroll/hide them together.
    private final List<Button> scrollableButtons = new ArrayList<>();

    private Button doneButton;

    private double scrollAmount = 0;
    private int maxScrollAmount = 0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_NAME = "ducky-settings.json";

    public SettingsScreen(Component title) {
        super(title);
        loadConfig();
    }

    @Override
    protected void init() {
        super.init();

        scrollableButtons.clear();

        final int centerX = this.width / 2 + BUTTON_CENTER_OFFSET;

        addScrollableButton("AI (open)", (btn) ->
                this.minecraft.setScreen(new AiSettingsScreen(Component.literal("AI Settings"))), centerX);

        // Done button is anchored to the BOTTOM of the screen, not stacked after the others.
        // This is what guarantees it's always visible no matter how many buttons are above it.
        doneButton = Button.builder(Component.literal("Done"), (btn) -> {
            saveConfig();
            this.minecraft.setScreen(null);
        }).bounds(centerX - BUTTON_WIDTH / 2, this.height - FOOTER_HEIGHT + 7, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(doneButton);

        recalculateScrollBounds();
        // Keep existing scroll position (e.g. after a resize) but make sure it's still valid.
        scrollAmount = Mth.clamp(scrollAmount, 0, maxScrollAmount);
        layoutScrollableButtons();
    }

    private void addScrollableButton(String label, Button.OnPress onPress, int centerX) {
        Button button = Button.builder(Component.literal(label), onPress)
                .bounds(centerX - BUTTON_WIDTH / 2, LIST_TOP, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.addRenderableWidget(button);
        scrollableButtons.add(button);
    }

    private void recalculateScrollBounds() {
        int viewportBottom = this.height - FOOTER_HEIGHT;
        int viewportHeight = Math.max(0, viewportBottom - LIST_TOP);
        int contentHeight = scrollableButtons.size() * BUTTON_SPACING;
        maxScrollAmount = Math.max(0, contentHeight - viewportHeight);
    }

    private void layoutScrollableButtons() {
        int viewportBottom = this.height - FOOTER_HEIGHT;

        for (int i = 0; i < scrollableButtons.size(); i++) {
            Button button = scrollableButtons.get(i);
            int y = LIST_TOP + (i * BUTTON_SPACING) - (int) Math.round(scrollAmount);
            button.setY(y);

            // Hide + disable buttons that have scrolled fully out of the visible list area,
            // so they don't render over the title or the Done button and can't be clicked
            // while off-screen.
            boolean visible = (y + BUTTON_HEIGHT) > LIST_TOP && y < viewportBottom;
            button.visible = visible;
            button.active = visible;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScrollAmount > 0) {
            // One notch scrolls roughly one row. Flip the sign if it feels backwards to you.
            scrollAmount -= scrollY * BUTTON_SPACING;
            scrollAmount = Mth.clamp(scrollAmount, 0, maxScrollAmount);
            layoutScrollableButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(this.font, TITLE_TEXT, TITLE_X, TITLE_Y, TITLE_COLOR, false);

        // Optional: a simple scrollbar so it's obvious the list can scroll.
        if (maxScrollAmount > 0) {
            int viewportTop = LIST_TOP;
            int viewportBottom = this.height - FOOTER_HEIGHT;
            int viewportHeight = viewportBottom - viewportTop;
            int contentHeight = scrollableButtons.size() * BUTTON_SPACING;

            int barX = this.width / 2 + BUTTON_WIDTH / 2 + 6;
            int barHeight = Math.max(10, (int) ((float) viewportHeight * viewportHeight / contentHeight));
            int barY = viewportTop + (int) ((viewportHeight - barHeight) * (scrollAmount / maxScrollAmount));

            graphics.fill(barX, viewportTop, barX + 2, viewportBottom, 0x33FFFFFF);
            graphics.fill(barX, barY, barX + 2, barY + barHeight, 0xFFFFFFFF);
        }
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
        ConfigData() {}
        ConfigData(boolean p, boolean o) {

        }
    }
}