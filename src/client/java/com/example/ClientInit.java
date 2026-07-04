
package com.example;

import com.example.ai.AiChatMod;
import com.example.commands.AiCommands;
import com.example.commands.DuckyCommands;
import com.example.commands.PandaCommands;
import com.example.general.CooldownWarpDetector;
import com.example.general.SameLobbyDetector;
import com.example.panda.CommandDetector;
import com.example.panda.PandaTracker;
//import com.example.turtle.TurtleCoordsKeybind;
import com.example.turtle.TurtleTracker;
import net.fabricmc.api.ClientModInitializer;
import com.example.fishing.Autofishing;

public class ClientInit implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		// register client commands (your existing PandaCommands)
		PandaCommands.register();
		PandaTracker.register();
		TurtleTracker.register();
		Autofishing.register();
		com.example.client.ModClient.register();
		DuckyCommands.register();
		AiCommands.register();
		com.example.chat.ChatResponder.register();
		CommandDetector.register();
		SameLobbyDetector.register();
		CooldownWarpDetector.register();
		//TurtleCoordsKeybind.register();
		ConfigClass.load();
		SameLobbyDetector.setEnabled(ConfigClass.INSTANCE.sameLobbyDetectorEnabled);

		new AiChatMod().onInitializeClient();


		//com.example.chat.ChatResponder.enabled = true; // optional: start enabled
	}

	// preserve your previous public API names so other code still calls them the same way

	public static void setPandaEnabled(boolean enabled) {
		ConfigClass.INSTANCE.pandaEnabled = enabled;
		ConfigClass.save();
		PandaTracker.setEnabled(enabled);
	}

	public static boolean isPandaEnabled() {
		return ConfigClass.INSTANCE.pandaEnabled;
	}

	public static void setTurtleEnabled(boolean enabled) {
		ConfigClass.INSTANCE.turtleEnabled = enabled;
		ConfigClass.save();
		TurtleTracker.setEnabled(enabled);
	}

	public static boolean isTurtleEnabled() {
		return ConfigClass.INSTANCE.turtleEnabled;
	}
}