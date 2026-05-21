package de.jesse.schaltplanmod.client;

import de.jesse.schaltplanmod.SchaltplanMod;
import de.jesse.schaltplanmod.circuit.CircuitComponentType;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class LitematicaSchematicInstaller {
	private static final Path TARGET_FOLDER = Path.of("schematics", "SchaltplanMod");

	private LitematicaSchematicInstaller() {
	}

	public static void installBundledSchematics() {
		Path targetFolder = FabricLoader.getInstance().getGameDir().resolve(TARGET_FOLDER);

		try {
			Files.createDirectories(targetFolder);

			for (CircuitComponentType type : CircuitComponentType.MENU_ORDER) {
				if (!type.hasBundledSchematic()) {
					continue;
				}
				copy(type, targetFolder);
			}
		} catch (IOException exception) {
			SchaltplanMod.LOGGER.warn("Could not install bundled Circuit litematics for Litematica.", exception);
		}
	}

	private static void copy(CircuitComponentType type, Path targetFolder) throws IOException {
		ClassLoader classLoader = LitematicaSchematicInstaller.class.getClassLoader();

		try (InputStream stream = classLoader.getResourceAsStream(type.resourcePath())) {
			if (stream == null) {
				throw new IOException("Missing bundled schematic: " + type.resourcePath());
			}

			Path target = targetFolder.resolve(type.fileName());
			Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
