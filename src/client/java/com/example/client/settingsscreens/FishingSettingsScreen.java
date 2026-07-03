package com.example.client.settingsscreens;

import com.example.ConfigClass;
import com.example.client.SettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class FishingSettingsScreen extends Screen {
    private static int BUTTON_WIDTH = 220;
    private static int BUTTON_HEIGHT = 20;
    private static int BUTTON_SPACING = 26;
    private static int BUTTON_CENTER_OFFSET = 0;
    private static int BUTTON_START_Y = 80;

    //private boolean turtlesEnabled;
    //public static boolean turtleOne = false;


    private Button fishingToggleButton;
    private SensitivitySlider sensitivitySlider;
    private Button backButton;

    public FishingSettingsScreen(Component title) {
        super(title);
        // initialize with current global state
        //ConfigClass.INSTANCE.turtleEnabled = com.example.ClientInit.isTurtleEnabled();
    }

    @Override
    protected void init() {
        super.init();

        final int centerX = this.width / 2 + BUTTON_CENTER_OFFSET;
        final int startY = BUTTON_START_Y;
        final int spacing = BUTTON_SPACING;

        fishingToggleButton = Button.builder(toggleText("Enable Fishing Assist", ConfigClass.INSTANCE.fishingEnabled), (btn) -> {
            ConfigClass.INSTANCE.fishingEnabled = !ConfigClass.INSTANCE.fishingEnabled;
            btn.setMessage(toggleText("Enable Fishing Assist", ConfigClass.INSTANCE.fishingEnabled));
            // apply immediately to mod
            //com.example.ClientInit.setTurtleEnabled(ConfigClass.INSTANCE.turtleEnabled);

            // send the requested chat message on toggle (use sendSystemMessage for mapping safety)
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.player != null) {
                    if (ConfigClass.INSTANCE.fishingEnabled) {
                        mc.player.sendSystemMessage(Component.literal("§l§aFishing Assist Enabled."));
                    } else {
                        mc.player.sendSystemMessage(Component.literal("§l§cFishing Assist Disabled."));
                    }
                }
            } catch (Throwable t) {
                // defensive: don't crash the UI if messaging fails
                t.printStackTrace();
            }
        }).bounds(centerX - BUTTON_WIDTH / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(fishingToggleButton);

        // Sensitivity slider - drag like the vanilla FOV slider.
        // Range/step tuned via SensitivitySlider.MIN / MAX below.
        sensitivitySlider = new SensitivitySlider(
                centerX - BUTTON_WIDTH / 2, startY + spacing, BUTTON_WIDTH, BUTTON_HEIGHT
        );
        this.addRenderableWidget(sensitivitySlider);


        // Back button returns to the main SettingsScreen (moved down)
        backButton = Button.builder(Component.literal("Back"), (btn) -> {
            this.minecraft.setScreen(new SettingsScreen(Component.literal("Ducky Mod")));
        }).bounds(centerX - BUTTON_WIDTH / 2, startY + spacing * 3, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(backButton);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        // You can draw a small subtitle at top if you like:
        graphics.text(this.font, "Fishing settings", 20, 20, 0xFFFFFFFF, false);
    }

    private static Component toggleText(String label, boolean on) {
        return Component.literal(label + ": " + (on ? "ON" : "OFF"));
    }

    /**
     * Drag-to-adjust slider for the bite-detection sensitivity, styled the
     * same way vanilla's FOV slider works. AbstractSliderButton internally
     * stores its value as a 0.0-1.0 double ("this.value"), so we map that
     * onto our real MIN..MAX sensitivity range ourselves.
     */
    private static class SensitivitySlider extends AbstractSliderButton {
        // Tune these to whatever range makes sense for your detection logic.
        private static final double MIN = 0.001D;
        private static final double MAX = 0.10D;

        public SensitivitySlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), toPct(ConfigClass.INSTANCE.fishingSensitivity));
            this.updateMessage();
        }

        private static double toPct(double realValue) {
            double clamped = Math.max(MIN, Math.min(MAX, realValue));
            return (clamped - MIN) / (MAX - MIN);
        }

        private static double fromPct(double pct) {
            return MIN + pct * (MAX - MIN);
        }

        @Override
        protected void updateMessage() {
            double realValue = fromPct(this.value);
            this.setMessage(Component.literal(String.format("Sensitivity: %.3f", realValue)));
        }

        @Override
        protected void applyValue() {
            ConfigClass.INSTANCE.fishingSensitivity = fromPct(this.value);
        }
    }
}