package de.jesse.schaltplanmod.client;

import de.jesse.schaltplanmod.SchaltplanMod;
import net.fabricmc.api.ClientModInitializer;

public class SchaltplanModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		LitematicaSchematicInstaller.installBundledSchematics();
		CircuitEditorKeybinds.register();
		RedstoneWorldInitializer.register();
		SchaltplanMod.LOGGER.info("Circuit client loaded.");
	}
}
