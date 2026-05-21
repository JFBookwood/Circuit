package de.jesse.schaltplanmod.client;

import de.jesse.schaltplanmod.SchaltplanMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CircuitTutorialState {
	private static final Path MARKER_FILE = FabricLoader.getInstance()
			.getConfigDir()
			.resolve("schaltplanmod")
			.resolve("tutorial_seen");

	private CircuitTutorialState() {
	}

	public static boolean shouldShowTutorial() {
		return !Files.exists(MARKER_FILE);
	}

	public static void markSeen() {
		try {
			Files.createDirectories(MARKER_FILE.getParent());
			Files.writeString(MARKER_FILE, "seen=true\n");
		} catch (IOException exception) {
			SchaltplanMod.LOGGER.warn("Could not save tutorial state.", exception);
		}
	}
}
