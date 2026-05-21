package de.jesse.schaltplanmod.client;

import de.jesse.schaltplanmod.circuit.CircuitComponentType;
import de.jesse.schaltplanmod.circuit.LitematicAnalyzer;
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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class SmartMatrixGeneratorTool {
	private static final int MAX_EXAMPLES = 80;

	private SmartMatrixGeneratorTool() {
	}

	public static void main(String[] args) throws Exception {
		Path outputFolder = args.length > 0 ? Path.of(args[0]) : Path.of("generated", "schaltplanmod");
		Files.createDirectories(outputFolder);

		if (args.length > 1 && "dump".equalsIgnoreCase(args[1])) {
			dumpReference(outputFolder, CircuitComponentType.SMART_4_TO_2_ENCODER);
			dumpReference(outputFolder, CircuitComponentType.SMART_4_TO_3_DECODER);
			return;
		}

		if (args.length > 2 && "compare-litematic".equalsIgnoreCase(args[1])) {
			Path referenceFile = Path.of(args[2]);
			LitematicSchematic reference = readLitematicFile(referenceFile, CircuitComponentType.SMART_4_TO_2_ENCODER, "External Smart 4->2 Encoder");
			Path generated = SmartMatrixStructureGenerator.writeEncoder(reference.inputs().size(), reference.outputs().size(), outputFolder);
			ComparisonReport report = compare("External Encoder " + reference.inputs().size() + "->" + reference.outputs().size(), reference, readStructure(generated));
			Path reportFolder = outputFolder.resolve("reports");
			Files.createDirectories(reportFolder);
			Path reportFile = reportFolder.resolve(report.fileName());
			Files.writeString(reportFile, report.text());
			dumpSchematic(outputFolder, reference, safeFileName(referenceFile.getFileName().toString()) + "_dump.txt");
			System.out.println(report.title() + ": exact=" + (report.exactMatch() ? "OK" : "DIFF") + ", logic=" + (report.logicMatch() ? "OK" : "DIFF") + " -> " + reportFile);
			return;
		}

		if (args.length > 1 && "matrix-test".equalsIgnoreCase(args[1])) {
			List<MatrixSize> sizes = matrixTestSizes(args);
			Path reportFolder = outputFolder.resolve("reports");
			Files.createDirectories(reportFolder);
			StringBuilder summary = new StringBuilder("Smart encoder matrix tests\n\n");
			boolean ok = true;
			for (MatrixSize size : sizes) {
				Path generated = SmartMatrixStructureGenerator.writeEncoder(size.inputs(), size.outputs(), outputFolder);
				MatrixTestResult result = testEncoderMatrix(size.inputs(), size.outputs(), readStructure(generated));
				Path reportFile = reportFolder.resolve("matrix_encoder_" + size.inputs() + "_to_" + size.outputs() + ".txt");
				Files.writeString(reportFile, result.text());
				ok &= result.ok();
				summary.append("Encoder ")
						.append(size.inputs())
						.append("->")
						.append(size.outputs())
						.append(": ")
						.append(result.ok() ? "OK" : "FAIL")
						.append(" -> ")
						.append(reportFile)
						.append('\n');
			}
			Path summaryFile = reportFolder.resolve("matrix_tests_summary.txt");
			Files.writeString(summaryFile, summary.toString());
			System.out.println(summary);
			if (!ok) {
				throw new IllegalStateException("One or more matrix tests failed. See " + summaryFile);
			}
			return;
		}

		if (args.length > 1 && "redstone-sim".equalsIgnoreCase(args[1])) {
			List<MatrixSize> sizes = redstoneSimulationSizes(args);
			Path reportFolder = outputFolder.resolve("reports");
			Files.createDirectories(reportFolder);
			StringBuilder summary = new StringBuilder("Smart encoder redstone simulation tests\n\n");
			boolean ok = true;
			for (MatrixSize size : sizes) {
				Path generated = SmartMatrixStructureGenerator.writeEncoder(size.inputs(), size.outputs(), outputFolder);
				SimulationReport result = simulateEncoder(size.inputs(), size.outputs(), readStructure(generated));
				Path reportFile = reportFolder.resolve("redstone_sim_encoder_" + size.inputs() + "_to_" + size.outputs() + ".txt");
				Files.writeString(reportFile, result.text());
				ok &= result.ok();
				summary.append("Encoder ")
						.append(size.inputs())
						.append("->")
						.append(size.outputs())
						.append(": ")
						.append(result.ok() ? "OK" : "FAIL")
						.append(" -> ")
						.append(reportFile)
						.append('\n');
			}
			Path summaryFile = reportFolder.resolve("redstone_sim_summary.txt");
			Files.writeString(summaryFile, summary.toString());
			System.out.println(summary);
			if (!ok) {
				throw new IllegalStateException("One or more redstone simulations failed. See " + summaryFile);
			}
			return;
		}

		if (args.length > 1 && "decoder-sim".equalsIgnoreCase(args[1])) {
			List<MatrixSize> sizes = decoderSimulationSizes(args);
			Path reportFolder = outputFolder.resolve("reports");
			Files.createDirectories(reportFolder);
			StringBuilder summary = new StringBuilder("Smart decoder redstone simulation tests\n\n");
			boolean ok = true;
			for (MatrixSize size : sizes) {
				Path generated = SmartMatrixStructureGenerator.writeDecoder(size.inputs(), size.outputs(), outputFolder);
				SimulationReport result = simulateDecoder(size.inputs(), size.outputs(), readStructure(generated));
				Path reportFile = reportFolder.resolve("redstone_sim_decoder_" + size.inputs() + "_to_" + size.outputs() + ".txt");
				Files.writeString(reportFile, result.text());
				ok &= result.ok();
				summary.append("Decoder ")
						.append(size.inputs())
						.append("->")
						.append(size.outputs())
						.append(": ")
						.append(result.ok() ? "OK" : "FAIL")
						.append(" -> ")
						.append(reportFile)
						.append('\n');
			}
			Path summaryFile = reportFolder.resolve("decoder_redstone_sim_summary.txt");
			Files.writeString(summaryFile, summary.toString());
			System.out.println(summary);
			if (!ok) {
				throw new IllegalStateException("One or more decoder redstone simulations failed. See " + summaryFile);
			}
			return;
		}

		if (args.length > 1 && "preset-sim".equalsIgnoreCase(args[1])) {
			PresetSimulationReport result = simulatePresets();
			Path reportFolder = outputFolder.resolve("reports");
			Files.createDirectories(reportFolder);
			Path reportFile = reportFolder.resolve("preset_sim_summary.txt");
			Files.writeString(reportFile, result.text());
			System.out.println(result.text());
			if (!result.ok()) {
				throw new IllegalStateException("One or more presets failed validation. See " + reportFile);
			}
			return;
		}

		List<ComparisonReport> reports = new ArrayList<>();
		reports.add(generateAndCompareEncoder(CircuitComponentType.SMART_4_TO_2_ENCODER, outputFolder));
		reports.add(generateAndCompareDecoder(CircuitComponentType.SMART_4_TO_3_DECODER, outputFolder));

		Path reportFolder = outputFolder.resolve("reports");
		Files.createDirectories(reportFolder);

		StringBuilder summary = new StringBuilder();
		summary.append("Smart matrix generator reports\n\n");
		for (ComparisonReport report : reports) {
			Path reportFile = reportFolder.resolve(report.fileName());
			Files.writeString(reportFile, report.text());
			summary.append(report.title())
					.append(": exact=")
					.append(report.exactMatch() ? "OK" : "DIFF")
					.append(", logic=")
					.append(report.logicMatch() ? "OK" : "DIFF")
					.append(" -> ")
					.append(reportFile)
					.append('\n');
		}

		Path summaryFile = reportFolder.resolve("summary.txt");
		Files.writeString(summaryFile, summary.toString());
		System.out.println(summary);
	}

	private static ComparisonReport generateAndCompareEncoder(CircuitComponentType referenceType, Path outputFolder) throws IOException {
		LitematicSchematic reference = LitematicAnalyzer.read(referenceType);
		Path generated = SmartMatrixStructureGenerator.writeEncoder(reference.inputs().size(), reference.outputs().size(), outputFolder);
		Structure generatedStructure = readStructure(generated);
		return compare("Encoder " + reference.inputs().size() + "->" + reference.outputs().size(), reference, generatedStructure);
	}

	private static ComparisonReport generateAndCompareDecoder(CircuitComponentType referenceType, Path outputFolder) throws IOException {
		LitematicSchematic reference = LitematicAnalyzer.read(referenceType);
		Path generated = SmartMatrixStructureGenerator.writeDecoder(reference.inputs().size(), reference.outputs().size(), outputFolder);
		Structure generatedStructure = readStructure(generated);
		return compare("Decoder " + reference.inputs().size() + "->" + reference.outputs().size(), reference, generatedStructure);
	}

	private static void dumpReference(Path outputFolder, CircuitComponentType type) throws IOException {
		LitematicSchematic schematic = LitematicAnalyzer.read(type);
		dumpSchematic(outputFolder, schematic, type.fileName().replace(".litematic", "_dump.txt"));
	}

	private static void dumpSchematic(Path outputFolder, LitematicSchematic schematic, String fileName) throws IOException {
		Map<SchematicPoint, String> blocks = blockMap(schematic.blocks(), false);
		StringBuilder dump = new StringBuilder();
		dump.append(schematic.regionName()).append(" size ").append(size(schematic.size())).append('\n');
		for (int y = 0; y < schematic.size().y(); y++) {
			dump.append("Layer ").append(y).append('\n');
			for (int z = 0; z < schematic.size().z(); z++) {
				for (int x = 0; x < schematic.size().x(); x++) {
					dump.append(symbol(blocks.get(new SchematicPoint(x, y, z))));
				}
				dump.append('\n');
			}
		}
		Path dumpFile = outputFolder.resolve("reports").resolve(fileName);
		Files.createDirectories(dumpFile.getParent());
		Files.writeString(dumpFile, dump.toString());
		System.out.println("Dumped " + dumpFile);
	}

	private static LitematicSchematic readLitematicFile(Path file, CircuitComponentType type, String displayName) throws IOException {
		try (InputStream stream = Files.newInputStream(file)) {
			CompoundTag root = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
			CompoundTag regions = root.getCompound("Regions");
			String regionName = regions.getAllKeys().stream().sorted().findFirst().orElseThrow();
			CompoundTag region = regions.getCompound(regionName);
			LitematicSize size = readLitematicSize(region.getCompound("Size"));
			List<PaletteState> palette = readLitematicPalette(region.getList("BlockStatePalette", 10));
			long[] blockStates = region.getLongArray("BlockStates");
			int bitsPerBlock = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
			List<SchematicBlock> blocks = new ArrayList<>();
			List<SchematicPort> ports = new ArrayList<>();

			for (int y = 0; y < size.y(); y++) {
				for (int z = 0; z < size.z(); z++) {
					for (int x = 0; x < size.x(); x++) {
						PaletteState state = palette.get(paletteIndexAt(blockStates, (y * size.z() + z) * size.x() + x, bitsPerBlock));
						if (!"minecraft:air".equals(state.blockName())) {
							SchematicPoint point = new SchematicPoint(x, y, z);
							blocks.add(new SchematicBlock(point, state.blockName(), state.properties()));
							if ("minecraft:lime_concrete".equals(state.blockName())) {
								ports.add(new SchematicPort(PortRole.INPUT, point, point.above(), inferSide(point, size)));
							} else if ("minecraft:pink_concrete".equals(state.blockName())) {
								ports.add(new SchematicPort(PortRole.OUTPUT, point, point.above(), inferSide(point, size)));
							}
						}
					}
				}
			}

			return new LitematicSchematic(type, regionName, size, blocks, ports, displayName);
		}
	}

	private static LitematicSize readLitematicSize(CompoundTag size) {
		return new LitematicSize(Math.abs(size.getInt("x")), Math.abs(size.getInt("y")), Math.abs(size.getInt("z")));
	}

	private static List<PaletteState> readLitematicPalette(ListTag paletteTag) {
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

	private static int paletteIndexAt(long[] blockStates, int blockIndex, int bitsPerBlock) {
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

	private static PortSide inferSide(SchematicPoint marker, LitematicSize size) {
		if (marker.x() == 0) return PortSide.WEST;
		if (marker.x() == size.x() - 1) return PortSide.EAST;
		if (marker.z() == 0) return PortSide.NORTH;
		if (marker.z() == size.z() - 1) return PortSide.SOUTH;
		return PortSide.INTERNAL;
	}

	private static char symbol(String state) {
		if (state == null) {
			return '.';
		}
		if (state.startsWith("minecraft:yellow_concrete")) {
			return 'Y';
		}
		if (state.startsWith("minecraft:lime_concrete")) {
			return 'I';
		}
		if (state.startsWith("minecraft:pink_concrete")) {
			return 'O';
		}
		if (state.startsWith("minecraft:redstone_wire")) {
			return 'r';
		}
		if (state.startsWith("minecraft:redstone_wall_torch")) {
			return 'T';
		}
		if (state.startsWith("minecraft:repeater")) {
			return 'R';
		}
		return '?';
	}

	private static ComparisonReport compare(String title, LitematicSchematic reference, Structure generated) {
		Map<SchematicPoint, String> expected = blockMap(reference.blocks(), false);
		Map<SchematicPoint, String> actual = generated.blocks();
		Map<SchematicPoint, String> expectedLogic = blockMap(reference.blocks(), true);
		Map<SchematicPoint, String> actualLogic = normalizeMap(actual);

		Comparison exact = diff(expected, actual);
		Comparison logic = diff(expectedLogic, actualLogic);
		boolean sizeMatch = reference.size().equals(generated.size());

		StringBuilder text = new StringBuilder();
		text.append(title).append('\n');
		text.append("Reference: ").append(reference.type().fileName()).append('\n');
		text.append("Reference size: ").append(size(reference.size())).append('\n');
		text.append("Generated size: ").append(size(generated.size())).append('\n');
		text.append("Size match: ").append(sizeMatch).append("\n\n");
		appendTorchPositions(text, "Reference torches", expected);
		appendTorchPositions(text, "Generated torches", actual);
		text.append('\n');
		text.append("Exact compare\n");
		appendComparison(text, exact);
		text.append('\n');
		text.append("Logic compare (ignores redstone_wire power)\n");
		appendComparison(text, logic);

		return new ComparisonReport(safeFileName(title) + ".txt", title, sizeMatch && exact.match(), sizeMatch && logic.match(), text.toString());
	}

	private static void appendTorchPositions(StringBuilder text, String label, Map<SchematicPoint, String> blocks) {
		text.append(label).append(":\n");
		blocks.entrySet().stream()
				.filter(entry -> entry.getValue().startsWith("minecraft:redstone_wall_torch"))
				.map(entry -> format(entry.getKey(), entry.getValue()))
				.forEach(entry -> text.append("  ").append(entry).append('\n'));
	}

	private static List<MatrixSize> matrixTestSizes(String[] args) {
		if (args.length > 3) {
			if ((args.length - 2) % 2 != 0) {
				throw new IllegalArgumentException("matrix-test expects input/output pairs.");
			}
			List<MatrixSize> sizes = new ArrayList<>();
			for (int index = 2; index < args.length; index += 2) {
				sizes.add(new MatrixSize(Integer.parseInt(args[index]), Integer.parseInt(args[index + 1])));
			}
			return sizes;
		}
		return List.of(
				new MatrixSize(4, 2),
				new MatrixSize(20, 10),
				new MatrixSize(32, 5),
				new MatrixSize(64, 6),
				new MatrixSize(1024, 10)
		);
	}

	private static List<MatrixSize> redstoneSimulationSizes(String[] args) {
		if (args.length > 3) {
			return matrixTestSizes(args);
		}
		return List.of(
				new MatrixSize(4, 2),
				new MatrixSize(20, 10),
				new MatrixSize(32, 5),
				new MatrixSize(64, 6)
		);
	}

	private static List<MatrixSize> decoderSimulationSizes(String[] args) {
		if (args.length > 3) {
			return matrixTestSizes(args);
		}
		return List.of(
				new MatrixSize(3, 4),
				new MatrixSize(2, 4),
				new MatrixSize(4, 16)
		);
	}

	private static PresetSimulationReport simulatePresets() throws IOException {
		Map<CircuitComponentType, LitematicSchematic> schematics = new java.util.EnumMap<>(CircuitComponentType.class);
		for (CircuitComponentType type : CircuitComponentType.values()) {
			if (!type.hasBundledSchematic()) {
				continue;
			}
			schematics.put(type, LitematicAnalyzer.read(type));
		}

		List<String> failures = new ArrayList<>();
		StringBuilder text = new StringBuilder("Circuit preset validation\n\n");
		List<Path> files = SchaltplanPlanStorage.listPlanFiles();
		for (Path file : files) {
			List<PlacedComponent> components = SchaltplanPlanStorage.loadFile(file, schematics);
			text.append(file).append(": ").append(components.size()).append(" components\n");
			if (components.isEmpty()) {
				failures.add(file + ": no components loaded");
				continue;
			}
			validateWireGroups(file, components, failures);
			validatePresetTruthTable(file, components, failures, text);
		}

		text.append('\n');
		appendMatrixList(text, "Failures", failures);
		text.append("\nResult: ").append(failures.isEmpty() ? "OK" : "FAIL").append('\n');
		return new PresetSimulationReport(failures.isEmpty(), text.toString());
	}

	private static void validateWireGroups(Path file, List<PlacedComponent> components, List<String> failures) {
		Map<Long, List<PlacedComponent>> groups = new LinkedHashMap<>();
		for (PlacedComponent component : components) {
			if (isWire(component) && component.groupId() > 0) {
				groups.computeIfAbsent(component.groupId(), ignored -> new ArrayList<>()).add(component);
			}
		}

		for (Map.Entry<Long, List<PlacedComponent>> entry : groups.entrySet()) {
			List<PlacedComponent> group = entry.getValue();
			if (group.size() < 2) {
				continue;
			}
			Set<SchematicPoint> unvisited = new HashSet<>();
			for (PlacedComponent component : group) {
				unvisited.add(new SchematicPoint(component.gridX(), 0, component.gridZ()));
			}
			List<SchematicPoint> queue = new ArrayList<>();
			SchematicPoint first = unvisited.iterator().next();
			queue.add(first);
			unvisited.remove(first);
			for (int index = 0; index < queue.size(); index++) {
				SchematicPoint point = queue.get(index);
				for (SchematicPoint neighbor : List.of(
						new SchematicPoint(point.x() + 1, 0, point.z()),
						new SchematicPoint(point.x() - 1, 0, point.z()),
						new SchematicPoint(point.x(), 0, point.z() + 1),
						new SchematicPoint(point.x(), 0, point.z() - 1))) {
					if (unvisited.remove(neighbor)) {
						queue.add(neighbor);
					}
				}
			}
			if (!unvisited.isEmpty()) {
				failures.add(file.getFileName() + ": wire group " + entry.getKey() + " is split into disconnected/diagonal pieces; unreachable=" + unvisited);
			}
		}
	}

	private static boolean isWire(PlacedComponent component) {
		return component.schematic().type() == CircuitComponentType.WIRE || component.schematic().type() == CircuitComponentType.OBSERVER_WIRE;
	}

	private static void validatePresetTruthTable(Path file, List<PlacedComponent> components, List<String> failures, StringBuilder text) {
		String name = file.getFileName().toString();
		if (name.equals("01_switch_lamp.bcdi")) {
			validateSingleOutputPreset(name, components, failures, text, values -> values[0]);
		} else if (name.equals("02_not_gate_lamp.bcdi")) {
			validateSingleOutputPreset(name, components, failures, text, values -> !values[0]);
		} else if (name.equals("03_and_gate_lamp.bcdi")) {
			validateSingleOutputPreset(name, components, failures, text, values -> values[0] && values[1]);
		} else if (name.equals("04_xor_gate_lamp.bcdi")) {
			validateSingleOutputPreset(name, components, failures, text, values -> values[0] ^ values[1]);
		} else if (name.equals("half_adder.bcdi")) {
			validateHalfAdderPreset(name, components, failures, text);
		}
	}

	private static void validateSingleOutputPreset(String name, List<PlacedComponent> components, List<String> failures, StringBuilder text, TruthFunction function) {
		List<Integer> inputs = inputComponentIndexes(components);
		List<Integer> lamps = lampComponentIndexes(components);
		if (inputs.isEmpty() || lamps.size() != 1) {
			failures.add(name + ": expected inputs and exactly one lamp, got inputs=" + inputs.size() + " lamps=" + lamps.size());
			return;
		}
		int combinations = 1 << inputs.size();
		for (int mask = 0; mask < combinations; mask++) {
			boolean[] values = inputValues(inputs.size(), mask);
			boolean expected = function.apply(values);
			boolean actual = lampOn(components, inputs, lamps.getFirst(), values);
			if (actual != expected) {
				failures.add(name + ": input " + booleanVector(values) + " expected lamp=" + expected + " got=" + actual);
			}
		}
		text.append("  truth table: ").append(name).append(" checked ").append(combinations).append(" combinations\n");
	}

	private static void validateHalfAdderPreset(String name, List<PlacedComponent> components, List<String> failures, StringBuilder text) {
		List<Integer> inputs = inputComponentIndexes(components);
		List<Integer> lamps = lampComponentIndexes(components).stream()
				.sorted(Comparator.comparingInt(index -> components.get(index).gridZ()))
				.toList();
		if (inputs.size() != 2 || lamps.size() != 2) {
			failures.add(name + ": expected two inputs and two lamps, got inputs=" + inputs.size() + " lamps=" + lamps.size());
			return;
		}
		for (int mask = 0; mask < 4; mask++) {
			boolean[] values = inputValues(2, mask);
			boolean expectedSum = values[0] ^ values[1];
			boolean expectedCarry = values[0] && values[1];
			boolean actualSum = lampOn(components, inputs, lamps.get(0), values);
			boolean actualCarry = lampOn(components, inputs, lamps.get(1), values);
			if (actualSum != expectedSum || actualCarry != expectedCarry) {
				failures.add(name + ": input " + booleanVector(values) + " expected sum/carry=" + expectedSum + "/" + expectedCarry + " got=" + actualSum + "/" + actualCarry);
			}
		}
		text.append("  truth table: ").append(name).append(" checked 4 combinations\n");
	}

	private static void validateFullAdderPreset(String name, List<PlacedComponent> components, List<String> failures, StringBuilder text) {
		List<Integer> inputs = inputComponentIndexes(components);
		List<Integer> lamps = lampComponentIndexes(components).stream()
				.sorted(Comparator.comparingInt(index -> components.get(index).gridZ()))
				.toList();
		if (inputs.size() != 3 || lamps.size() != 2) {
			failures.add(name + ": expected three inputs and two lamps, got inputs=" + inputs.size() + " lamps=" + lamps.size());
			return;
		}
		for (int mask = 0; mask < 8; mask++) {
			boolean[] values = inputValues(3, mask);
			int sum = (values[0] ? 1 : 0) + (values[1] ? 1 : 0) + (values[2] ? 1 : 0);
			boolean expectedSum = (sum & 1) == 1;
			boolean expectedCarry = sum >= 2;
			boolean actualSum = lampOn(components, inputs, lamps.get(0), values);
			boolean actualCarry = lampOn(components, inputs, lamps.get(1), values);
			if (actualSum != expectedSum || actualCarry != expectedCarry) {
				failures.add(name + ": input " + booleanVector(values) + " expected sum/carry=" + expectedSum + "/" + expectedCarry + " got=" + actualSum + "/" + actualCarry);
			}
		}
		text.append("  truth table: ").append(name).append(" checked 8 combinations\n");
	}

	private static boolean lampOn(List<PlacedComponent> components, List<Integer> inputIndexes, int lampIndex, boolean[] values) {
		Map<Integer, Boolean> manualInputs = new LinkedHashMap<>();
		for (int index = 0; index < inputIndexes.size(); index++) {
			manualInputs.put(inputIndexes.get(index), values[index]);
		}
		CircuitSimulationEngine.SimulationSnapshot snapshot = CircuitSimulationEngine.simulate(components, manualInputs, 32);
		return snapshot.signalForComponent(lampIndex) > 0;
	}

	private static List<Integer> inputComponentIndexes(List<PlacedComponent> components) {
		List<Integer> inputs = new ArrayList<>();
		for (int index = 0; index < components.size(); index++) {
			CircuitComponentType type = components.get(index).schematic().type();
			if (type == CircuitComponentType.SWITCH || type == CircuitComponentType.BUTTON) {
				inputs.add(index);
			}
		}
		return inputs;
	}

	private static List<Integer> lampComponentIndexes(List<PlacedComponent> components) {
		List<Integer> lamps = new ArrayList<>();
		for (int index = 0; index < components.size(); index++) {
			if (components.get(index).schematic().type() == CircuitComponentType.LAMP) {
				lamps.add(index);
			}
		}
		return lamps;
	}

	private static boolean[] inputValues(int count, int mask) {
		boolean[] values = new boolean[count];
		for (int index = 0; index < count; index++) {
			values[index] = ((mask >> index) & 1) == 1;
		}
		return values;
	}

	private static String booleanVector(boolean[] values) {
		StringBuilder text = new StringBuilder();
		for (boolean value : values) {
			text.append(value ? '1' : '0');
		}
		return text.toString();
	}

	@FunctionalInterface
	private interface TruthFunction {
		boolean apply(boolean[] values);
	}

	private static MatrixTestResult testEncoderMatrix(int inputs, int outputs, Structure generated) {
		Set<SchematicPoint> expectedTorches = new HashSet<>();
		Set<SchematicPoint> actualTorches = new HashSet<>();
		List<String> missing = new ArrayList<>();
		List<String> extra = new ArrayList<>();
		List<String> reachProblems = new ArrayList<>();
		int activeOutputs = requiredBinaryOutputs(inputs);

		for (int input = 0; input < inputs; input++) {
			for (int output = 0; output < outputs; output++) {
				if (((input >> output) & 1) == 1) {
					expectedTorches.add(new SchematicPoint(expectedOutputContactX(output, outputs), 2, expectedContactZ(input)));
				}
			}
		}

		for (Map.Entry<SchematicPoint, String> entry : generated.blocks().entrySet()) {
			if (entry.getValue().startsWith("minecraft:redstone_wall_torch[facing=north")) {
				actualTorches.add(entry.getKey());
			}
		}

		for (SchematicPoint expected : expectedTorches) {
			if (!actualTorches.contains(expected)) {
				missing.add(format(expected, "missing north-facing matrix torch"));
			}
			if (!isOutputBusReachable(expected.x(), expected.z(), generated.blocks())) {
				reachProblems.add(format(expected, "output bus signal path is longer than 15 blocks without a repeater"));
			}
		}

		for (SchematicPoint actual : actualTorches) {
			if (!expectedTorches.contains(actual)) {
				extra.add(format(actual, "unexpected north-facing matrix torch"));
			}
		}

		StringBuilder text = new StringBuilder();
		text.append("Encoder ").append(inputs).append("->").append(outputs).append(" matrix logic test\n");
		text.append("Generated size: ").append(size(generated.size())).append('\n');
		text.append("Binary outputs required: ").append(activeOutputs).append('\n');
		text.append("Provided outputs: ").append(outputs).append('\n');
		if (outputs > activeOutputs) {
			text.append("Unused high outputs: ").append(outputs - activeOutputs).append(" (valid for binary encoding, left without matrix torches)\n");
		}
		text.append('\n');
		appendMatrixList(text, "Missing torches", missing);
		appendMatrixList(text, "Extra torches", extra);
		appendMatrixList(text, "Reach problems", reachProblems);
		boolean ok = missing.isEmpty() && extra.isEmpty() && reachProblems.isEmpty();
		text.append("\nResult: ").append(ok ? "OK" : "FAIL").append('\n');
		return new MatrixTestResult(ok, text.toString());
	}

	private static void appendMatrixList(StringBuilder text, String label, List<String> values) {
		text.append(label).append(": ").append(values.size()).append('\n');
		values.stream().limit(MAX_EXAMPLES).forEach(value -> text.append("  ").append(value).append('\n'));
		if (values.size() > MAX_EXAMPLES) {
			text.append("  ... ").append(values.size() - MAX_EXAMPLES).append(" more\n");
		}
	}

	private static boolean isOutputBusReachable(int outputX, int contactZ, Map<SchematicPoint, String> blocks) {
		int segmentLength = contactZ;
		for (int z = contactZ - 1; z >= 1; z--) {
			String state = blocks.get(new SchematicPoint(outputX, 1, z));
			if (state != null && state.startsWith("minecraft:repeater[") && state.contains("facing=north")) {
				if (segmentLength - z > 15) {
					return false;
				}
				segmentLength = z - 1;
			}
		}
		return segmentLength <= 15;
	}

	private static int requiredBinaryOutputs(int inputs) {
		int values = Math.max(1, inputs - 1);
		return Math.max(1, Integer.SIZE - Integer.numberOfLeadingZeros(values));
	}

	private static int expectedOutputContactX(int output, int outputs) {
		return 1 + (outputs - 1 - output) * 2;
	}

	private static int expectedContactZ(int input) {
		return input * 2;
	}

	private static int row(int index) {
		return 1 + index * 2;
	}

	private static int outputColumn(int output) {
		return 1 + output * 2;
	}

	private static SimulationReport simulateEncoder(int inputs, int outputs, Structure structure) {
		RedstoneCircuit circuit = RedstoneCircuit.from(structure);
		List<String> failures = new ArrayList<>();
		StringBuilder text = new StringBuilder();
		text.append("Encoder ").append(inputs).append("->").append(outputs).append(" redstone simulation\n");
		text.append("Generated size: ").append(size(structure.size())).append('\n');
		text.append("Inputs simulated: ").append(inputs).append('\n');
		text.append("Outputs sampled: ").append(outputs).append("\n\n");

		for (int input = 0; input < inputs; input++) {
			SimulationState state = circuit.simulate(input, Math.max(96, structure.size().z() * 3));
			int actual = 0;
			for (int output = 0; output < outputs; output++) {
				int outputX = expectedOutputContactX(output, outputs);
				int signal = state.signalAt(new SchematicPoint(outputX, 1, 0));
				if (signal > 0) {
					actual |= 1 << output;
				}
			}
			int expected = input & outputMask(outputs);
			if (actual != expected) {
				failures.add("input " + input + ": expected " + expected + " (" + binary(expected, outputs) + "), got " + actual + " (" + binary(actual, outputs) + ")");
			}
		}

		appendMatrixList(text, "Failures", failures);
		boolean ok = failures.isEmpty();
		text.append("\nResult: ").append(ok ? "OK" : "FAIL").append('\n');
		return new SimulationReport(ok, text.toString());
	}

	private static SimulationReport simulateDecoder(int inputs, int outputs, Structure structure) {
		List<String> failures = new ArrayList<>();
		StringBuilder text = new StringBuilder();
		Map<SchematicPoint, Integer> expectedConditions = expectedDecoderConditions(inputs, outputs);
		Map<SchematicPoint, Integer> actualConditions = structure.blocks().entrySet().stream()
				.filter(entry -> entry.getKey().y() == 2 || entry.getKey().y() == 3)
				.filter(entry -> entry.getValue().startsWith("minecraft:redstone_wall_torch[")
						|| entry.getValue().startsWith("minecraft:repeater["))
				.collect(Collectors.toMap(
						entry -> decoderConditionKey(entry.getKey(), entry.getValue()),
						entry -> entry.getValue().startsWith("minecraft:redstone_wall_torch[") ? 1 : 0,
						(left, right) -> left,
						LinkedHashMap::new
				));

		text.append("Decoder ").append(inputs).append("->").append(outputs).append(" logical one-hot simulation\n");
		text.append("Generated size: ").append(size(structure.size())).append('\n');
		text.append("Algorithm: MattBatWings-style combination lock generated from the base structure.\n");
		text.append("Condition repeater = required 0. Removed repeater + new top dust + side torch = required 1.\n");
		text.append("Expected conditions: ").append(expectedConditions.size()).append('\n');
		text.append("Actual conditions: ").append(actualConditions.size()).append("\n\n");

		for (Map.Entry<SchematicPoint, Integer> expected : expectedConditions.entrySet()) {
			Integer actual = actualConditions.get(expected.getKey());
			if (actual == null) {
				failures.add(format(expected.getKey(), "missing decoder condition " + expected.getValue()));
			} else if (!actual.equals(expected.getValue())) {
				failures.add(format(expected.getKey(), "expected condition " + expected.getValue() + ", got " + actual));
			}
		}
		for (SchematicPoint actual : actualConditions.keySet()) {
			if (!expectedConditions.containsKey(actual)) {
				failures.add(format(actual, "unexpected decoder condition"));
			}
		}
		failures.addAll(decoderOutputBusReachProblems(outputs, structure));

		int values = decoderTestValueCount(inputs, outputs);
		for (int value = 0; value < values; value++) {
			List<Integer> activeOutputs = simulateLogicalDecoder(inputs, outputs, actualConditions, value);
			List<Integer> expectedOutputs = expectedDecoderSlots(inputs, outputs, value);
			if (!activeOutputs.equals(expectedOutputs)) {
				failures.add("input value " + value + ": expected " + expectedOutputs + ", got " + activeOutputs);
			}
		}

		appendMatrixList(text, "Failures", failures);
		boolean ok = failures.isEmpty();
		text.append("\nResult: ").append(ok ? "OK" : "FAIL").append('\n');
		return new SimulationReport(ok, text.toString());
	}

	private static SchematicPoint decoderConditionKey(SchematicPoint point, String state) {
		if (state.startsWith("minecraft:redstone_wall_torch[")) {
			return new SchematicPoint(point.x() + 1, 2, point.z() - 1);
		}
		return point;
	}

	private static List<String> decoderOutputBusReachProblems(int outputs, Structure structure) {
		List<String> failures = new ArrayList<>();
		int outputZ = structure.size().z() - 1;
		for (int output = 0; output < outputs; output++) {
			int x = outputColumn(output);
			int runLength = 0;
			for (int z = 1; z < outputZ - 1; z++) {
				String state = structure.blocks().get(new SchematicPoint(x, 1, z));
				if (state == null) {
					continue;
				}
				if (state.startsWith("minecraft:repeater[")) {
					runLength = 0;
					continue;
				}
				if (state.startsWith("minecraft:redstone_wire[")) {
					runLength++;
					if (runLength > 15) {
						failures.add(format(new SchematicPoint(x, 1, z), "decoder output bus " + output + " exceeds 15 redstone dust blocks without a repeater"));
						break;
					}
				}
			}
		}
		return failures;
	}

	private static Map<SchematicPoint, Integer> expectedDecoderConditions(int inputs, int outputs) {
		Map<SchematicPoint, Integer> expected = new LinkedHashMap<>();
		for (int output = 0; output < outputs; output++) {
			int code = decoderCodeForSlot(output, inputs, outputs);
			for (int bit = 0; bit < inputs; bit++) {
				if (shouldSkipDecoderCondition(inputs, outputs, output, bit)) {
					continue;
				}
				expected.put(new SchematicPoint(outputColumn(output) + 1, 2, row(bit)), (code >> bit) & 1);
			}
		}
		return expected;
	}

	private static List<Integer> simulateLogicalDecoder(int inputs, int outputs, Map<SchematicPoint, Integer> conditions, int value) {
		List<Integer> active = new ArrayList<>();
		for (int output = 0; output < outputs; output++) {
			boolean matches = true;
			for (int bit = 0; bit < inputs; bit++) {
				if (shouldSkipDecoderCondition(inputs, outputs, output, bit)) {
					continue;
				}
				Integer required = conditions.get(new SchematicPoint(outputColumn(output) + 1, 2, row(bit)));
				if (required == null || required != ((value >> bit) & 1)) {
					matches = false;
					break;
				}
			}
			if (matches) {
				active.add(output);
			}
		}
		return active;
	}

	private static List<Integer> expectedDecoderSlots(int inputs, int outputs, int value) {
		List<Integer> slots = new ArrayList<>();
		for (int slot = 0; slot < outputs; slot++) {
			if (decoderCodeForSlot(slot, inputs, outputs) == value) {
				slots.add(slot);
			}
		}
		return slots;
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

	private static int decoderTestValueCount(int inputs, int outputs) {
		int fullRange = 1 << Math.min(inputs, 20);
		if (outputs < fullRange) {
			return outputs + 1;
		}
		return fullRange;
	}

	private static int outputMask(int outputs) {
		if (outputs >= Integer.SIZE - 1) {
			return Integer.MAX_VALUE;
		}
		return (1 << outputs) - 1;
	}

	private static String binary(int value, int outputs) {
		String binary = Integer.toBinaryString(value);
		int width = Math.min(outputs, 31);
		if (binary.length() >= width) {
			return binary;
		}
		return "0".repeat(width - binary.length()) + binary;
	}

	private static void appendComparison(StringBuilder text, Comparison comparison) {
		text.append("Match: ").append(comparison.match()).append('\n');
		text.append("Missing: ").append(comparison.missing().size()).append('\n');
		text.append("Extra: ").append(comparison.extra().size()).append('\n');
		text.append("Different: ").append(comparison.different().size()).append('\n');
		appendExamples(text, "Missing examples", comparison.missing());
		appendExamples(text, "Extra examples", comparison.extra());
		appendExamples(text, "Different examples", comparison.different());
	}

	private static void appendExamples(StringBuilder text, String label, List<String> examples) {
		if (examples.isEmpty()) {
			return;
		}

		text.append(label).append(":\n");
		examples.stream().limit(MAX_EXAMPLES).forEach(example -> text.append("  ").append(example).append('\n'));
		if (examples.size() > MAX_EXAMPLES) {
			text.append("  ... ").append(examples.size() - MAX_EXAMPLES).append(" more\n");
		}
	}

	private static Comparison diff(Map<SchematicPoint, String> expected, Map<SchematicPoint, String> actual) {
		List<String> missing = new ArrayList<>();
		List<String> extra = new ArrayList<>();
		List<String> different = new ArrayList<>();

		for (Map.Entry<SchematicPoint, String> entry : expected.entrySet()) {
			String actualState = actual.get(entry.getKey());
			if (actualState == null) {
				missing.add(format(entry.getKey(), entry.getValue()));
			} else if (!entry.getValue().equals(actualState)) {
				different.add(format(entry.getKey(), entry.getValue() + " != " + actualState));
			}
		}

		for (Map.Entry<SchematicPoint, String> entry : actual.entrySet()) {
			if (!expected.containsKey(entry.getKey())) {
				extra.add(format(entry.getKey(), entry.getValue()));
			}
		}

		return new Comparison(missing, extra, different);
	}

	private static Map<SchematicPoint, String> blockMap(List<SchematicBlock> blocks, boolean normalizePower) {
		Map<SchematicPoint, String> map = new TreeMap<>(pointComparator());
		for (SchematicBlock block : blocks) {
			map.put(block.position(), normalizePower ? normalizeState(block.blockStateString()) : block.blockStateString());
		}
		return map;
	}

	private static Map<SchematicPoint, String> normalizeMap(Map<SchematicPoint, String> blocks) {
		Map<SchematicPoint, String> normalized = new TreeMap<>(pointComparator());
		blocks.forEach((point, state) -> normalized.put(point, normalizeState(state)));
		return normalized;
	}

	private static Structure readStructure(Path path) throws IOException {
		try (InputStream stream = Files.newInputStream(path)) {
			CompoundTag root = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
			ListTag sizeTag = root.getList("size", 3);
			LitematicSize size = new LitematicSize(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2));
			ListTag paletteTag = root.getList("palette", 10);
			List<String> palette = new ArrayList<>();
			for (int index = 0; index < paletteTag.size(); index++) {
				palette.add(stateString(paletteTag.getCompound(index)));
			}

			Map<SchematicPoint, String> blocks = new TreeMap<>(pointComparator());
			ListTag blockTags = root.getList("blocks", 10);
			for (int index = 0; index < blockTags.size(); index++) {
				CompoundTag block = blockTags.getCompound(index);
				ListTag pos = block.getList("pos", 3);
				blocks.put(new SchematicPoint(pos.getInt(0), pos.getInt(1), pos.getInt(2)), palette.get(block.getInt("state")));
			}

			return new Structure(size, blocks);
		}
	}

	private static String stateString(CompoundTag stateTag) {
		String name = stateTag.getString("Name");
		CompoundTag propertiesTag = stateTag.getCompound("Properties");
		if (propertiesTag.isEmpty()) {
			return name;
		}

		Map<String, String> properties = new TreeMap<>();
		for (String key : propertiesTag.getAllKeys()) {
			properties.put(key, propertiesTag.getString(key));
		}

		StringBuilder state = new StringBuilder(name).append('[');
		boolean first = true;
		for (Map.Entry<String, String> entry : properties.entrySet()) {
			if (!first) {
				state.append(',');
			}
			state.append(entry.getKey()).append('=').append(entry.getValue());
			first = false;
		}
		return state.append(']').toString();
	}

	private static String normalizeState(String state) {
		if (!state.startsWith("minecraft:redstone_wire[")) {
			return state;
		}

		Map<String, String> properties = new LinkedHashMap<>();
		String body = state.substring(state.indexOf('[') + 1, state.length() - 1);
		for (String part : body.split(",")) {
			String[] keyValue = part.split("=", 2);
			if (keyValue.length == 2 && !"power".equals(keyValue[0])) {
				properties.put(keyValue[0], keyValue[1]);
			}
		}
		return "minecraft:redstone_wire" + properties.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.map(entry -> entry.getKey() + "=" + entry.getValue())
				.collect(java.util.stream.Collectors.joining(",", "[", "]"));
	}

	private static Comparator<SchematicPoint> pointComparator() {
		return Comparator.comparingInt(SchematicPoint::y)
				.thenComparingInt(SchematicPoint::z)
				.thenComparingInt(SchematicPoint::x);
	}

	private static String format(SchematicPoint point, String state) {
		return "(" + point.x() + "," + point.y() + "," + point.z() + ") " + state;
	}

	private static String size(LitematicSize size) {
		return size.x() + "x" + size.y() + "x" + size.z();
	}

	private static String safeFileName(String title) {
		return title.toLowerCase().replace("->", "_to_").replaceAll("[^a-z0-9_]+", "_");
	}

	private record Structure(LitematicSize size, Map<SchematicPoint, String> blocks) {
	}

	private record PaletteState(String blockName, Map<String, String> properties) {
	}

	private record MatrixSize(int inputs, int outputs) {
	}

	private record MatrixTestResult(boolean ok, String text) {
	}

	private record SimulationReport(boolean ok, String text) {
	}

	private record PresetSimulationReport(boolean ok, String text) {
	}

	private static final class RedstoneCircuit {
		private final Map<SchematicPoint, SimBlock> blocks;
		private final List<SchematicPoint> inputWires;
		private final List<SchematicPoint> wires;
		private final List<SchematicPoint> torches;
		private final List<SchematicPoint> repeaters;
		private final Map<SchematicPoint, List<SchematicPoint>> torchesByPoweredWire;
		private final Map<SchematicPoint, List<SchematicPoint>> repeatersByPoweredWire;

		private RedstoneCircuit(Map<SchematicPoint, SimBlock> blocks, List<SchematicPoint> inputWires, List<SchematicPoint> wires,
								List<SchematicPoint> torches, List<SchematicPoint> repeaters,
								Map<SchematicPoint, List<SchematicPoint>> torchesByPoweredWire,
								Map<SchematicPoint, List<SchematicPoint>> repeatersByPoweredWire) {
			this.blocks = blocks;
			this.inputWires = inputWires;
			this.wires = wires;
			this.torches = torches;
			this.repeaters = repeaters;
			this.torchesByPoweredWire = torchesByPoweredWire;
			this.repeatersByPoweredWire = repeatersByPoweredWire;
		}

		private static RedstoneCircuit from(Structure structure) {
			Map<SchematicPoint, SimBlock> blocks = new TreeMap<>(pointComparator());
			List<SchematicPoint> inputWires = new ArrayList<>();
			List<SchematicPoint> wires = new ArrayList<>();
			List<SchematicPoint> torches = new ArrayList<>();
			List<SchematicPoint> repeaters = new ArrayList<>();
			for (Map.Entry<SchematicPoint, String> entry : structure.blocks().entrySet()) {
				SimBlock block = SimBlock.parse(entry.getValue());
				blocks.put(entry.getKey(), block);
				if ("minecraft:lime_concrete".equals(block.name())) {
					inputWires.add(new SchematicPoint(entry.getKey().x(), entry.getKey().y() + 1, entry.getKey().z()));
				} else if (block.isWire()) {
					wires.add(entry.getKey());
				} else if (block.isTorch()) {
					torches.add(entry.getKey());
				} else if (block.isRepeater()) {
					repeaters.add(entry.getKey());
				}
			}
			inputWires.sort(pointComparator());
			wires.sort(pointComparator());
			torches.sort(pointComparator());
			repeaters.sort(pointComparator());
			return new RedstoneCircuit(blocks, inputWires, wires, torches, repeaters, torchesByPoweredWire(blocks, torches), repeatersByPoweredWire(blocks, repeaters));
		}

		private static Map<SchematicPoint, List<SchematicPoint>> torchesByPoweredWire(Map<SchematicPoint, SimBlock> blocks, List<SchematicPoint> torches) {
			Map<SchematicPoint, List<SchematicPoint>> powered = new LinkedHashMap<>();
			for (SchematicPoint torch : torches) {
				SimBlock torchBlock = blocks.get(torch);
				Direction attachedDirection = Direction.byName(torchBlock.property("facing")).opposite();
				List<SchematicPoint> candidates = new ArrayList<>();
				candidates.add(new SchematicPoint(torch.x(), torch.y() - 1, torch.z()));
				candidates.add(new SchematicPoint(torch.x(), torch.y() + 1, torch.z()));
				candidates.add(new SchematicPoint(torch.x(), torch.y() + 2, torch.z()));
				for (Direction direction : Direction.HORIZONTAL) {
					if (direction != attachedDirection) {
						candidates.add(direction.offset(torch));
					}
				}
				for (SchematicPoint wire : candidates) {
					SimBlock block = blocks.get(wire);
					if (block != null && block.isWire()) {
						powered.computeIfAbsent(wire, ignored -> new ArrayList<>()).add(torch);
					}
				}
			}
			return powered;
		}

		private static Map<SchematicPoint, List<SchematicPoint>> repeatersByPoweredWire(Map<SchematicPoint, SimBlock> blocks, List<SchematicPoint> repeaters) {
			Map<SchematicPoint, List<SchematicPoint>> powered = new LinkedHashMap<>();
			for (SchematicPoint repeater : repeaters) {
				SimBlock block = blocks.get(repeater);
				Direction facing = Direction.byName(block.property("facing"));
				SchematicPoint target = facing.offset(repeater);
				SimBlock targetBlock = blocks.get(target);
				if (targetBlock != null && targetBlock.isWire()) {
					powered.computeIfAbsent(target, ignored -> new ArrayList<>()).add(repeater);
				}
			}
			return powered;
		}

		private SimulationState simulate(int activeInput, int maxTicks) {
			Set<SchematicPoint> externalPower = new HashSet<>();
			if (activeInput >= 0 && activeInput < inputWires.size()) {
				externalPower.add(inputWires.get(activeInput));
			}
			return simulatePoweredWires(externalPower, maxTicks);
		}

		private SimulationState simulate(Set<Integer> activeInputs, int maxTicks) {
			Set<SchematicPoint> externalPower = new HashSet<>();
			for (int input : activeInputs) {
				if (input >= 0 && input < inputWires.size()) {
					externalPower.add(inputWires.get(input));
				}
			}
			return simulatePoweredWires(externalPower, maxTicks);
		}

		private SimulationState simulatePoweredWires(Set<SchematicPoint> externalPower, int maxTicks) {
			SimulationState state = SimulationState.initial(blocks, externalPower);
			for (int tick = 0; tick < maxTicks; tick++) {
				SimulationState next = step(state, externalPower);
				if (next.sameAs(state)) {
					return next;
				}
				state = next;
			}
			return state;
		}

		private SimulationState step(SimulationState previous, Set<SchematicPoint> externalPower) {
			Map<SchematicPoint, Integer> power = new LinkedHashMap<>();
			Map<SchematicPoint, Boolean> litTorches = new LinkedHashMap<>();
			Map<SchematicPoint, Integer> repeaterOutput = new LinkedHashMap<>();

			for (SchematicPoint point : torches) {
				SimBlock block = blocks.get(point);
				if (block.isTorch()) {
					Direction facing = Direction.byName(block.property("facing"));
					SchematicPoint attached = facing.opposite().offset(point);
					litTorches.put(point, !isSolidBlockPowered(attached, previous));
				}
			}

			for (SchematicPoint point : repeaters) {
				SimBlock block = blocks.get(point);
				Direction facing = Direction.byName(block.property("facing"));
				SchematicPoint input = facing.opposite().offset(point);
				repeaterOutput.put(point, previous.signalAt(input) > 0 ? 15 : 0);
			}

			for (SchematicPoint point : wires) {
				SimBlock block = blocks.get(point);
				int strongest = externalPower.contains(point) ? 15 : 0;
				strongest = Math.max(strongest, torchPowerIntoWire(point, litTorches));
				strongest = Math.max(strongest, repeaterPowerIntoWire(point, repeaterOutput));

				for (Direction direction : Direction.HORIZONTAL) {
					SchematicPoint neighbor = direction.offset(point);
					SimBlock neighborBlock = blocks.get(neighbor);
					if (neighborBlock != null && neighborBlock.isWire() && connects(block, direction) && connects(neighborBlock, direction.opposite())) {
						strongest = Math.max(strongest, Math.max(0, previous.signalAt(neighbor) - 1));
					}
				}
				power.put(point, strongest);
			}

			return new SimulationState(power, litTorches, repeaterOutput);
		}

		private boolean isSolidBlockPowered(SchematicPoint solidBlock, SimulationState previous) {
			SchematicPoint above = new SchematicPoint(solidBlock.x(), solidBlock.y() + 1, solidBlock.z());
			if (previous.signalAt(above) > 0 && isWire(above)) {
				return true;
			}

			for (Direction direction : Direction.HORIZONTAL) {
				SchematicPoint neighbor = direction.offset(solidBlock);
				if (previous.signalAt(neighbor) > 0 && isWire(neighbor)) {
					return true;
				}
				SimBlock neighborBlock = blocks.get(neighbor);
				if (neighborBlock != null && neighborBlock.isRepeater()) {
					Direction repeaterFacing = Direction.byName(neighborBlock.property("facing"));
					if (repeaterFacing.offset(neighbor).equals(solidBlock) && previous.repeaterSignalAt(neighbor) > 0) {
						return true;
					}
				}
			}
			return false;
		}

		private int torchPowerIntoWire(SchematicPoint wire, Map<SchematicPoint, Boolean> litTorches) {
			for (SchematicPoint torch : torchesByPoweredWire.getOrDefault(wire, List.of())) {
				if (litTorches.getOrDefault(torch, false)) {
					return 15;
				}
			}
			return 0;
		}

		private int repeaterPowerIntoWire(SchematicPoint wire, Map<SchematicPoint, Integer> repeaterOutput) {
			for (SchematicPoint repeater : repeatersByPoweredWire.getOrDefault(wire, List.of())) {
				if (repeaterOutput.getOrDefault(repeater, 0) > 0) {
					return 15;
				}
			}
			return 0;
		}

		private boolean isWire(SchematicPoint point) {
			SimBlock block = blocks.get(point);
			return block != null && block.isWire();
		}

		private boolean connects(SimBlock wire, Direction direction) {
			String value = wire.property(direction.name().toLowerCase());
			return !"none".equals(value);
		}
	}

	private record SimulationState(Map<SchematicPoint, Integer> power, Map<SchematicPoint, Boolean> litTorches, Map<SchematicPoint, Integer> repeaterOutput) {
		private static SimulationState initial(Map<SchematicPoint, SimBlock> blocks, Set<SchematicPoint> externalPower) {
			Map<SchematicPoint, Integer> power = new LinkedHashMap<>();
			Map<SchematicPoint, Boolean> litTorches = new LinkedHashMap<>();
			Map<SchematicPoint, Integer> repeaterOutput = new LinkedHashMap<>();
			for (Map.Entry<SchematicPoint, SimBlock> entry : blocks.entrySet()) {
				if (entry.getValue().isWire()) {
					power.put(entry.getKey(), externalPower.contains(entry.getKey()) ? 15 : 0);
				} else if (entry.getValue().isTorch()) {
					litTorches.put(entry.getKey(), Boolean.parseBoolean(entry.getValue().property("lit")));
				} else if (entry.getValue().isRepeater()) {
					repeaterOutput.put(entry.getKey(), Boolean.parseBoolean(entry.getValue().property("powered")) ? 15 : 0);
				}
			}
			return new SimulationState(power, litTorches, repeaterOutput);
		}

		private int signalAt(SchematicPoint point) {
			return Math.max(power.getOrDefault(point, 0), repeaterOutput.getOrDefault(point, 0));
		}

		private int repeaterSignalAt(SchematicPoint point) {
			return repeaterOutput.getOrDefault(point, 0);
		}

		private boolean torchLitAt(SchematicPoint point) {
			return litTorches.getOrDefault(point, false);
		}

		private boolean outputActiveAt(SchematicPoint point) {
			return torchLitAt(point) || signalAt(point) > 0
					|| signalAt(new SchematicPoint(point.x(), point.y(), point.z() - 1)) > 0
					|| signalAt(new SchematicPoint(point.x(), point.y(), point.z() + 1)) > 0
					|| signalAt(new SchematicPoint(point.x() - 1, point.y(), point.z())) > 0
					|| signalAt(new SchematicPoint(point.x() + 1, point.y(), point.z())) > 0
					|| signalAt(new SchematicPoint(point.x(), point.y() + 1, point.z())) > 0;
		}

		private boolean sameAs(SimulationState other) {
			return power.equals(other.power) && litTorches.equals(other.litTorches) && repeaterOutput.equals(other.repeaterOutput);
		}
	}

	private record SimBlock(String name, Map<String, String> properties) {
		private static SimBlock parse(String state) {
			int propertyStart = state.indexOf('[');
			if (propertyStart < 0) {
				return new SimBlock(state, Map.of());
			}
			Map<String, String> properties = new LinkedHashMap<>();
			String body = state.substring(propertyStart + 1, state.length() - 1);
			for (String part : body.split(",")) {
				String[] keyValue = part.split("=", 2);
				if (keyValue.length == 2) {
					properties.put(keyValue[0], keyValue[1]);
				}
			}
			return new SimBlock(state.substring(0, propertyStart), properties);
		}

		private boolean isWire() {
			return "minecraft:redstone_wire".equals(name);
		}

		private boolean isTorch() {
			return "minecraft:redstone_wall_torch".equals(name);
		}

		private boolean isRepeater() {
			return "minecraft:repeater".equals(name);
		}

		private String property(String name) {
			return properties.getOrDefault(name, "");
		}
	}

	private enum Direction {
		NORTH(0, 0, -1),
		SOUTH(0, 0, 1),
		WEST(-1, 0, 0),
		EAST(1, 0, 0);

		private static final Direction[] HORIZONTAL = {NORTH, SOUTH, WEST, EAST};
		private final int x;
		private final int y;
		private final int z;

		Direction(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		private static Direction byName(String name) {
			for (Direction direction : values()) {
				if (direction.name().equalsIgnoreCase(name)) {
					return direction;
				}
			}
			throw new IllegalArgumentException("Unknown direction: " + name);
		}

		private Direction opposite() {
			return switch (this) {
				case NORTH -> SOUTH;
				case SOUTH -> NORTH;
				case WEST -> EAST;
				case EAST -> WEST;
			};
		}

		private SchematicPoint offset(SchematicPoint point) {
			return new SchematicPoint(point.x() + x, point.y() + y, point.z() + z);
		}
	}

	private record Comparison(List<String> missing, List<String> extra, List<String> different) {
		boolean match() {
			return missing.isEmpty() && extra.isEmpty() && different.isEmpty();
		}
	}

	private record ComparisonReport(String fileName, String title, boolean exactMatch, boolean logicMatch, String text) {
	}
}
