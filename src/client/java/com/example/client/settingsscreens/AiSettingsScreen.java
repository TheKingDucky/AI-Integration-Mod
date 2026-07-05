package com.example.client.settingsscreens;

import com.example.ConfigClass;
import com.example.client.SettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AiSettingsScreen extends Screen {
    private static int BUTTON_WIDTH = 220;
    private static int BUTTON_HEIGHT = 20;
    private static int BUTTON_SPACING = 26;
    private static int BUTTON_CENTER_OFFSET = 0;
    private static int BUTTON_START_Y = 80;


    private boolean aiEnabled = true;

    private Button aiToggleButton;
    private Button backButton;

    public AiSettingsScreen(Component title) {
        super(title);

        this.aiEnabled = ConfigClass.INSTANCE.airesponseEnabled;
    }

    @Override
    protected void init() {
        super.init();

        final int centerX = this.width / 2 + BUTTON_CENTER_OFFSET;
        final int startY = BUTTON_START_Y;
        final int spacing = BUTTON_SPACING;



        aiToggleButton = Button.builder(toggleText("AI Response Feature", aiEnabled), (btn) -> {
            aiEnabled = !aiEnabled;
            btn.setMessage(toggleText("AI Response Feature", aiEnabled));

            ConfigClass.INSTANCE.airesponseEnabled = aiEnabled;
            ConfigClass.save();
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.player != null) {
                    if (ConfigClass.INSTANCE.airesponseEnabled) {
                        mc.player.sendSystemMessage(Component.literal("§l§aAi Response Feature Enabled."));
                    } else {
                        mc.player.sendSystemMessage(Component.literal("§l§cAi Response Feature Disabled."));
                    }
                }
            } catch (Throwable t) {

                t.printStackTrace();
            }
        }).bounds(centerX - BUTTON_WIDTH / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(aiToggleButton);

        backButton = Button.builder(Component.literal("Back"), (btn) -> {
            this.minecraft.setScreen(new SettingsScreen(Component.literal("Ducky Mod")));
        }).bounds(centerX - BUTTON_WIDTH / 2, startY + spacing * 3, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(backButton);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(this.font, "AI settings", 20, 20, 0xFFFFFFFF, false);
    }

    private static Component toggleText(String label, boolean on) {
        return Component.literal(label + ": " + (on ? "ON" : "OFF"));
    }
}