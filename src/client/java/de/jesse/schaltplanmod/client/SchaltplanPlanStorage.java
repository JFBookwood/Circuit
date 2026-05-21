package de.jesse.schaltplanmod.client;

import de.jesse.schaltplanmod.SchaltplanMod;
import de.jesse.schaltplanmod.circuit.CircuitComponentType;
import de.jesse.schaltplanmod.circuit.LitematicSchematic;
import de.jesse.schaltplanmod.circuit.LitematicSize;
import de.jesse.schaltplanmod.circuit.PortRole;
import de.jesse.schaltplanmod.circuit.PortSide;
import de.jesse.schaltplanmod.circuit.SchematicBlock;
import de.jesse.schaltplanmod.circuit.SchematicPoint;
import de.jesse.schaltplanmod.circuit.SchematicPort;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SchaltplanPlanStorage {
	private static final String PLAN_EXTENSION = ".bcdi";
	private static final Path PLAN_FILE = configDir()
			.resolve("schaltplanmod")
			.resolve("plans")
			.resolve("default" + PLAN_EXTENSION);
	private static final Path LAST_PLAN_FILE = PLAN_FILE.getParent().resolve("last_plan.txt");
	private static final Path MODULE_FOLDER = PLAN_FILE.getParent().resolve("modules");
	private static final DateTimeFormatter MODULE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	private SchaltplanPlanStorage() {
	}

	private static Path configDir() {
		try {
			Path configDir = FabricLoader.getInstance().getConfigDir();
			if (configDir != null) {
				return configDir;
			}
		} catch (RuntimeException ignored) {
		}
		return Path.of("run", "config");
	}

	public static boolean save(List<PlacedComponent> components) {
		return saveAs(PLAN_FILE, components);
	}

	public static boolean saveNamed(String name, List<PlacedComponent> components) {
		return saveAs(planFileForName(name), components);
	}

	private static boolean saveAs(Path file, List<PlacedComponent> components) {
		List<String> lines = new ArrayList<>();
		lines.add("# Circuit plan v1");

		for (PlacedComponent component : components) {
			lines.add(component.schematic().type().name() + " " + component.gridX() + " " + component.gridZ() + " " + component.rotation() + " " + component.groupId() + " " + component.plane());
		}

		try {
			Files.createDirectories(file.getParent());
			Files.write(file, lines);
			writeNbt(file, components);
			rememberLastPlan(file);
			return true;
		} catch (IOException exception) {
			SchaltplanMod.LOGGER.warn("Could not save circuit: {}", file, exception);
			return false;
		}
	}

	public static List<PlacedComponent> load(Map<CircuitComponentType, LitematicSchematic> schematics) {
		Path last = lastPlanFile();
		return loadFile(last == null ? PLAN_FILE : last, schematics);
	}

	public static List<Path> listPlanFiles() {
		List<Path> files = new ArrayList<>();
		collectPlanFiles(PLAN_FILE.getParent(), files);
		collectPlanFiles(MODULE_FOLDER, files);
		files.sort(Comparator.comparing(path -> path.getFileName().toString()));
		return files;
	}

	public static Path saveModule(List<PlacedComponent> components) throws IOException {
		Files.createDirectories(MODULE_FOLDER);
		String name = "module_" + MODULE_TIME_FORMAT.format(LocalDateTime.now());
		Path file = MODULE_FOLDER.resolve(name + PLAN_EXTENSION);
		LitematicSchematic schematic = createModuleSchematic(name, components);
		Files.write(file, List.of("# Circuit module v1 - " + name, "# Embedded module data is stored in the .nbt sidecar."));
		writeNbt(file, List.of(new PlacedComponent(schematic, 0, 0)));
		return file;
	}

	public static LitematicSchematic createModuleSchematic(String name, List<PlacedComponent> components) {
		return flattenModule(name, components);
	}

	public static List<PlacedComponent> loadFile(Path file, Map<CircuitComponentType, LitematicSchematic> schematics) {
		if (file.getFileName().toString().endsWith(".nbt")) {
			return loadNbtFile(file, schematics);
		}

		Path sidecar = nbtSidecar(file);
		if (Files.exists(sidecar)) {
			List<PlacedComponent> nbtComponents = loadNbtFile(sidecar, schematics);
			if (!nbtComponents.isEmpty()) {
				return nbtComponents;
			}
		}

		List<PlacedComponent> components = new ArrayList<>();

		if (!Files.exists(file)) {
			return components;
		}

		try {
			for (String line : Files.readAllLines(file)) {
				if (line.isBlank() || line.startsWith("#")) {
					continue;
				}

				String[] parts = line.split("\\s+");
				if (parts.length != 3 && parts.length != 4 && parts.length != 5 && parts.length != 6) {
					continue;
				}

				CircuitComponentType type = CircuitComponentType.valueOf(parts[0]);
				LitematicSchematic schematic = schematics.get(type);
				if (schematic != null) {
					int rotation = parts.length >= 5 ? Integer.parseInt(parts[3]) : 0;
					long groupId = parts.length >= 5 ? Long.parseLong(parts[4]) : parts.length == 4 ? Long.parseLong(parts[3]) : 0L;
					int plane = parts.length == 6 ? Integer.parseInt(parts[5]) : 0;
					components.add(new PlacedComponent(schematic, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), rotation, groupId, plane));
				}
			}
		} catch (RuntimeException | IOException exception) {
			SchaltplanMod.LOGGER.warn("Could not load circuit: {}", file, exception);
		}

		return components;
	}

	public static Path planFile() {
		return PLAN_FILE;
	}

	public static Path planFolder() {
		return PLAN_FILE.getParent();
	}

	public static Path moduleFolder() {
		return MODULE_FOLDER;
	}

	public static boolean deletePlanFile(Path file) {
		try {
			if (!isDeletablePlanFile(file)) {
				return false;
			}
			Files.deleteIfExists(file);
			Files.deleteIfExists(nbtSidecar(file));
			return true;
		} catch (IOException exception) {
			SchaltplanMod.LOGGER.warn("Could not delete circuit: {}", file, exception);
			return false;
		}
	}

	public static void rememberOpenedPlan(Path file) {
		rememberLastPlan(file);
	}

	private static boolean isDeletablePlanFile(Path file) {
		if (file == null || !file.getFileName().toString().endsWith(PLAN_EXTENSION)) {
			return false;
		}
		Path normalized = file.toAbsolutePath().normalize();
		Path plans = PLAN_FILE.getParent().toAbsolutePath().normalize();
		Path modules = MODULE_FOLDER.toAbsolutePath().normalize();
		return normalized.startsWith(plans) || normalized.startsWith(modules);
	}

	private static Path planFileForName(String name) {
		String cleaned = name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT)
				.replaceAll("[^a-z0-9_ -]+", "")
				.replace(' ', '_')
				.replace('-', '_');
		if (cleaned.isBlank()) {
			cleaned = "untitled";
		}
		return PLAN_FILE.getParent().resolve(cleaned + PLAN_EXTENSION);
	}

	private static void rememberLastPlan(Path file) {
		try {
			if (file.toAbsolutePath().normalize().startsWith(MODULE_FOLDER.toAbsolutePath().normalize())) {
				return;
			}
			Files.createDirectories(LAST_PLAN_FILE.getParent());
			Files.writeString(LAST_PLAN_FILE, file.toAbsolutePath().normalize().toString());
		} catch (IOException exception) {
			SchaltplanMod.LOGGER.warn("Could not remember last circuit: {}", file, exception);
		}
	}

	private static Path lastPlanFile() {
		try {
			if (!Files.exists(LAST_PLAN_FILE)) {
				return null;
			}
			Path file = Path.of(Files.readString(LAST_PLAN_FILE).trim());
			return Files.exists(file) ? file : null;
		} catch (RuntimeException | IOException exception) {
			SchaltplanMod.LOGGER.warn("Could not read last circuit file.", exception);
			return null;
		}
	}

	private static void collectPlanFiles(Path folder, List<Path> files) {
		if (!Files.isDirectory(folder)) {
			return;
		}

		try (var stream = Files.list(folder)) {
			stream.filter(path -> path.getFileName().toString().endsWith(PLAN_EXTENSION))
					.forEach(files::add);
		} catch (IOException exception) {
			SchaltplanMod.LOGGER.warn("Could not read plan folder: {}", folder, exception);
		}
	}

	private static void writeNbt(Path bcdiFile, List<PlacedComponent> components) throws IOException {
		CompoundTag root = new CompoundTag();
		ListTag componentTags = new ListTag();

		root.putString("format", "bcdi");
		root.putInt("version", 1);

		for (PlacedComponent component : components) {
			CompoundTag tag = new CompoundTag();
			tag.putString("type", component.schematic().type().name());
			tag.putInt("gridX", component.gridX());
			tag.putInt("gridZ", component.gridZ());
			tag.putInt("rotation", component.rotation());
			tag.putLong("groupId", component.groupId());
			tag.putInt("plane", component.plane());
			if (component.schematic().customDisplayName() != null) {
				tag.put("schematic", writeSchematic(component.schematic()));
			}
			componentTags.add(tag);
		}

		root.put("components", componentTags);
		NbtIo.writeCompressed(root, nbtSidecar(bcdiFile));
	}

	private static LitematicSchematic flattenModule(String name, List<PlacedComponent> components) {
		List<SchematicBlock> transformedBlocks = new ArrayList<>();
		List<SchematicPort> transformedPorts = new ArrayList<>();
		int minX = 0;
		int minZ = 0;
		int maxX = 0;
		int maxY = 0;
		int maxZ = 0;
		boolean anyBlock = false;

		for (PlacedComponent component : components) {
			for (SchematicBlock block : component.schematic().blocks()) {
				SchematicPoint point = transformPoint(component, block.position());
				transformedBlocks.add(new SchematicBlock(point, block.blockName(), rotateProperties(block.properties(), component.rotation())));
				minX = anyBlock ? Math.min(minX, point.x()) : point.x();
				minZ = anyBlock ? Math.min(minZ, point.z()) : point.z();
				maxX = anyBlock ? Math.max(maxX, point.x()) : point.x();
				maxY = anyBlock ? Math.max(maxY, point.y()) : point.y();
				maxZ = anyBlock ? Math.max(maxZ, point.z()) : point.z();
				anyBlock = true;
			}

			for (SchematicPort port : component.schematic().ports()) {
				transformedPorts.add(new SchematicPort(
						port.role(),
						transformPoint(component, port.markerPosition()),
						transformPoint(component, port.connectionPosition()),
						port.side()
				));
			}
		}

		int offsetX = minX < 0 ? -minX : 0;
		int offsetZ = minZ < 0 ? -minZ : 0;
		List<SchematicBlock> normalizedBlocks = transformedBlocks.stream()
				.map(block -> new SchematicBlock(new SchematicPoint(block.position().x() + offsetX, block.position().y(), block.position().z() + offsetZ), block.blockName(), block.properties()))
				.toList();
		List<SchematicPort> normalizedPorts = transformedPorts.stream()
				.map(port -> new SchematicPort(
						port.role(),
						new SchematicPoint(port.markerPosition().x() + offsetX, port.markerPosition().y(), port.markerPosition().z() + offsetZ),
						new SchematicPoint(port.connectionPosition().x() + offsetX, port.connectionPosition().y(), port.connectionPosition().z() + offsetZ),
						port.side()
				))
				.toList();

		LitematicSize size = new LitematicSize(Math.max(1, maxX - minX + 1), Math.max(1, maxY + 1), Math.max(1, maxZ - minZ + 1));
		return new LitematicSchematic(CircuitComponentType.CUSTOM_MODULE, name, size, normalizedBlocks, normalizedPorts, displayName(name));
	}

	private static SchematicPoint transformPoint(PlacedComponent component, SchematicPoint point) {
		SchematicPoint rotated = rotatePoint(point, component.schematic().size(), component.rotation());
		return new SchematicPoint(component.gridX() + rotated.x(), point.y(), component.gridZ() + rotated.z());
	}

	private static SchematicPoint rotatePoint(SchematicPoint point, LitematicSize size, int rotation) {
		return switch (rotation) {
			case 1 -> new SchematicPoint(size.z() - 1 - point.z(), point.y(), point.x());
			case 2 -> new SchematicPoint(size.x() - 1 - point.x(), point.y(), size.z() - 1 - point.z());
			case 3 -> new SchematicPoint(point.z(), point.y(), size.x() - 1 - point.x());
			default -> point;
		};
	}

	private static Map<String, String> rotateProperties(Map<String, String> properties, int rotation) {
		if (!properties.containsKey("facing")) {
			return properties;
		}
		Map<String, String> rotated = new LinkedHashMap<>(properties);
		rotated.put("facing", rotateFacing(properties.get("facing"), rotation));
		return Map.copyOf(rotated);
	}

	private static String rotateFacing(String facing, int rotation) {
		return switch (facing) {
			case "north" -> switch (rotation) {
				case 1 -> "east";
				case 2 -> "south";
				case 3 -> "west";
				default -> "north";
			};
			case "east" -> switch (rotation) {
				case 1 -> "south";
				case 2 -> "west";
				case 3 -> "north";
				default -> "east";
			};
			case "south" -> switch (rotation) {
				case 1 -> "west";
				case 2 -> "north";
				case 3 -> "east";
				default -> "south";
			};
			case "west" -> switch (rotation) {
				case 1 -> "north";
				case 2 -> "east";
				case 3 -> "south";
				default -> "west";
			};
			default -> facing;
		};
	}

	private static String displayName(String fileName) {
		String cleaned = fileName.replace('_', ' ').trim();
		return cleaned.isBlank() ? "Custom Module" : Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
	}

	private static List<PlacedComponent> loadNbtFile(Path file, Map<CircuitComponentType, LitematicSchematic> schematics) {
		List<PlacedComponent> components = new ArrayList<>();

		try {
			CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
			ListTag componentTags = root.getList("components", 10);

			for (int index = 0; index < componentTags.size(); index++) {
				CompoundTag tag = componentTags.getCompound(index);
				CircuitComponentType type = CircuitComponentType.valueOf(tag.getString("type"));
				LitematicSchematic schematic = tag.contains("schematic")
						? readSchematic(tag.getCompound("schematic"), type)
						: schematics.get(type);
				if (schematic != null) {
					components.add(new PlacedComponent(
							schematic,
							tag.getInt("gridX"),
							tag.getInt("gridZ"),
							tag.getInt("rotation"),
							tag.getLong("groupId"),
							tag.getInt("plane")
					));
				}
			}
		} catch (RuntimeException | IOException exception) {
			SchaltplanMod.LOGGER.warn("Could not load NBT circuit: {}", file, exception);
		}

		return components;
	}

	private static CompoundTag writeSchematic(LitematicSchematic schematic) {
		CompoundTag tag = new CompoundTag();
		CompoundTag size = new CompoundTag();
		ListTag blocks = new ListTag();
		ListTag ports = new ListTag();

		tag.putString("displayName", schematic.displayName());
		tag.putString("regionName", schematic.regionName());
		size.putInt("x", schematic.size().x());
		size.putInt("y", schematic.size().y());
		size.putInt("z", schematic.size().z());
		tag.put("size", size);

		for (SchematicBlock block : schematic.blocks()) {
			CompoundTag blockTag = new CompoundTag();
			writePoint(blockTag, "pos", block.position());
			blockTag.putString("blockName", block.blockName());
			CompoundTag properties = new CompoundTag();
			block.properties().forEach(properties::putString);
			blockTag.put("properties", properties);
			blocks.add(blockTag);
		}
		tag.put("blocks", blocks);

		for (SchematicPort port : schematic.ports()) {
			CompoundTag portTag = new CompoundTag();
			portTag.putString("role", port.role().name());
			portTag.putString("side", port.side().name());
			writePoint(portTag, "marker", port.markerPosition());
			writePoint(portTag, "connection", port.connectionPosition());
			ports.add(portTag);
		}
		tag.put("ports", ports);
		return tag;
	}

	private static LitematicSchematic readSchematic(CompoundTag tag, CircuitComponentType fallbackType) {
		CompoundTag sizeTag = tag.getCompound("size");
		LitematicSize size = new LitematicSize(sizeTag.getInt("x"), sizeTag.getInt("y"), sizeTag.getInt("z"));
		List<SchematicBlock> blocks = new ArrayList<>();
		List<SchematicPort> ports = new ArrayList<>();

		ListTag blockTags = tag.getList("blocks", 10);
		for (int index = 0; index < blockTags.size(); index++) {
			CompoundTag blockTag = blockTags.getCompound(index);
			CompoundTag propertiesTag = blockTag.getCompound("properties");
			java.util.LinkedHashMap<String, String> properties = new java.util.LinkedHashMap<>();
			for (String key : propertiesTag.getAllKeys()) {
				properties.put(key, propertiesTag.getString(key));
			}
			blocks.add(new SchematicBlock(readPoint(blockTag, "pos"), blockTag.getString("blockName"), Map.copyOf(properties)));
		}

		ListTag portTags = tag.getList("ports", 10);
		for (int index = 0; index < portTags.size(); index++) {
			CompoundTag portTag = portTags.getCompound(index);
			ports.add(new SchematicPort(
					PortRole.valueOf(portTag.getString("role")),
					readPoint(portTag, "marker"),
					readPoint(portTag, "connection"),
					PortSide.valueOf(portTag.getString("side"))
			));
		}

		return new LitematicSchematic(fallbackType, tag.getString("regionName"), size, blocks, ports, tag.getString("displayName"));
	}

	private static void writePoint(CompoundTag parent, String key, SchematicPoint point) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("x", point.x());
		tag.putInt("y", point.y());
		tag.putInt("z", point.z());
		parent.put(key, tag);
	}

	private static SchematicPoint readPoint(CompoundTag parent, String key) {
		CompoundTag tag = parent.getCompound(key);
		return new SchematicPoint(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
	}

	private static Path nbtSidecar(Path bcdiFile) {
		String fileName = bcdiFile.getFileName().toString();
		if (fileName.endsWith(PLAN_EXTENSION)) {
			fileName = fileName.substring(0, fileName.length() - PLAN_EXTENSION.length());
		}
		return bcdiFile.resolveSibling(fileName + ".nbt");
	}

}
