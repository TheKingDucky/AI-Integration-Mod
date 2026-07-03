package com.example.client.settingsscreens;

import com.example.ConfigClass;
import com.example.client.SettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TurtleSettingsScreen extends Screen {
    private static int BUTTON_WIDTH = 220;
    private static int BUTTON_HEIGHT = 20;
    private static int BUTTON_SPACING = 26;
    private static int BUTTON_CENTER_OFFSET = 0;
    private static int BUTTON_START_Y = 80;

    //private boolean turtlesEnabled;
    //public static boolean turtleOne = false;


    private Button turtlesToggleButton;
    private Button turtleOneToggleButton;
    private Button backButton;

    public TurtleSettingsScreen(Component title) {
        super(title);
        // initialize with current global state
        ConfigClass.INSTANCE.turtleEnabled = com.example.ClientInit.isTurtleEnabled();
    }

    @Override
    protected void init() {
        super.init();

        final int centerX = this.width / 2 + BUTTON_CENTER_OFFSET;
        final int startY = BUTTON_START_Y;
        final int spacing = BUTTON_SPACING;

        turtlesToggleButton = Button.builder(toggleText("Enable Turtle Tracker", ConfigClass.INSTANCE.turtleEnabled), (btn) -> {
            ConfigClass.INSTANCE.turtleEnabled = !ConfigClass.INSTANCE.turtleEnabled;
            btn.setMessage(toggleText("Enable Turtle Tracker", ConfigClass.INSTANCE.turtleEnabled));
            // apply immediately to mod
            com.example.ClientInit.setTurtleEnabled(ConfigClass.INSTANCE.turtleEnabled);

            // send the requested chat message on toggle (use sendSystemMessage for mapping safety)
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.player != null) {
                    if (ConfigClass.INSTANCE.turtleEnabled) {
                        mc.player.sendSystemMessage(Component.literal("§l§aTurtle Tracking Enabled."));
                    } else {
                        mc.player.sendSystemMessage(Component.literal("§l§cTurtle Tracking Disabled."));
                    }
                }
            } catch (Throwable t) {
                // defensive: don't crash the UI if messaging fails
                t.printStackTrace();
            }
        }).bounds(centerX - BUTTON_WIDTH / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(turtlesToggleButton);

        turtleOneToggleButton = Button.builder(toggleText("Enable One Turtle Abandon", ConfigClass.INSTANCE.turtleOne), (btn) -> {
            ConfigClass.INSTANCE.turtleOne = !ConfigClass.INSTANCE.turtleOne;
            btn.setMessage(toggleText("Enable One Turtle Abandon", ConfigClass.INSTANCE.turtleOne));
            // apply immediately to mod
           // com.example.ClientInit.setTurtleEnabled(turtleOne);

            // send the requested chat message on toggle (use sendSystemMessage for mapping safety)
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.player != null) {
                    if (ConfigClass.INSTANCE.turtleOne) {
                        mc.player.sendSystemMessage(Component.literal("§l§aOne Turtle Abandon Feature Enabled."));
                    } else {
                        mc.player.sendSystemMessage(Component.literal("§l§cOne Turtle Abandon Feature Disabled."));
                    }
                }
            } catch (Throwable t) {
                // defensive: don't crash the UI if messaging fails
                t.printStackTrace();
            }
        }).bounds(centerX - BUTTON_WIDTH / 2, startY + spacing, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(turtleOneToggleButton);







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
        graphics.text(this.font, "Turtle settings", 20, 20, 0xFFFFFFFF, false);
    }

    private static Component toggleText(String label, boolean on) {
        return Component.literal(label + ": " + (on ? "ON" : "OFF"));
    }
}
