package de.jesse.schaltplanmod;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchaltplanMod implements ModInitializer {
	public static final String MOD_ID = "schaltplanmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Circuit loaded.");
	}
}
