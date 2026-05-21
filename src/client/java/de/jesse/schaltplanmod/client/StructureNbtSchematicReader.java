package de.jesse.schaltplanmod.client;

import de.jesse.schaltplanmod.circuit.CircuitComponentType;
import de.jesse.schaltplanmod.circuit.LitematicSchematic;
import de.jesse.schaltplanmod.circuit.LitematicSize;
import de.jesse.schaltplanmod.circuit.PortRole;
import de.jesse.schaltplanmod.circuit.PortSide;
import de.jesse.schaltplanmod.circuit.SchematicBlock;
import de.jesse.schaltplanmod.circuit.SchematicPoint;
import de.jesse.schaltplanmod.circuit.SchematicPort;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class StructureNbtSchematicReader {
	private static final String INPUT_MARKER = "minecraft:lime_concrete";
	private static final String OUTPUT_MARKER = "minecraft:pink_concrete";

	private StructureNbtSchematicReader() {
	}

	public static LitematicSchematic read(Path file, CircuitComponentType fallbackType, String displayName) throws IOException {
		CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
		ListTag sizeTag = root.getList("size", 3);
		LitematicSize size = new LitematicSize(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2));
		List<PaletteState> palette = readPalette(root.getList("palette", 10));
		List<SchematicBlock> blocks = new ArrayList<>();
		List<SchematicPort> ports = new ArrayList<>();

		ListTag blockTags = root.getList("blocks", 10);
		for (int index = 0; index < blockTags.size(); index++) {
			CompoundTag blockTag = blockTags.getCompound(index);
			ListTag posTag = blockTag.getList("pos", 3);
			SchematicPoint point = new SchematicPoint(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
			PaletteState state = palette.get(blockTag.getInt("state"));
			blocks.add(new SchematicBlock(point, state.blockName(), state.properties()));

			Optional<PortRole> role = markerRole(state.blockName());
			role.ifPresent(portRole -> ports.add(new SchematicPort(portRole, point, point.above(), inferSide(portRole, point, size))));
		}

		blocks.sort(Comparator
				.comparing((SchematicBlock block) -> block.position().y())
				.thenComparing(block -> block.position().z())
				.thenComparing(block -> block.position().x()));
		ports.sort(Comparator
				.comparing(SchematicPort::role)
				.thenComparing(port -> port.markerPosition().x())
				.thenComparing(port -> port.markerPosition().z())
				.thenComparing(port -> port.markerPosition().y()));

		return new LitematicSchematic(fallbackType, file.getFileName().toString(), size, blocks, ports, displayName);
	}

	private static List<PaletteState> readPalette(ListTag paletteTag) {
		List<PaletteState> palette = new ArrayList<>();
		for (int index = 0; index < paletteTag.size(); index++) {
			CompoundTag stateTag = paletteTag.getCompound(index);
			CompoundTag propertiesTag = stateTag.getCompound("Properties");
			Map<String, String> properties = new LinkedHashMap<>();
			for (String key : propertiesTag.getAllKeys()) {
				properties.put(key, propertiesTag.getString(key));
			}
			palette.add(new PaletteState(stateTag.getString("Name"), Map.copyOf(properties)));
		}
		return palette;
	}

	private static Optional<PortRole> markerRole(String blockName) {
		if (INPUT_MARKER.equals(blockName)) {
			return Optional.of(PortRole.INPUT);
		}
		if (OUTPUT_MARKER.equals(blockName)) {
			return Optional.of(PortRole.OUTPUT);
		}
		return Optional.empty();
	}

	private static PortSide inferSide(PortRole role, SchematicPoint marker, LitematicSize size) {
		if (role == PortRole.INPUT && marker.x() == 0) {
			return PortSide.WEST;
		}
		if (role == PortRole.OUTPUT && marker.x() == size.x() - 1) {
			return PortSide.EAST;
		}
		if (marker.x() == 0) {
			return PortSide.WEST;
		}
		if (marker.x() == size.x() - 1) {
			return PortSide.EAST;
		}
		if (marker.z() == 0) {
			return PortSide.NORTH;
		}
		if (marker.z() == size.z() - 1) {
			return PortSide.SOUTH;
		}
		return PortSide.INTERNAL;
	}

	private record PaletteState(String blockName, Map<String, String> properties) {
	}
}
