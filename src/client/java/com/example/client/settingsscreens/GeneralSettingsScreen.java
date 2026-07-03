package com.example.client.settingsscreens;

import com.example.ConfigClass;
import com.example.client.SettingsScreen;
import com.example.general.SameLobbyDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class GeneralSettingsScreen extends Screen {
    private static int BUTTON_WIDTH = 220;
    private static int BUTTON_HEIGHT = 20;
    private static int BUTTON_SPACING = 26;
    private static int BUTTON_CENTER_OFFSET = 0;
    private static int BUTTON_START_Y = 80;

    // renamed from otherEnabled -> chatEnabled
    //public static boolean ChatMessageSenderEnabled = true;
   // public static boolean WarpCooldownEnabled = true;
    //private boolean SameLobbyDetectorEnabled;


    // renamed UI fields
    private Button ChatMessageSendingToggleButton;
    private Button backButton;
    private Button WarpCooldownButton;
    private Button SameLobbyToggleButton;



    public GeneralSettingsScreen(Component title) {
        super(title);
        // initialize with current global state
        // this.chatEnabled = com.example.chat.ChatResponder.enabled;
    }

    @Override
    protected void init() {
        super.init();

        final int centerX = this.width / 2 + BUTTON_CENTER_OFFSET;
        final int startY = BUTTON_START_Y;
        final int spacing = BUTTON_SPACING;

        // toggle now controls ChatResponder.enabled
        ChatMessageSendingToggleButton = Button.builder(toggleText("Chat Message Sending feature", ConfigClass.INSTANCE.chatMessageSenderEnabled), (btn) -> {
            ConfigClass.INSTANCE.chatMessageSenderEnabled = !ConfigClass.INSTANCE.chatMessageSenderEnabled;
            ConfigClass.save();
            btn.setMessage(toggleText("Chat Message Sending feature", ConfigClass.INSTANCE.chatMessageSenderEnabled));
            // wire chatting feature to your ChatResponder
            //com.example.chat.ChatResponder.enabled = warpEnabled;
            System.out.println("Chat Message Sending feature is:" + ConfigClass.INSTANCE.chatMessageSenderEnabled + "");
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.player != null) {
                    if (ConfigClass.INSTANCE.chatMessageSenderEnabled) {
                        mc.player.sendSystemMessage(Component.literal("§l§aChat Message Sender enabled."));
                    } else {
                        mc.player.sendSystemMessage(Component.literal("§l§cChat Message Sender disabled."));
                    }
                }
            } catch (Throwable t) {
                // defensive: don't crash the UI if messaging fails
                t.printStackTrace();
            }
        }).bounds(centerX - BUTTON_WIDTH / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(ChatMessageSendingToggleButton);


        WarpCooldownButton = Button.builder(toggleText("Warp Cooldown feature", ConfigClass.INSTANCE.warpCooldownEnabled), (btn) -> {
            ConfigClass.INSTANCE.warpCooldownEnabled = !ConfigClass.INSTANCE.warpCooldownEnabled;
            ConfigClass.save();
            btn.setMessage(toggleText("Warp Cooldown feature", ConfigClass.INSTANCE.warpCooldownEnabled));
            // wire chatting feature to your ChatResponder
            //com.example.general.CooldownWarpDetector.enabled = WarpCooldownEnabled;
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.player != null) {
                    if (ConfigClass.INSTANCE.warpCooldownEnabled) {
                        mc.player.sendSystemMessage(Component.literal("§l§aWarp Cooldown Display Enabled."));
                    } else {
                        mc.player.sendSystemMessage(Component.literal("§l§cWarp Cooldown Display Disabled."));
                    }
                }
            } catch (Throwable t) {
                // defensive: don't crash the UI if messaging fails
                t.printStackTrace();
            }
        }).bounds(centerX - BUTTON_WIDTH / 2, startY + spacing, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(WarpCooldownButton);



        SameLobbyToggleButton = Button.builder(toggleText("Enable Same Lobby Detector", ConfigClass.INSTANCE.sameLobbyDetectorEnabled), (btn) -> {
            boolean newValue = !ConfigClass.INSTANCE.sameLobbyDetectorEnabled;
            ConfigClass.INSTANCE.sameLobbyDetectorEnabled = newValue;
            SameLobbyDetector.setEnabled(newValue);
            ConfigClass.save();
            btn.setMessage(toggleText("Enable Same Lobby Detector", ConfigClass.INSTANCE.sameLobbyDetectorEnabled));
            // apply immediately to detector
            SameLobbyDetector.setEnabled(ConfigClass.INSTANCE.sameLobbyDetectorEnabled);

            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.player != null) {
                    if (ConfigClass.INSTANCE.sameLobbyDetectorEnabled) {
                        mc.player.sendSystemMessage(Component.literal("§l§aDuplicate Lobby Warp Detector Enabled."));
                    } else {
                        mc.player.sendSystemMessage(Component.literal("§l§cDuplicate Lobby Warp Detector Disabled."));
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }).bounds(centerX - BUTTON_WIDTH / 2, startY + spacing + spacing, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(SameLobbyToggleButton);










        backButton = Button.builder(Component.literal("Back"), (btn) -> {
            this.minecraft.setScreen(new SettingsScreen(Component.literal("Ducky Mod")));
        }).bounds(centerX - BUTTON_WIDTH / 2, startY + spacing * 4, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(backButton);

















    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(this.font, "General settings", 20, 20, 0xFFFFFFFF, false);
    }

    private static Component toggleText(String label, boolean on) {
        return Component.literal(label + ": " + (on ? "ON" : "OFF"));
    }
}