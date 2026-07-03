package com.example.client.settingsscreens;

import com.example.client.SettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class PandaSettingsScreen extends Screen {
    private static int BUTTON_WIDTH = 220;
    private static int BUTTON_HEIGHT = 20;
    private static int BUTTON_SPACING = 26;
    private static int BUTTON_CENTER_OFFSET = 0;
    private static int BUTTON_START_Y = 80;

    private boolean pandasEnabled;


    private Button pandasToggleButton;
    private Button backButton;

    public PandaSettingsScreen(Component title) {
        super(title);
        // initialize with current global state
        this.pandasEnabled = com.example.ClientInit.isPandaEnabled();
    }

    @Override
    protected void init() {
        super.init();

        final int centerX = this.width / 2 + BUTTON_CENTER_OFFSET;
        final int startY = BUTTON_START_Y;
        final int spacing = BUTTON_SPACING;

        pandasToggleButton = Button.builder(toggleText("Enable Panda Tracker", pandasEnabled), (btn) -> {
            pandasEnabled = !pandasEnabled;
            btn.setMessage(toggleText("Enable Panda Tracker", pandasEnabled));
            // apply immediately to mod

            com.example.ClientInit.setPandaEnabled(pandasEnabled);

            // send the requested chat message on toggle (use sendSystemMessage for mapping safety)
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.player != null) {
                    if (pandasEnabled) {
                        mc.player.sendSystemMessage(Component.literal("§l§aPanda Tracking Enabled."));
                    } else {
                        mc.player.sendSystemMessage(Component.literal("§l§cPanda Tracking Disabled."));
                    }
                }
            } catch (Throwable t) {
                // defensive: don't crash the UI if messaging fails
                t.printStackTrace();
            }
        }).bounds(centerX - BUTTON_WIDTH / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(pandasToggleButton);

        // Warp detector toggle button (new)


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
        graphics.text(this.font, "Panda settings", 20, 20, 0xFFFFFFFF, false);
    }

    private static Component toggleText(String label, boolean on) {
        return Component.literal(label + ": " + (on ? "ON" : "OFF"));
    }
}