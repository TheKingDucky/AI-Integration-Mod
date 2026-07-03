package com.example;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal Fabric mod main class — no registrations or example features.
 * Keep this as the baseline and add things later as you need them.
 */
public class TemplateMod implements ModInitializer {
	public static final String MOD_ID = "ducky";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Runs when the mod is loaded. Add initialization code here later.
		LOGGER.info("{} has been initialized.", MOD_ID);
		ConfigClass.load();
	}
}