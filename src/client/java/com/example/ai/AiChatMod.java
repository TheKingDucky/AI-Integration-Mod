/*
package com.example.ai;

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
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class AiChatMod implements ClientModInitializer {

    // --- Config ---
    private static final String PREFIX = "?gpt ";
    private static final long COOLDOWN_MS = 8000; // 8 seconds between requests
    private static final String MODEL = "gemini-2.5-flash";


    private volatile boolean enabled = true;

    private String apiKey;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private long lastUseMillis = 0L;

    @Override
    public void onInitializeClient() {
        loadConfig();

        // ClientReceiveMessageEvents.CHAT fires for any chat message the client receives,
        // meaning any player on the server (not just you) can trigger it. This is a
        // listen-only event (nothing here cancels or hides the message), so the trigger
        // message displays normally to everyone, exactly like it would without this mod.
        // Player chat messages (signed, vanilla-style "<name> message").
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            String triggeredBy = sender != null ? sender.toString() : "unknown";
            checkForTrigger(message, triggeredBy);
        });

        // Game/system messages. Many servers with custom chat formatting (party chat,
        // guild chat, rank-prefixed chat like "[MVP+] Name: ...") deliver their chat
        // through the system-message channel instead of signed player chat, because the
        // custom formatting doesn't fit the vanilla chat-signing system. Without this,
        // the mod would never see chat on those servers at all.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                checkForTrigger(message, "system/game message");
            }
        });
    }

    private void checkForTrigger(Component message, String triggeredBy) {
        if (!enabled) {
            return;
        }

        String content = message.getString();
        if (content == null) {
            return;
        }

        // The received line is the full formatted chat line (e.g. "<Player495> ?gpt
        // hello", or on some servers something like "§9Party §8> §b[MVP+] Name: ?gpt
        // hello"), not just the raw text you typed. So we look for "?gpt " anywhere in
        // the line rather than requiring it at position 0.
        int triggerIndex = content.indexOf(PREFIX);
        if (triggerIndex < 0) {
            return;
        }

        String prompt = content.substring(triggerIndex + PREFIX.length()).trim();

        System.out.println("[AiChatMod] Triggered by " + triggeredBy + " with prompt: " + prompt);

        handleTrigger(prompt);
    }

    private void handleTrigger(String prompt) {
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

        handlePrompt(prompt);
    }

    private boolean isOnCooldown() {
        long now = System.currentTimeMillis();
        if (now - lastUseMillis < COOLDOWN_MS) {
            return true;
        }
        lastUseMillis = now;
        return false;
    }

    private void handlePrompt(String prompt) {
        Minecraft client = Minecraft.getInstance();

        CompletableFuture
                .supplyAsync(() -> queryGemini(prompt), executor)
                .thenAccept(response -> client.execute(() -> sendAiReply(response)))
                .exceptionally(ex -> {
                    client.execute(() -> {
                        LocalPlayer p = client.player;
                        if (p != null) {
                            p.sendSystemMessage(
                                    Component.literal("[AI] Error: " + ex.getMessage()).withStyle(ChatFormatting.RED)
                            );
                        }
                    });
                    return null;
                });
    }


    private void sendAiReply(String response) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || response == null || response.isBlank()) {
            return;
        }

        String toSend = response;

        // Extra loop guard: if the model's own reply happens to start with the trigger
        // prefix, break it so it can't re-trigger this (or another) bot on the server.
        if (toSend.startsWith(PREFIX) || toSend.startsWith("?gpt")) {
            toSend = "AI: " + toSend;
        }

        // Minecraft chat has a hard character limit per message; keep a safety margin.
        if (toSend.length() > 250) {
            toSend = toSend.substring(0, 250) + "...";
        }

        // Sending directly via the network connection rather than a LocalPlayer
        // convenience method, since that convenience method's name/existence has
        // changed across Minecraft versions. This sends the packet exactly as if
        // you had typed the text and pressed enter — it goes through the server
        // to everyone.
        p.connection.sendChat(toSend);
    }


    private String queryGemini(String prompt) {
        try {
            String escapedPrompt = prompt
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n");

            String body = "{\"contents\":[{\"parts\":[{\"text\":\"" + escapedPrompt + "\"}]}]}";

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
                return "API returned status " + response.statusCode() + " (rate limit or bad request)";
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String text = json.getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString();

            text = text.replace("\n", " ").trim();
            if (text.length() > 250) {
                text = text.substring(0, 250) + "...";
            }
            return text;

        } catch (Exception e) {
            return "failed to reach AI service (" + e.getMessage() + ")";
        }
    }


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
}*/
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
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Listens to chat messages received by the client (i.e. any message any player on the
 * server sends, including yourself) for a "?gpt <prompt>" trigger. When triggered, it
 * queries Gemini and then SENDS the reply as a real chat message from your own account,
 * visible to everyone on the server.
 *
 * IMPORTANT: unlike a purely local/client-side assistant, this means any player on the
 * server can cause your account to publicly post AI-generated text. Many servers treat
 * this kind of automated trigger -> auto-post behavior as a chat bot/macro, which can
 * violate server rules even though no packets are being spoofed or protocol is being
 * abused. Consider restricting who can trigger this (e.g. only your own UUID) before
 * using it on a server you don't control.
 *
 * Written for Minecraft 26.1.2 using Mojang's official mappings, Fabric API.
 */
public class AiChatMod implements ClientModInitializer {

