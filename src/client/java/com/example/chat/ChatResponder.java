package com.example.chat;

import com.example.ConfigClass;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;

public class ChatResponder {

    private static final String REPLY_TEXT =
            "TheKingDucky is a complex amazing person who's personality shines as bright as the sun. From his elite dungeon skills to his nonchalant personality, he is truly the goal in life that many people strive to aim for.";

    public static void register() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {

            if (!ConfigClass.INSTANCE.chatEnabled) return;

            String text = message.getString();
            String lower = text.toLowerCase();

            if (lower.contains("ducky") && !lower.contains("thekingducky")) {
                Minecraft mc = Minecraft.getInstance();

                if (text.contains("§2Guild >")) {
                    mc.player.connection.sendChat("/gc " + REPLY_TEXT);
                }
                else if (text.contains("§9Party §8>")) {
                    mc.player.connection.sendChat("/pc " + REPLY_TEXT);
                }
                else if (text.contains("From")) {
                    mc.player.connection.sendChat("/r " + REPLY_TEXT);
                }
                else if (text.contains("Co-op >")) {
                    mc.player.connection.sendChat("/cc " + REPLY_TEXT);
                }
            }
        });
    }
}