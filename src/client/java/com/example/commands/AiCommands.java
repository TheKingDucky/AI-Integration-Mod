package com.example.commands;

import com.example.ConfigClass;
import com.example.ai.AiChatMod;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;


public class AiCommands {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("ducky")
                            .then(ClientCommands.literal("help")
                                    .executes(context -> {
                                        context.getSource().sendFeedback(
                                                // Edit this line to whatever you want /ducky help to show.
                                                Component.literal("go to the github readme i swear there is everything there if not contact me discord thekingducky. https://github.com/TheKingDucky/AI-Integration-Mod")
                                                        .withStyle(ChatFormatting.WHITE)
                                        );
                                        return 1;
                                    }))
                    .then(ClientCommands.literal("ai")
                            .then(ClientCommands.literal("maxcharacters")
                                    .then(ClientCommands.literal("preview")
                                            .executes(context -> {
                                                context.getSource().sendFeedback(
                                                        Component.literal("Max AI reply length is currently: ")
                                                                .withStyle(ChatFormatting.GOLD)
                                                                .append(Component.literal(String.valueOf(ConfigClass.INSTANCE.maxCharacters))
                                                                        .withStyle(ChatFormatting.WHITE))
                                                );
                                                return 1;
                                            }))
                                    .then(ClientCommands.argument("amount", IntegerArgumentType.integer(1))
                                            .executes(context -> {
                                                int amount = IntegerArgumentType.getInteger(context, "amount");
                                                ConfigClass.INSTANCE.maxCharacters = amount;
                                                ConfigClass.save();

                                                context.getSource().sendFeedback(
                                                        Component.literal("Max AI reply length set to " + amount + " characters.")
                                                                .withStyle(ChatFormatting.GREEN)
                                                );
                                                return 1;
                                            })))
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