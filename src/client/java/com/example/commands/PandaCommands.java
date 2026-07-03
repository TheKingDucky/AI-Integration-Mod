package com.example.commands;

import com.example.ClientInit;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class PandaCommands {

    private PandaCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommands.literal("pandaon")
                            .executes(ctx -> {
                                FabricClientCommandSource src = ctx.getSource();
                                ClientInit.setPandaEnabled(true);
                                if (src.getPlayer() != null) {
                                    src.getPlayer().sendSystemMessage(Component.literal("§l§aPanda tracking enabled."));
                                } else {
                                    Minecraft.getInstance().player.sendSystemMessage(Component.literal("§l§aPanda tracking enabled."));
                                }
                                return 1;
                            })
            );

            dispatcher.register(
                    ClientCommands.literal("pandaoff")
                            .executes(ctx -> {
                                FabricClientCommandSource src = ctx.getSource();
                                ClientInit.setPandaEnabled(false);
                                if (src.getPlayer() != null) {
                                    src.getPlayer().sendSystemMessage(Component.literal("§l§cPanda tracking disabled."));
                                } else {
                                    Minecraft.getInstance().player.sendSystemMessage(Component.literal("§l§cPanda tracking disabled."));
                                }
                                return 1;
                            })
            );
        });
    }
}