    // --- Config ---
    private static final String PREFIX = "?gpt ";
    private static final long COOLDOWN_MS = 8000; // 8 seconds between requests
    private static final String MODEL = "gemini-2.5-flash";

    /**
     * Master on/off switch for the whole feature. When false, the chat listener still
     * fires (Fabric always calls registered listeners) but exits immediately, so no
     * detection, no API calls, and no outgoing chat happen. Flip this from wherever you
     * end up wiring up your own toggle (command, keybind, config screen, etc.).
     */
    //private volatile boolean enabled = true;

    private String apiKey;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private long lastUseMillis = 0L;

    @Override
    public void onInitializeClient() {
        loadConfig();

        // ClientReceiveMessageEvents.CHAT fires for any chat message the client receives,
        // meaning any player on the server (not just you) can trigger it. This is a
        // listen-only event (nothing here cancels or hides the message), so the trigger
        // message displays normally to everyone, exactly like it would without this mod.
        // Player chat messages (signed, vanilla-style "<name> message").
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            String triggeredBy = sender != null ? sender.toString() : "unknown";
            checkForTrigger(message, triggeredBy);
        });

        // Game/system messages. Many servers with custom chat formatting (party chat,
        // guild chat, rank-prefixed chat like "[MVP+] Name: ...") deliver their chat
        // through the system-message channel instead of signed player chat, because the
        // custom formatting doesn't fit the vanilla chat-signing system. Without this,
        // the mod would never see chat on those servers at all.
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

        // The received line is the full formatted chat line (e.g. "<Player495> ?gpt
        // hello", or on some servers something like "§9Party §8> §b[MVP+] Name: ?gpt
        // hello"), not just the raw text you typed. So we look for "?gpt " anywhere in
        // the line rather than requiring it at position 0.
        int triggerIndex = content.indexOf(PREFIX);
        if (triggerIndex < 0) {
            return;
        }

        String prompt = content.substring(triggerIndex + PREFIX.length()).trim();
        String replyPrefix = determineReplyPrefix(content);

        System.out.println("[AiChatMod] Triggered by " + triggeredBy + " with prompt: " + prompt);

        handleTrigger(prompt, replyPrefix);
    }

    /**
     * Figures out which channel the trigger came from (based on the same formatting
     * patterns your other feature already uses) and returns the command prefix needed
     * to reply into that same channel. Empty string means "just send to normal/public
     * chat" (no slash command needed).
     */
    private String determineReplyPrefix(String content) {
        if (content.contains("§2Guild >")) {
            return "/gc ";
        } else if (content.contains("§9Party §8>")) {
            return "/pc ";
        } else if (content.contains("From")) {
            // Hypixel-style private message. Note: /r always replies to whoever most
            // recently whispered you — if another whisper arrives in the few hundred ms
            // between the trigger and the AI reply coming back, this could reply to the
            // wrong person. Low risk given the cooldown, but worth knowing.
            return "/r ";
        } else if (content.contains("Co-op >")) {
            return "/cc ";
        } else {
            return "";
        }
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
        Minecraft client = Minecraft.getInstance();

        CompletableFuture
                .supplyAsync(() -> queryGemini(prompt), executor)
                .thenAccept(response -> client.execute(() -> sendAiReply(response, replyPrefix)))
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

    /**
     * Sends the AI's response as a real chat message from the local player's account,
     * into whichever channel the trigger came from (replyPrefix), visible to whoever can
     * see that channel — not a local-only system message.
     * Must be called on the client thread.
     */
    private void sendAiReply(String response, String replyPrefix) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || response == null || response.isBlank()) {
            return;
        }

        String toSend = response;

        // Extra loop guard: if the model's own reply happens to start with the trigger
        // prefix, break it so it can't re-trigger this (or another) bot on the server.
        if (toSend.startsWith(PREFIX) || toSend.startsWith("?gpt")) {
            toSend = "AI: " + toSend;
        }

        // Minecraft chat has a hard character limit per message; keep a safety margin,
        // and leave room for the reply-channel command prefix (e.g. "/gc ") on top.
        int budget = 250 - replyPrefix.length();
        if (toSend.length() > budget) {
            toSend = toSend.substring(0, Math.max(0, budget - 3)) + "...";
        }

        // Sending directly via the network connection rather than a LocalPlayer
        // convenience method, since that convenience method's name/existence has
        // changed across Minecraft versions. This sends the packet exactly as if
        // you had typed the text and pressed enter — it goes through the server
        // to everyone who can see that channel.
        p.connection.sendChat(replyPrefix + toSend);
    }

    /**
     * Blocking network call — always run this off the render/client thread.
     * Throws on any failure (bad status code, network error, parse error) instead of
     * returning descriptive text, so a failure is never mistaken for a real AI answer
     * and broadcast to a public/guild/party channel. Failures are caught by the
     * .exceptionally() handler in handlePrompt and shown only to you, locally.
     */
    private String queryGemini(String prompt) {
        try {
            String escapedPrompt = prompt
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n");

            String body = "{\"contents\":[{\"parts\":[{\"text\":\"" + escapedPrompt + "\"}]}]}";

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
                // 429 = rate limited / out of quota (tokens exhausted), 400/403 = bad key
                // or request. Either way, this must not be treated as a real answer.
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
            if (text.length() > 250) {
                text = text.substring(0, 250) + "...";
            }
            return text;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("failed to reach AI service (" + e.getMessage() + ")", e);
        }
    }

    /**
     * Loads the API key from config/aichat.properties so it's never hardcoded
     * or committed alongside the mod.
     */
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