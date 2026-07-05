
package com.example;

import com.example.ai.AiChatMod;
import com.example.commands.AiCommands;
import net.fabricmc.api.ClientModInitializer;


public class ClientInit implements ClientModInitializer {

	@Override
	public void onInitializeClient() {

		com.example.client.ModClient.register();
		AiCommands.register();
		ConfigClass.load();
		new AiChatMod().onInitializeClient();


		//com.example.chat.ChatResponder.enabled = true; // optional: start enabled
	}}


