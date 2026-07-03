package com.example.client.settingsscreens;

import com.example.ConfigClass;
import com.example.client.SettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ChatSettingsScreen extends Screen {
    private static int BUTTON_WIDTH = 220;
    private static int BUTTON_HEIGHT = 20;
    private static int BUTTON_SPACING = 26;
    private static int BUTTON_CENTER_OFFSET = 0;
    private static int BUTTON_START_Y = 80;

    // renamed from otherEnabled -> chatEnabled
    private boolean chatEnabled = true;
    // renamed UI fields
    private Button chatToggleButton;
    private Button backButton;

    public ChatSettingsScreen(Component title) {
        super(title);
        // initialize with current global state
        this.chatEnabled = ConfigClass.INSTANCE.chatEnabled;
    }

    @Override
    protected void init() {
        super.init();

        final int centerX = this.width / 2 + BUTTON_CENTER_OFFSET;
        final int startY = BUTTON_START_Y;
        final int spacing = BUTTON_SPACING;

        // toggle now controls ChatResponder.enabled
        chatToggleButton = Button.builder(toggleText("Chatting Feature", chatEnabled), (btn) -> {
            chatEnabled = !chatEnabled;
            btn.setMessage(toggleText("Chatting Feature", chatEnabled));
            // wire chatting feature to your ChatResponder
            ConfigClass.INSTANCE.chatEnabled = chatEnabled;
            ConfigClass.save();
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.player != null) {
                    if (ConfigClass.INSTANCE.chatEnabled) {
                        mc.player.sendSystemMessage(Component.literal("§l§aChatting Feature Enabled."));
                    } else {
                        mc.player.sendSystemMessage(Component.literal("§l§cChatting Feature Disabled."));
                    }
                }
            } catch (Throwable t) {
                // defensive: don't crash the UI if messaging fails
                t.printStackTrace();
            }
        }).bounds(centerX - BUTTON_WIDTH / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(chatToggleButton);

        backButton = Button.builder(Component.literal("Back"), (btn) -> {
            this.minecraft.setScreen(new SettingsScreen(Component.literal("Ducky Mod")));
        }).bounds(centerX - BUTTON_WIDTH / 2, startY + spacing * 3, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(backButton);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(this.font, "Chatting settings", 20, 20, 0xFFFFFFFF, false);
    }

    private static Component toggleText(String label, boolean on) {
        return Component.literal(label + ": " + (on ? "ON" : "OFF"));
    }
}