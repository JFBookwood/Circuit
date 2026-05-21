package de.jesse.schaltplanmod.client;

import de.jesse.schaltplanmod.SchaltplanMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GeneratorWarningState {
	private static final Path MARKER_FILE = FabricLoader.getInstance()
			.getConfigDir()
			.resolve("schaltplanmod")
			.resolve("generator_warning_hidden");

	private GeneratorWarningState() {
	}

	public static boolean shouldShow() {
		return !Files.exists(MARKER_FILE);
	}

	public static void hidePermanently() {
		try {
			Files.createDirectories(MARKER_FILE.getParent());
			Files.writeString(MARKER_FILE, "hidden=true\n");
		} catch (IOException exception) {
			SchaltplanMod.LOGGER.warn("Could not save generator warning state.", exception);
		}
	}
}
