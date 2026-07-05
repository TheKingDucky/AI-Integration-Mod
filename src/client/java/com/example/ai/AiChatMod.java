package com.example.ai;

import com.example.ConfigClass;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class AiChatMod implements ClientModInitializer {

    public static AiChatMod INSTANCE;

    // le configuracion
    private static final String PREFIX = "?gpt ";
    private static final long COOLDOWN_MS = 5000; // 5 seconds between requests
    private static final String MODEL = "gemini-2.5-flash";

    // cap of model response relayed
    private static final int MAX_TOTAL_REPLY_LENGTH = 1000;

    // Minecraft's vanilla per-message character limit with a little safety margin
    private static final int MAX_MESSAGE_LENGTH = 250;


    private static final long SPLIT_MESSAGE_DELAY_MS = 700;

    private String apiKey;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private long lastUseMillis = 0L;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        loadConfig();


        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            String triggeredBy = sender != null ? sender.toString() : "unknown";
            checkForTrigger(message, triggeredBy);
        });

        //game/system message because chat detection is WEIRD
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                checkForTrigger(message, "system/game message");
            }
        });
    }

    private void checkForTrigger(Component message, String triggeredBy) {
        if (!ConfigClass.INSTANCE.airesponseEnabled) {
            return;
        }

        String content = message.getString();
        if (content == null) {
            return;
        }


        int triggerIndex = content.indexOf(PREFIX);
        if (triggerIndex < 0) {
            return;
        }

        String prompt = content.substring(triggerIndex + PREFIX.length()).trim();
        String replyPrefix = determineReplyPrefix(content);

        System.out.println("[AiChatMod] Triggered by " + triggeredBy + " with prompt: " + prompt);

        handleTrigger(prompt, replyPrefix);
    }


    private String determineReplyPrefix(String content) {
        if (content.contains("§2Guild >")) {
            return "/gc ";
        } else if (content.contains("§9Party §8>")) {
            return "/pc ";
        } else if (content.contains("From")) {
            return "/r ";
        } else if (content.contains("Co-op >")) {
            return "/cc ";
        } else {
            return "";
        }
    }


    public void handleGptCommand(String prompt) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }

        if (prompt == null || prompt.isBlank()) {
            player.sendSystemMessage(
                    Component.literal("Usage: /ducky ai gpt <your question>").withStyle(ChatFormatting.RED)
            );
            return;
        }

        if (isOnCooldown()) {
            player.sendSystemMessage(
                    Component.literal("Slow down! Wait a few seconds before asking again.").withStyle(ChatFormatting.RED)
            );
            return;
        }

        if (apiKey == null || apiKey.isBlank()) {
            player.sendSystemMessage(
                    Component.literal("AI chat isn't configured — missing API key.").withStyle(ChatFormatting.RED)
            );
            return;
        }

        CompletableFuture
                .supplyAsync(() -> queryGemini(prompt), executor)
                .thenAccept(response -> client.execute(() -> {
                    LocalPlayer p = client.player;
                    if (p != null) {
                        p.sendSystemMessage(
                                Component.literal("[AI] " + response).withStyle(ChatFormatting.AQUA)
                        );
                    }
                }))
                .exceptionally(ex -> {
                    client.execute(() -> {
                        LocalPlayer p = client.player;
                        if (p != null) {
                            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                            p.sendSystemMessage(
                                    Component.literal("[AI] Error: " + cause.getMessage()).withStyle(ChatFormatting.RED)
                            );
                        }
                    });
                    return null;
                });
    }

    private void handleTrigger(String prompt, String replyPrefix) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }

        if (prompt.isEmpty()) {
            player.sendSystemMessage(
                    Component.literal("Usage: ?gpt <your question>").withStyle(ChatFormatting.RED)
            );
            return;
        }

        if (isOnCooldown()) {
            LocalPlayer p = client.player;
            if (p != null) {
                // Sent into the same channel the trigger came from (replyPrefix), so
                // whoever's waiting can actually see why nothing happened yet — this one
                // is public/broadcast, unlike the usage/config messages below.
                p.connection.sendChat(replyPrefix + "on cooldown, try again in a few seconds");
            }
            return;
        }

        if (apiKey == null || apiKey.isBlank()) {
            player.sendSystemMessage(
                    Component.literal("AI chat isn't configured — missing API key.").withStyle(ChatFormatting.RED)
            );
            return;
        }

        handlePrompt(prompt, replyPrefix);
    }

    private boolean isOnCooldown() {
        long now = System.currentTimeMillis();
        if (now - lastUseMillis < COOLDOWN_MS) {
            return true;
        }
        lastUseMillis = now;
        return false;
    }

    private void handlePrompt(String prompt, String replyPrefix) {
        CompletableFuture
                .supplyAsync(() -> queryGemini(prompt), executor)
                .thenAccept(response -> sendAiReplySplit(response, replyPrefix))
                .exceptionally(ex -> {
                    Minecraft client = Minecraft.getInstance();
                    client.execute(() -> {
                        LocalPlayer p = client.player;
                        if (p != null) {
                            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                            p.sendSystemMessage(
                                    Component.literal("[AI] Error: " + cause.getMessage()).withStyle(ChatFormatting.RED)
                            );
                        }
                    });
                    return null;
                });
    }


    private void sendAiReplySplit(String response, String replyPrefix) {
        if (response == null || response.isBlank()) {
            return;
        }

        String text = response;

        // Extra loop guard: if the model's own reply happens to start with the trigger
        // prefix, break it so it can't re-trigger this (or another) bot on the server.
        if (text.startsWith(PREFIX) || text.startsWith("?gpt")) {
            text = "AI: " + text;
        }

        int perMessageBudget = MAX_MESSAGE_LENGTH - replyPrefix.length();
        List<String> chunks = splitIntoChunks(text, perMessageBudget);

        sendChunksSequentially(chunks, replyPrefix, 0);
    }


    private List<String> splitIntoChunks(String text, int maxLen) {
        List<String> chunks = new ArrayList<>();
        if (maxLen <= 0) {
            maxLen = 1;
        }

        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            while (word.length() > maxLen) {
                if (current.length() > 0) {
                    chunks.add(current.toString());
                    current.setLength(0);
                }
                chunks.add(word.substring(0, maxLen));
                word = word.substring(maxLen);
            }

            if (current.length() == 0) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= maxLen) {
                current.append(' ').append(word);
            } else {
                chunks.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }

        if (current.length() > 0) {
            chunks.add(current.toString());
        }

        return chunks;
    }


    private void sendChunksSequentially(List<String> chunks, String replyPrefix, int index) {
        if (index >= chunks.size()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            LocalPlayer p = client.player;
            if (p != null) {
                p.connection.sendChat(replyPrefix + chunks.get(index));
            }
        });

        if (index + 1 < chunks.size()) {
            scheduler.schedule(
                    () -> sendChunksSequentially(chunks, replyPrefix, index + 1),
                    SPLIT_MESSAGE_DELAY_MS,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    private static String jsonEscape(String s) {
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }

    //says what error is
    private String queryGemini(String prompt) {
        try {
            String escapedPrompt = jsonEscape(prompt);
            String escapedSystemInstruction = jsonEscape(
                    ConfigClass.INSTANCE.systemInstructionPart1 + ConfigClass.INSTANCE.systemInstructionPart2 + ConfigClass.INSTANCE.systemInstructionPart3
            );

            String body = "{"
                    + "\"system_instruction\":{\"parts\":[{\"text\":\"" + escapedSystemInstruction + "\"}]},"
                    + "\"contents\":[{\"parts\":[{\"text\":\"" + escapedPrompt + "\"}]}]"
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            "https://generativelanguage.googleapis.com/v1beta/models/"
                                    + MODEL + ":generateContent"
                    ))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                // 429 = rate limited / out of quota (tokens exhausted), 400/403 = bad key or request
                throw new RuntimeException(
                        "Gemini API returned status " + response.statusCode()
                                + " — check your API key/quota. Body: "
                                + response.body().substring(0, Math.min(200, response.body().length()))
                );
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String text = json.getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString();

            text = text.replace("\n", " ").trim();

            // Overall cap so one answer can't turn into a huge wall of split messages.
            // The splitting itself (into per-message chunks) happens later, ins endAiReplySplit — this only bounds the total length before that.
            if (text.length() > MAX_TOTAL_REPLY_LENGTH) {
                text = text.substring(0, MAX_TOTAL_REPLY_LENGTH) + "...";
            }
            return text;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("failed to reach AI service (" + e.getMessage() + ")", e);
        }
    }


     //Load API key from config/aichat.properties so it's never hardcoded (dont need people stealing my api key on release)

    private void loadConfig() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("aichat.properties");
        Properties props = new Properties();

        try {
            if (!Files.exists(configPath)) {
                Files.writeString(configPath, "gemini_api_key=PUT_YOUR_KEY_HERE\n");
            }
            try (InputStream in = Files.newInputStream(configPath)) {
                props.load(in);
            }
            apiKey = props.getProperty("gemini_api_key", "").trim();
            if (apiKey.equals("PUT_YOUR_KEY_HERE")) {
                apiKey = null;
            }

            System.out.println("[AiChatMod] Config path: " + configPath.toAbsolutePath());
            System.out.println("[AiChatMod] API key loaded: " + (apiKey != null && !apiKey.isBlank()));
        } catch (IOException e) {
            apiKey = null;
        }
    }
}