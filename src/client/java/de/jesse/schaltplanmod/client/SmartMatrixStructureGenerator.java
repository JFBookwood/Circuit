package de.jesse.schaltplanmod.client;

import de.jesse.schaltplanmod.SchaltplanMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SmartMatrixStructureGenerator {
	private SmartMatrixStructureGenerator() {
	}

	public static Path writeEncoder(int inputs, int outputs) throws IOException {
		return writeEncoder(inputs, outputs, generatedFolder());
	}

	public static Path writeEncoder(int inputs, int outputs, Path outputFolder) throws IOException {
		StructureBuilder builder = new StructureBuilder(2 * outputs + 4, 4, 2 * inputs + 1);
		int inputX = builder.sizeX - 1;
		int injectorX = builder.sizeX - 3;

		for (int z = 0; z < builder.sizeZ; z++) {
			for (int x = 0; x < builder.sizeX; x++) {
				builder.block(x, 0, z, block("minecraft:yellow_concrete"));
			}
		}

		for (int output = 0; output < outputs; output++) {
			int outputX = outputColumn(output);
			builder.block(outputX, 0, 0, block("minecraft:pink_concrete"));
			for (int z = 0; z < builder.sizeZ; z++) {
				builder.block(outputX, 1, z, wire("none", "side", "side", "none", 0));
			}
			placeOutputBusRepeaters(builder, outputX);
		}

		for (int input = 0; input < inputs; input++) {
			int rowZ = row(input);
			builder.block(inputX, 0, rowZ, block("minecraft:lime_concrete"));
			builder.block(inputX, 1, rowZ, wire("side", "side", "side", "side", 0));
			builder.block(inputX - 1, 1, rowZ, block("minecraft:yellow_concrete"));
			builder.block(injectorX, 1, rowZ, wallTorch("west", true));

			for (int x = 1; x <= injectorX; x++) {
				builder.block(x, 2, rowZ, block("minecraft:yellow_concrete"));
				builder.block(x, 3, rowZ, wire("side", "none", "none", "side", horizontalBusPower(x)));
			}

			for (int output = 0; output < outputs; output++) {
				if (((input >> output) & 1) == 1) {
					int contactX = outputContactColumn(output, outputs);
					int contactZ = Math.max(0, rowZ - 1);
					builder.block(contactX, 2, Math.min(builder.sizeZ - 1, contactZ + 1), block("minecraft:yellow_concrete"));
					builder.block(contactX, 2, contactZ, wallTorch("north", false));
				}
			}
		}

		return write(outputFolder, "smart_encoder_" + inputs + "_to_" + outputs + ".nbt", builder);
	}

	public static Path writeDecoder(int inputs, int outputs) throws IOException {
		return writeDecoder(inputs, outputs, generatedFolder());
	}

	public static Path writeDecoder(int inputs, int outputs, Path outputFolder) throws IOException {
		StructureBuilder builder = new StructureBuilder(2 * outputs + 4, 4, 2 * inputs + 3);
		int inputX = builder.sizeX - 1;
		int outputZ = builder.sizeZ - 1;

		for (int z = 0; z < builder.sizeZ; z++) {
			for (int x = 0; x < builder.sizeX; x++) {
				builder.block(x, 0, z, block("minecraft:yellow_concrete"));
			}
		}

		for (int input = 0; input < inputs; input++) {
			int rowZ = row(input);
			builder.block(inputX, 0, rowZ, block("minecraft:lime_concrete"));
			builder.block(inputX, 1, rowZ, wire("side", "none", "none", "up", 0));
			builder.block(inputX - 1, 1, rowZ, block("minecraft:yellow_concrete"));

			for (int output = 0; output < outputs; output++) {
				int outX = outputColumn(output);
				builder.block(outX, 2, rowZ, block("minecraft:yellow_concrete"));
				builder.block(outX + 1, 1, rowZ, block("minecraft:yellow_concrete"));
				builder.block(outX + 1, 2, rowZ, repeater("east"));
				builder.block(outX, 3, rowZ, wire("side", "side", "side", "side", 0));
			}
			builder.block(inputX - 2, 2, rowZ, block("minecraft:yellow_concrete"));
			builder.block(inputX - 1, 2, rowZ, wire("side", "none", "none", "up", 0));
			builder.block(inputX - 2, 3, rowZ, wire("side", "none", "none", "side", 0));
		}

		for (int output = 0; output < outputs; output++) {
			int outX = outputColumn(output);
			builder.block(outX, 0, outputZ, block("minecraft:pink_concrete"));
			builder.block(outX, 1, outputZ, wallTorch("south", true));
			builder.block(outX, 1, outputZ - 1, block("minecraft:yellow_concrete"));
			for (int z = 1; z < outputZ - 1; z++) {
				if (z > 1 && z % 12 == 0) {
					builder.block(outX, 1, z, repeater("south"));
				} else {
					builder.block(outX, 1, z, wire("none", "side", "side", "none", 0));
				}
			}
		}

		for (int output = 0; output < outputs; output++) {
			int outX = outputColumn(output);
			int code = decoderCodeForSlot(output, inputs, outputs);
			for (int bit = 0; bit < inputs; bit++) {
				int rowZ = row(bit);
				if (shouldSkipDecoderCondition(inputs, outputs, output, bit)) {
					builder.block(outX + 1, 2, rowZ, block("minecraft:air"));
					continue;
				}
				if (((code >> bit) & 1) == 1) {
					builder.block(outX + 1, 1, rowZ, block("minecraft:air"));
					builder.block(outX + 1, 2, rowZ, block("minecraft:yellow_concrete"));
					builder.block(outX, 2, rowZ + 1, wallTorch("south", true));
					builder.block(outX, 3, rowZ, wire("side", "none", "none", "side", 0));
					builder.block(outX + 1, 3, rowZ, wire("side", "none", "none", "side", 0));
					if (outX + 2 < builder.sizeX) {
						builder.block(outX + 2, 3, rowZ, wire("side", "none", "none", "side", 0));
					}
				}
			}
		}

		return write(outputFolder, "smart_decoder_" + inputs + "_to_" + outputs + ".nbt", builder);
	}

	private static Path writeSmartDecoder3To4(Path outputFolder) throws IOException {
		StructureBuilder builder = new StructureBuilder(12, 4, 9);

		for (int z = 0; z < builder.sizeZ; z++) {
			for (int x = 0; x < builder.sizeX; x++) {
				builder.block(x, 0, z, block("minecraft:yellow_concrete"));
			}
		}

		for (int input = 0; input < 3; input++) {
			builder.block(11, 0, row(input), block("minecraft:lime_concrete"));
			builder.block(11, 1, row(input), wire("side", "none", "none", "up", 0));
		}

		for (int output = 0; output < 4; output++) {
			int outputX = outputColumn(output);
			builder.block(outputX, 0, 8, block("minecraft:pink_concrete"));
			builder.block(outputX, 1, 8, wallTorch("south", false));
			builder.block(outputX, 1, 7, block("minecraft:yellow_concrete"));
		}

		int[][] verticalPower = {
				{10, 14, 12, 14},
				{11, 15, 13, 15},
				{12, 14, 14, 14},
				{13, 15, 15, 13},
				{14, 14, 14, 12},
				{15, 13, 13, 11}
		};
		for (int z = 1; z <= 6; z++) {
			for (int output = 0; output < 4; output++) {
				builder.block(outputColumn(output), 1, z, wire("none", "side", "side", "none", verticalPower[z - 1][output]));
			}
		}

		int[][] layerOneSupports = {
				{2, 6, 10},
				{2, 8, 10},
				{4, 6, 8, 10}
		};
		for (int input = 0; input < layerOneSupports.length; input++) {
			for (int x : layerOneSupports[input]) {
				builder.block(x, 1, row(input), block("minecraft:yellow_concrete"));
			}
		}

		placeDecoderInputRow(builder, 1, new int[]{1, 3, 4, 5, 7, 8, 9}, new int[]{2, 6}, new int[]{1, 3, 4, 5, 7, 8, 9}, new int[]{1});
		placeDecoderInputRow(builder, 3, new int[]{1, 3, 4, 5, 6, 7, 9}, new int[]{8}, new int[]{1, 3, 4, 5, 6, 7, 9}, new int[]{1});
		placeDecoderInputRow(builder, 5, new int[]{1, 2, 3, 5, 7, 9}, new int[]{4, 6, 8}, new int[]{1, 2, 3, 5, 7, 9}, new int[]{5, 7});

		placeTorches(builder, 2, 3, 7);
		placeTorches(builder, 4, 3, 5);
		placeTorches(builder, 6, 1);

		return write(outputFolder, "smart_decoder_3_to_4.nbt", builder);
	}

	private static void placeDecoderInputRow(StructureBuilder builder, int z, int[] supports, int[] repeaters, int[] topWires, int[] allSideTopWires) {
		for (int x : supports) {
			builder.block(x, 2, z, block("minecraft:yellow_concrete"));
		}
		for (int x : repeaters) {
			builder.block(x, 2, z, repeater("east"));
		}
		builder.block(10, 2, z, wire("side", "none", "none", "up", 0));

		for (int x : topWires) {
			builder.block(x, 3, z, contains(allSideTopWires, x)
					? wire("side", "side", "side", "side", 0)
					: wire("side", "none", "none", "side", 0));
		}
	}

	private static int[] decoderSupportColumns(int outputs) {
		int[] columns = new int[outputs + Math.max(0, outputs - 1)];
		int index = 0;
		for (int output = 0; output < outputs; output++) {
			columns[index++] = outputColumn(output);
			if (output < outputs - 1) {
				columns[index++] = outputColumn(output) + 1;
			}
		}
		return columns;
	}

	private static int[] decoderRepeaterColumns(int outputs) {
		int count = Math.max(0, (outputs - 1) / 7);
		int[] columns = new int[count];
		for (int index = 0; index < count; index++) {
			columns[index] = outputColumn(Math.min(outputs - 1, (index + 1) * 7));
		}
		return columns;
	}

	private static int[] decoderTopWireColumns(int outputs) {
		int[] columns = new int[outputs + Math.max(0, outputs - 1)];
		int index = 0;
		for (int output = 0; output < outputs; output++) {
			columns[index++] = outputColumn(output);
			if (output < outputs - 1) {
				columns[index++] = outputColumn(output) + 1;
			}
		}
		return columns;
	}

	private static void placeTorches(StructureBuilder builder, int z, int... xs) {
		for (int x : xs) {
			builder.block(x, 2, z, wallTorch("south", true));
		}
	}

	private static int decoderCodeForSlot(int slot, int inputs, int outputs) {
		int maxCode = Math.max(0, (1 << Math.min(inputs, 30)) - 1);
		if (outputs == maxCode + 1) {
			return slot == 0 ? maxCode : slot == 1 ? 0 : maxCode - slot + 1;
		}
		return Math.max(0, outputs - slot);
	}

	private static boolean shouldSkipDecoderCondition(int inputs, int outputs, int slot, int bit) {
		return inputs == 3 && outputs == 4 && slot == 0 && bit == 1;
	}

	private static void placeOutputBusRepeaters(StructureBuilder builder, int outputX) {
		for (int z = 15; z < builder.sizeZ - 1; z += 14) {
			builder.block(outputX, 1, z, repeater("north"));
		}
	}

	private static boolean contains(int[] values, int needle) {
		for (int value : values) {
			if (value == needle) {
				return true;
			}
		}
		return false;
	}

	private static int row(int index) {
		return 1 + index * 2;
	}

	private static int outputColumn(int output) {
		return 1 + output * 2;
	}

	private static int outputContactColumn(int output, int outputs) {
		return outputColumn(outputs - 1 - output);
	}

	private static int horizontalBusPower(int x) {
		return Math.min(15, 10 + x);
	}

	private static Path write(Path outputFolder, String fileName, StructureBuilder builder) throws IOException {
		Files.createDirectories(outputFolder);
		Path target = outputFolder.resolve(fileName);
		NbtIo.writeCompressed(builder.toNbt(), target);
		SchaltplanMod.LOGGER.info("Generated smart matrix structure: {}", target);
		return target;
	}

	private static Path generatedFolder() {
		try {
			return FabricLoader.getInstance().getGameDir().resolve("generated").resolve("schaltplanmod");
		} catch (RuntimeException exception) {
			return Path.of("generated", "schaltplanmod");
		}
	}

	private static BlockState block(String name) {
		return new BlockState(name, Map.of());
	}

	private static BlockState wallTorch(String facing, boolean lit) {
		return new BlockState("minecraft:redstone_wall_torch", Map.of("facing", facing, "lit", Boolean.toString(lit)));
	}

	private static BlockState repeater(String facing) {
		return new BlockState("minecraft:repeater", Map.of(
				"delay", "1",
				"facing", facing,
				"locked", "false",
				"powered", "false"
		));
	}

	private static BlockState wire(String east, String south, String north, String west, int power) {
		Map<String, String> properties = new LinkedHashMap<>();
		properties.put("east", east);
		properties.put("south", south);
		properties.put("north", north);
		properties.put("west", west);
		properties.put("power", Integer.toString(power));
		return new BlockState("minecraft:redstone_wire", properties);
	}

	private record BlockState(String name, Map<String, String> properties) {
		CompoundTag toNbt() {
			CompoundTag tag = new CompoundTag();
			tag.putString("Name", name);
			if (!properties.isEmpty()) {
				CompoundTag propertiesTag = new CompoundTag();
				properties.forEach(propertiesTag::putString);
				tag.put("Properties", propertiesTag);
			}
			return tag;
		}
	}

	private record PlacedBlock(int x, int y, int z, BlockState state) {
	}

	private record BlockPos(int x, int y, int z) {
	}

	private static final class StructureBuilder {
		private final int sizeX;
		private final int sizeY;
		private final int sizeZ;
		private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

		private StructureBuilder(int sizeX, int sizeY, int sizeZ) {
			this.sizeX = sizeX;
			this.sizeY = sizeY;
			this.sizeZ = sizeZ;
		}

		private void block(int x, int y, int z, BlockState state) {
			BlockPos position = new BlockPos(x, y, z);
			if ("minecraft:air".equals(state.name())) {
				blocks.remove(position);
				return;
			}
			blocks.put(position, state);
		}

		private CompoundTag toNbt() {
			CompoundTag root = new CompoundTag();
			ListTag size = new ListTag();
			size.add(IntTag.valueOf(sizeX));
			size.add(IntTag.valueOf(sizeY));
			size.add(IntTag.valueOf(sizeZ));
			root.put("size", size);
			root.putInt("DataVersion", 3953);
			root.put("entities", new ListTag());

			Map<BlockState, Integer> paletteIds = new LinkedHashMap<>();
			ListTag blockTags = new ListTag();

			for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
				BlockPos blockPos = entry.getKey();
				BlockState state = entry.getValue();
				int stateId = paletteIds.computeIfAbsent(state, ignored -> paletteIds.size());
				CompoundTag blockTag = new CompoundTag();
				ListTag pos = new ListTag();
				pos.add(IntTag.valueOf(blockPos.x()));
				pos.add(IntTag.valueOf(blockPos.y()));
				pos.add(IntTag.valueOf(blockPos.z()));
				blockTag.put("pos", pos);
				blockTag.putInt("state", stateId);
				blockTags.add(blockTag);
			}

			ListTag palette = new ListTag();
			paletteIds.keySet().forEach(state -> palette.add(state.toNbt()));
			root.put("palette", palette);
			root.put("blocks", blockTags);
			return root;
		}
	}
}
