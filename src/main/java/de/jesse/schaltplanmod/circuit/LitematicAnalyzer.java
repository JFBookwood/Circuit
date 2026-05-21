package de.jesse.schaltplanmod.circuit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class LitematicAnalyzer {
	private static final String INPUT_MARKER = "minecraft:lime_concrete";
	private static final String OUTPUT_MARKER = "minecraft:pink_concrete";

	private LitematicAnalyzer() {
	}

	public static LitematicSchematic read(CircuitComponentType type) throws IOException {
		ClassLoader classLoader = LitematicAnalyzer.class.getClassLoader();

		try (InputStream stream = classLoader.getResourceAsStream(type.resourcePath())) {
			if (stream == null) {
				throw new IOException("Missing schematic resource: " + type.resourcePath());
			}

			CompoundTag root = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
			CompoundTag regions = root.getCompound("Regions");

			if (regions.isEmpty()) {
				throw new IOException("Litematic has no regions: " + type.fileName());
			}

			String regionName = regions.getAllKeys().stream().sorted().findFirst().orElseThrow();
			CompoundTag region = regions.getCompound(regionName);
			LitematicSize size = readSize(region.getCompound("Size"));
			List<PaletteState> palette = readPalette(region.getList("BlockStatePalette", 10));
			long[] blockStates = region.getLongArray("BlockStates");
			int bitsPerBlock = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
			List<SchematicBlock> blocks = new ArrayList<>();
			List<SchematicPort> ports = new ArrayList<>();

			for (int y = 0; y < size.y(); y++) {
				for (int z = 0; z < size.z(); z++) {
					for (int x = 0; x < size.x(); x++) {
						int paletteIndex = paletteIndexAt(blockStates, linearIndex(x, y, z, size), bitsPerBlock);
						PaletteState state = palette.get(paletteIndex);
						String blockName = state.blockName();
						if (!"minecraft:air".equals(blockName)) {
							blocks.add(new SchematicBlock(new SchematicPoint(x, y, z), blockName, state.properties()));
						}
						Optional<PortRole> role = markerRole(blockName);

						if (role.isPresent()) {
							SchematicPoint marker = new SchematicPoint(x, y, z);
							ports.add(new SchematicPort(role.get(), marker, marker.above(), inferSide(role.get(), marker, size)));
						}
					}
				}
			}

			ports.sort(Comparator
					.comparing(SchematicPort::role)
					.thenComparing(port -> port.markerPosition().x())
					.thenComparing(port -> port.markerPosition().z())
					.thenComparing(port -> port.markerPosition().y()));

			return new LitematicSchematic(type, regionName, size, blocks, ports);
		}
	}

	private static LitematicSize readSize(CompoundTag size) {
		return new LitematicSize(Math.abs(size.getInt("x")), Math.abs(size.getInt("y")), Math.abs(size.getInt("z")));
	}

	private static List<PaletteState> readPalette(ListTag paletteTag) {
		List<PaletteState> palette = new ArrayList<>(paletteTag.size());

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

	private static int linearIndex(int x, int y, int z, LitematicSize size) {
		return (y * size.z() + z) * size.x() + x;
	}

	private static int paletteIndexAt(long[] blockStates, int blockIndex, int bitsPerBlock) {
		if (blockStates.length == 0) {
			return 0;
		}

		long startBit = (long) blockIndex * bitsPerBlock;
		int wordIndex = (int) (startBit / Long.SIZE);
		int bitOffset = (int) (startBit % Long.SIZE);
		long value = blockStates[wordIndex] >>> bitOffset;
		int bitsInFirstWord = Long.SIZE - bitOffset;

		if (bitsInFirstWord < bitsPerBlock && wordIndex + 1 < blockStates.length) {
			value |= blockStates[wordIndex + 1] << bitsInFirstWord;
		}

		return (int) (value & ((1L << bitsPerBlock) - 1L));
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
