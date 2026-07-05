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

/**
 * Listens to chat messages received by the client (i.e. any message any player on the
 * server sends, including yourself) for a "?gpt <prompt>" trigger. When triggered, it
 * queries Gemini and then SENDS the reply as a real chat message from your own account,
 * visible to everyone on the server. If the reply is too long for one Minecraft chat
 * message, it's split across multiple messages instead of being cut off.
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

    // Lets other classes (like your command handler) reach this running instance,
    // the same way ConfigClass.INSTANCE works. Set once, in onInitializeClient().
    public static AiChatMod INSTANCE;

    // --- Config ---
    private static final String PREFIX = "?gpt ";
    private static final long COOLDOWN_MS = 8000; // 8 seconds between requests
    private static final String MODEL = "gemini-2.5-flash";

    // Overall cap on how much of the model's answer we'll ever relay, regardless of how
    // many chat messages that takes. Keeps one huge answer from turning into a wall of
    // 15+ messages. Raise/lower this to trade off completeness vs. chat spam.
    private static final int MAX_TOTAL_REPLY_LENGTH = 600;

    // Minecraft's vanilla per-message character limit (with a little safety margin).
    private static final int MAX_MESSAGE_LENGTH = 250;

    // Persistent behavior instructions sent with every request — this is where you
    // control tone, personality, formatting rules, length constraints, etc. without
    // needing to repeat any of it in every prompt.
    //
    // Split into three separately-settable pieces so a command like
    // "/duckymod airesponse systeminstruction 2 <text>" can target just one piece
    // without needing to know or retype the other two. They're just concatenated
    // together, in order, when the request is actually built in queryGemini().
    //
    // NOT final/private: meant to be reassigned at runtime. "volatile" guarantees a
    // change made on one thread (e.g. your command handler, on the client thread) is
    // immediately visible to other threads (e.g. the background network thread running
    // queryGemini) rather than a stale cached copy. Changing any piece takes effect on
    // the very next ?gpt trigger — no restart, no reload, nothing else needed.
    public static volatile String systemInstructionPart1 =
            "Keep responses concise, ideally under 500 characters, since they need to ";
    public static volatile String systemInstructionPart2 =
            "fit into Minecraft chat messages. Do not use markdown formatting, ";
    public static volatile String systemInstructionPart3 =
            "asterisks, or emojis, since Minecraft chat can't render them.";

    // Delay between consecutive split messages so it doesn't look/behave like spam and
    // doesn't trip server-side anti-spam throttling.
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

    /**
     * Entry point for "/ducky ai gpt <prompt>". Unlike the chat-trigger path, this
     * never sends anything over the network — the answer is shown only to you via
     * sendSystemMessage, the same local-only mechanism used for error/usage messages
     * elsewhere in this class. Shares the same cooldown as the chat trigger, since both
     * consume the same API quota.
     */
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

    /**
     * Splits the AI's response into as many chat-length chunks as needed (breaking on
     * word boundaries, never mid-word) and sends them as separate real chat messages,
     * spaced out slightly so it doesn't look/behave like spam. Can be called from any
     * thread — each send is individually marshaled onto the client thread.
     */
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

    /**
     * Breaks text into pieces no longer than maxLen, splitting only at spaces so words
     * are never cut in half. A single word longer than maxLen (rare) gets hard-split as
     * a fallback so it can never produce an unbounded chunk.
     */
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

    /**
     * Sends one chunk, then schedules the next one after a short delay, until the list
     * is exhausted. Each send hops onto the client thread via client.execute, since
     * touching the network connection/player should happen there.
     */
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

    /**
     * Blocking network call — always run this off the render/client thread.
     * Throws on any failure (bad status code, network error, parse error) instead of
     * returning descriptive text, so a failure is never mistaken for a real AI answer
     * and broadcast to a public/guild/party channel. Failures are caught by the
     * .exceptionally() handler in handlePrompt and shown only to you, locally.
     */
    private String queryGemini(String prompt) {
        try {
            String escapedPrompt = jsonEscape(prompt);
            String escapedSystemInstruction = jsonEscape(
                    systemInstructionPart1 + systemInstructionPart2 + systemInstructionPart3
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

            // Overall cap so one answer can't turn into a huge wall of split messages.
            // The splitting itself (into per-message chunks) happens later, in
            // sendAiReplySplit — this only bounds the total length before that.
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