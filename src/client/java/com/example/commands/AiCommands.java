package com.example.commands;

import com.example.ConfigClass;
import com.example.ai.AiChatMod;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Registers "/ducky ai systeminstruction ..." as a client-side command.
 *
 * This is purely local — like the "preview" feedback, none of it is sent to the server
 * or visible to other players. It just edits the fields in ConfigClass that AiChatMod
 * reads from on every request, and saves them to disk immediately so they survive a
 * restart.
 *
 * Usage:
 *   /ducky ai systeminstruction 1 <text>   -> sets ConfigClass.INSTANCE.systemInstructionPart1
 *   /ducky ai systeminstruction 2 <text>   -> sets ConfigClass.INSTANCE.systemInstructionPart2
 *   /ducky ai systeminstruction 3 <text>   -> sets ConfigClass.INSTANCE.systemInstructionPart3
 *   /ducky ai systeminstruction 1 clear    -> blanks part 1 (likewise for 2, 3)
 *   /ducky ai systeminstruction preview    -> displays all three parts locally
 *
 * Call AiCommands.register() once from your client entrypoint's onInitializeClient(),
 * the same way you wired up AiChatMod.
 */
public class AiCommands {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("ducky")
                    .then(ClientCommands.literal("ai")
                            .then(ClientCommands.literal("gpt")
                                    .then(ClientCommands.argument("prompt", StringArgumentType.greedyString())
                                            .executes(context -> {
                                                String prompt = StringArgumentType.getString(context, "prompt");
                                                AiChatMod.INSTANCE.handleGptCommand(prompt);
                                                return 1;
                                            })))
                            .then(ClientCommands.literal("systeminstruction")
                                    .then(ClientCommands.literal("preview")
                                            .executes(context -> {
                                                context.getSource().sendFeedback(
                                                        Component.literal("System instruction part 1: ")
                                                                .withStyle(ChatFormatting.GOLD)
                                                                .append(Component.literal(ConfigClass.INSTANCE.systemInstructionPart1)
                                                                        .withStyle(ChatFormatting.WHITE))
                                                );
                                                context.getSource().sendFeedback(
                                                        Component.literal("System instruction part 2: ")
                                                                .withStyle(ChatFormatting.GOLD)
                                                                .append(Component.literal(ConfigClass.INSTANCE.systemInstructionPart2)
                                                                        .withStyle(ChatFormatting.WHITE))
                                                );
                                                context.getSource().sendFeedback(
                                                        Component.literal("System instruction part 3: ")
                                                                .withStyle(ChatFormatting.GOLD)
                                                                .append(Component.literal(ConfigClass.INSTANCE.systemInstructionPart3)
                                                                        .withStyle(ChatFormatting.WHITE))
                                                );
                                                return 1;
                                            }))
                                    .then(ClientCommands.argument("part", IntegerArgumentType.integer(1, 3))
                                            .suggests((context, builder) -> {
                                                builder.suggest(1);
                                                builder.suggest(2);
                                                builder.suggest(3);
                                                return builder.buildFuture();
                                            })
                                            .then(ClientCommands.literal("clear")
                                                    .executes(context -> {
                                                        int part = IntegerArgumentType.getInteger(context, "part");

                                                        if (part == 1) {
                                                            ConfigClass.INSTANCE.systemInstructionPart1 = "";
                                                        } else if (part == 2) {
                                                            ConfigClass.INSTANCE.systemInstructionPart2 = "";
                                                        } else {
                                                            ConfigClass.INSTANCE.systemInstructionPart3 = "";
                                                        }
                                                        ConfigClass.save();

                                                        context.getSource().sendFeedback(
                                                                Component.literal("System instruction part " + part + " cleared.")
                                                                        .withStyle(ChatFormatting.GREEN)
                                                        );
                                                        return 1;
                                                    }))
                                            .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                                                    .executes(context -> {
                                                        int part = IntegerArgumentType.getInteger(context, "part");
                                                        String text = StringArgumentType.getString(context, "text");

                                                        if (part == 1) {
                                                            ConfigClass.INSTANCE.systemInstructionPart1 = text;
                                                        } else if (part == 2) {
                                                            ConfigClass.INSTANCE.systemInstructionPart2 = text;
                                                        } else {
                                                            ConfigClass.INSTANCE.systemInstructionPart3 = text;
                                                        }
                                                        ConfigClass.save();

                                                        context.getSource().sendFeedback(
                                                                Component.literal("System instruction part " + part + " updated.")
                                                                        .withStyle(ChatFormatting.GREEN)
                                                        );
                                                        return 1;
                                                    }))))));
        });
    }
}