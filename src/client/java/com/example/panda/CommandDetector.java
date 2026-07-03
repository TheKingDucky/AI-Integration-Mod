package com.example.panda;

import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
//import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
//import net.minecraft.text.Text;

public final class CommandDetector {
    private CommandDetector() {}

    public static void register() {
        // Called when the client is about to send a command (no leading slash in the string).
        ClientSendMessageEvents.COMMAND.register(command -> {
            if (command == null) return;

            // Normalize: trim + collapse repeated spaces if you like; here we just trim and lowercase
            String normalized = command.trim().toLowerCase();

            // exact command "warp murk" (no leading slash in this callback)
            if (normalized.equals("warp murk") || normalized.startsWith("warp murk ")) {
                Minecraft mc = Minecraft.getInstance();
                if (mc == null || mc.player == null) return;

                // --- safe: client-only notification (server does NOT see this) ---
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("Warping to Murk"));

                // --- careful: if you *do* want to send a message to the server as the player ---
                // (UNCOMMENT the following line AT YOUR OWN RISK; this will send a real chat packet)
                // mc.getNetworkHandler().sendPacket(new ChatMessageC2SPacket("hello from client"));

                // If you want to do something else (open a GUI, toggle state, etc.) do it here.
            }
        });
    }
}