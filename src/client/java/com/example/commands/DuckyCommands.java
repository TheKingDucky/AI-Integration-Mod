package com.example.commands;

import com.example.client.ModClient;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public final class DuckyCommands {
    private DuckyCommands() {}

    /** Call this from ClientInit.onInitializeClient(). */
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommands.literal("ducky")
                            .executes(ctx -> {
                                // Tell ModClient to open the settings on the next client tick (safe).
                                ModClient.requestOpenSettings();

                                // For quick debugging you can also print to the game log:
                                System.out.println("[DuckyCommands] /ducky invoked -> requested settings open");

                                return 1;
                            })
            );
        });
    }
}