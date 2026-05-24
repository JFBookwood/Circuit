package de.jesse.schaltplanmod.client;

import de.jesse.schaltplanmod.SchaltplanMod;
import de.jesse.schaltplanmod.circuit.CircuitComponentCategory;
import de.jesse.schaltplanmod.circuit.CircuitComponentType;
import de.jesse.schaltplanmod.circuit.LitematicAnalyzer;
import de.jesse.schaltplanmod.circuit.LitematicSchematic;
import de.jesse.schaltplanmod.circuit.PortRole;
import de.jesse.schaltplanmod.circuit.SchematicBlock;
import de.jesse.schaltplanmod.circuit.SchematicPort;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;

public class SchaltplanEditorScreen extends Screen implements GeneratedCircuitReceiver {
	private static final int PANEL_WIDTH = 142;
	private static final int INSPECTOR_WIDTH = 172;
	private static final int TOOLBAR_HEIGHT = 58;
	private static final int GRID_ORIGIN_X_PADDING = 18;
	private static final int GRID_ORIGIN_Y_PADDING = 18;
	private static final int REPEATER_SPACING = 12;
	private static final int HISTORY_LIMIT = 64;
	private static final DateTimeFormatter MODULE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	private final Screen parent;
	private final Map<CircuitComponentType, LitematicSchematic> schematics = new EnumMap<>(CircuitComponentType.class);
	private final List<PlacedComponent> placedComponents = SchaltplanEditorState.placedComponents();
	private final Set<Integer> selectedComponentIndexes = new LinkedHashSet<>();
	private final ArrayDeque<List<PlacedComponent>> undoStack = new ArrayDeque<>();
	private final ArrayDeque<List<PlacedComponent>> redoStack = new ArrayDeque<>();
	private final Map<String, List<PlacedComponent>> moduleChildren = new HashMap<>();
	private CircuitComponentType selectedType = CircuitComponentType.AND;
	private EditorTool selectedTool = EditorTool.PLACE;
	private boolean detailedView;
	private int layer;
	private int activePlane;
	private int cellSize = 14;
	private int panX;
	private int panZ;
	private int paletteScroll;
	private int hoveredPaletteIndex = -1;
	private int selectedComponentIndex = -1;
	private int draggedComponentIndex = -1;
	private int dragOffsetGridX;
	private int dragOffsetGridZ;
	private boolean panning;
	private double panStartMouseX;
	private double panStartMouseY;
	private int panStartX;
	private int panStartZ;
	private boolean drawingWire;
	private int wireStartGridX;
	private int wireStartGridZ;
	private int wireEndGridX;
	private int wireEndGridZ;
	private boolean selectingArea;
	private int selectionStartGridX;
	private int selectionStartGridZ;
	private int selectionEndGridX;
	private int selectionEndGridZ;
	private String editingModuleName;
	private int editingModuleMinX;
	private int editingModuleMinZ;
	private int editingModuleMaxX;
	private int editingModuleMaxZ;
	private int lastRightClickedIndex = -1;
	private long lastRightClickMillis;
	private int lastLeftClickedIndex = -1;
	private long lastLeftClickMillis;
	private long nextGroupId = 1L;

	public SchaltplanEditorScreen(Screen parent) {
		super(Component.literal("Circuit Editor"));
		this.parent = parent;
		loadSchematics();
		loadDefaultPlanIfEditorIsEmpty();
		refreshNextGroupId();
	}

	@Override
	protected void init() {
		int x = PANEL_WIDTH + 8;
		int y = 6;
		addRenderableWidget(Button.builder(Component.literal(detailedView ? "Detailed View" : "Simple View"), button -> {
			detailedView = !detailedView;
			rebuildWidgets();
		}).bounds(x, y, 96, 20).build());
		x += 100;
		addRenderableWidget(Button.builder(Component.literal("- Plane"), button -> {
			activePlane = Math.max(0, activePlane - 1);
			clearSelection();
		}).bounds(x, y, 62, 20).build());
		x += 66;
		addRenderableWidget(Button.builder(Component.literal("+ Plane"), button -> {
			activePlane = Math.min(32, activePlane + 1);
			clearSelection();
		}).bounds(x, y, 62, 20).build());
		x += 66;
		addRenderableWidget(Button.builder(Component.literal("- Zoom"), button -> {
			cellSize = Math.max(6, cellSize - 2);
		}).bounds(x, y, 62, 20).build());
		x += 66;
		addRenderableWidget(Button.builder(Component.literal("+ Zoom"), button -> {
			cellSize = Math.min(30, cellSize + 2);
		}).bounds(x, y, 62, 20).build());

		x = PANEL_WIDTH + 8;
		y = 32;
		addRenderableWidget(Button.builder(Component.literal("Save"), button -> {
			minecraft.setScreen(new PlanSaveScreen(this, this));
		}).bounds(x, y, 44, 20).build());
		x += 48;
		addRenderableWidget(Button.builder(Component.literal("Load"), button -> {
			minecraft.setScreen(new PlanImportScreen(this, this, false));
		}).bounds(x, y, 44, 20).build());
		x += 48;
		addRenderableWidget(Button.builder(Component.literal("Import"), button -> {
			minecraft.setScreen(new PlanImportScreen(this, this, true));
		}).bounds(x, y, 54, 20).build());
		x += 58;
		addRenderableWidget(Button.builder(Component.literal("Gen"), button -> {
			minecraft.setScreen(GeneratorWarningState.shouldShow()
					? new GeneratorWarningScreen(this, this)
					: new EncoderDecoderGeneratorScreen(this, this));
		}).bounds(x, y, 38, 20).build());
		x += 42;
		addRenderableWidget(Button.builder(Component.literal("Module"), button -> {
			createModuleFromSelection();
		}).bounds(x, y, 58, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Sync"), button -> {
			syncToWorld();
		}).bounds(width - 172, y, 54, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Scan"), button -> {
			scanWorldRedstoneChanges();
		}).bounds(width - 232, y, 54, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Clear"), button -> {
			pushUndo();
			clearEditorAndWorld();
		}).bounds(width - 114, y, 50, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), button -> {
			minecraft.setScreen(parent);
		}).bounds(width - 58, y, 50, 20).build());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xff101318);
		graphics.enableScissor(PANEL_WIDTH, TOOLBAR_HEIGHT, Math.max(PANEL_WIDTH, width - INSPECTOR_WIDTH), height);
		renderGrid(graphics);
		renderPlacedComponents(graphics);
		renderWirePreview(graphics);
		renderSelectionBox(graphics);
		graphics.disableScissor();

		graphics.fill(0, 0, PANEL_WIDTH, height, 0xff191f27);
		graphics.fill(PANEL_WIDTH, 0, width, TOOLBAR_HEIGHT, 0xff171b22);
		renderInspector(graphics, mouseX, mouseY);
		renderPalette(graphics, mouseX, mouseY);
		renderStatus(graphics);

		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && mouseX < PANEL_WIDTH) {
			selectFromPalette((int) mouseY);
			return true;
		}

		if (button == 2 && isEditorArea(mouseX, mouseY)) {
			panning = true;
			panStartMouseX = mouseX;
			panStartMouseY = mouseY;
			panStartX = panX;
			panStartZ = panZ;
			return true;
		}

		if ((button == 0 || button == 1) && isEditorArea(mouseX, mouseY)) {
			if (button == 1) {
				int existingIndex = componentAt((int) mouseX, (int) mouseY);
				if (existingIndex >= 0 && handleModuleDoubleRightClick(existingIndex)) {
					return true;
				}
				if (existingIndex >= 0) {
					pushUndo();
					if (selectedComponentIndexes.contains(existingIndex) && selectedComponentIndexes.size() > 1) {
						removeSelectedComponents();
					} else {
						removeComponentOrGroup(existingIndex);
						selectedComponentIndex = -1;
						selectedComponentIndexes.clear();
					}
				}
				return true;
			}

			int existingIndex = componentAt((int) mouseX, (int) mouseY);
			if (selectedTool == EditorTool.POINTER && handlePointerDoubleClick(existingIndex, (int) mouseX, (int) mouseY)) {
				return true;
			}

			if (selectedTool == EditorTool.SELECT || Screen.hasShiftDown()) {
				selectingArea = true;
				selectionStartGridX = screenToGridX((int) mouseX);
				selectionStartGridZ = screenToGridZ((int) mouseY);
				selectionEndGridX = selectionStartGridX;
				selectionEndGridZ = selectionStartGridZ;
				selectedComponentIndexes.clear();
				selectedComponentIndex = -1;
				return true;
			}

			if (selectedTool == EditorTool.ROTATE) {
				if (existingIndex >= 0) {
					pushUndo();
					rotateComponentOrGroup(existingIndex);
					selectComponentOrGroup(existingIndex);
				}
				return true;
			}

			if (selectedTool == EditorTool.PLACE && isWireTool(selectedType)) {
				if (existingIndex >= 0 && placedComponents.get(existingIndex).groupId() != 0L) {
					GridPoint anchor = farthestWireEndpoint(placedComponents.get(existingIndex).groupId(), screenToGridX((int) mouseX), screenToGridZ((int) mouseY));
					pushUndo();
					removeComponentOrGroup(existingIndex);
					drawingWire = true;
					wireStartGridX = anchor.x();
					wireStartGridZ = anchor.z();
					wireEndGridX = screenToGridX((int) mouseX);
					wireEndGridZ = screenToGridZ((int) mouseY);
					return true;
				}

				drawingWire = true;
				wireStartGridX = screenToGridX((int) mouseX);
				wireStartGridZ = screenToGridZ((int) mouseY);
				wireEndGridX = wireStartGridX;
				wireEndGridZ = wireStartGridZ;
				return true;
			}

			if (existingIndex >= 0) {
				if (!selectedComponentIndexes.contains(existingIndex)) {
					selectComponentOrGroup(existingIndex);
				} else {
					selectedComponentIndex = existingIndex;
				}
				PlacedComponent placed = placedComponents.get(existingIndex);
				int clickedGridX = screenToGridX((int) mouseX);
				int clickedGridZ = screenToGridZ((int) mouseY);
				draggedComponentIndex = existingIndex;
				dragOffsetGridX = clickedGridX - placed.gridX();
				dragOffsetGridZ = clickedGridZ - placed.gridZ();
				pushUndo();
				return true;
			}

			if (selectedTool != EditorTool.PLACE) {
				selectedComponentIndex = -1;
				selectedComponentIndexes.clear();
				return true;
			}

			LitematicSchematic schematic = schematics.get(selectedType);
			if (schematic != null) {
				int gridX = screenToGridX((int) mouseX);
				int gridZ = screenToGridZ((int) mouseY);
				pushUndo();
				placedComponents.add(new PlacedComponent(schematic, gridX, gridZ).onPlane(activePlane));
				selectedComponentIndex = placedComponents.size() - 1;
				selectedComponentIndexes.clear();
				selectedComponentIndexes.add(selectedComponentIndex);
			}
			return true;
		}

		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (button == 2 && panning) {
			panX = panStartX + (int) Math.round((panStartMouseX - mouseX) / cellSize);
			panZ = panStartZ + (int) Math.round((panStartMouseY - mouseY) / cellSize);
			return true;
		}

		if (button == 0 && drawingWire) {
			wireEndGridX = screenToGridX((int) mouseX);
			wireEndGridZ = screenToGridZ((int) mouseY);
			return true;
		}

		if (button == 0 && selectingArea) {
			selectionEndGridX = screenToGridX((int) mouseX);
			selectionEndGridZ = screenToGridZ((int) mouseY);
			return true;
		}

		if (button == 0 && draggedComponentIndex >= 0 && draggedComponentIndex < placedComponents.size()) {
			PlacedComponent dragged = placedComponents.get(draggedComponentIndex);
			int gridX = screenToGridX((int) mouseX) - dragOffsetGridX;
			int gridZ = screenToGridZ((int) mouseY) - dragOffsetGridZ;
			int deltaX = gridX - dragged.gridX();
			int deltaZ = gridZ - dragged.gridZ();
			if (selectedComponentIndexes.size() > 1 && selectedComponentIndexes.contains(draggedComponentIndex)) {
				moveSelectedComponents(deltaX, deltaZ);
			} else {
				moveComponentOrGroup(draggedComponentIndex, deltaX, deltaZ);
			}
			return true;
		}

		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && drawingWire) {
			wireEndGridX = screenToGridX((int) mouseX);
			wireEndGridZ = screenToGridZ((int) mouseY);
			pushUndo();
			addWirePath(wireStartGridX, wireStartGridZ, wireEndGridX, wireEndGridZ);
			drawingWire = false;
			return true;
		}

		if (button == 0 && selectingArea) {
			selectionEndGridX = screenToGridX((int) mouseX);
			selectionEndGridZ = screenToGridZ((int) mouseY);
			selectComponentsInBox();
			selectingArea = false;
			return true;
		}

		if (button == 2 && panning) {
			panning = false;
			return true;
		}

		draggedComponentIndex = -1;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (isUndoShortcut(keyCode, modifiers)) {
			undo();
			return true;
		}
		if (isRedoShortcut(keyCode, modifiers)) {
			redo();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_R) {
			pushUndo();
			rotateHoveredOrLast();
			return true;
		}

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseX < PANEL_WIDTH) {
			paletteScroll = clampPaletteScroll(paletteScroll - (int) Math.signum(verticalAmount) * 18);
			return true;
		}

		if (isEditorArea(mouseX, mouseY)) {
			zoomAt((int) mouseX, (int) mouseY, verticalAmount);
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	private void loadSchematics() {
		for (CircuitComponentType type : CircuitComponentType.MENU_ORDER) {
			if (!type.hasBundledSchematic()) {
				continue;
			}
			try {
				schematics.put(type, LitematicAnalyzer.read(type));
			} catch (IOException exception) {
				SchaltplanMod.LOGGER.warn("Could not analyze schematic: {}", type.fileName(), exception);
			}
		}
	}

	private void renderPalette(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, "Tools & Components", 10, 10, 0xfff0f3f7, false);

		graphics.enableScissor(0, 25, PANEL_WIDTH, height);
		int y = 29 - paletteScroll;
		graphics.drawString(font, "Tools", 10, y, 0xff8fb4d8, false);
		y += 14;
		for (EditorTool tool : EditorTool.values()) {
			boolean selected = selectedTool == tool;
			boolean hovered = mouseX < PANEL_WIDTH && mouseY >= y && mouseY < y + 18;
			graphics.fill(8, y - 2, PANEL_WIDTH - 8, y + 16, selected ? 0xff31506f : hovered ? 0xff26303c : 0x00000000);
			graphics.drawString(font, tool.icon() + " " + tool.displayName(), 16, y + 3, selected ? 0xffffffff : 0xffc7d0dc, false);
			y += 20;
		}
		y += 8;

		for (CircuitComponentCategory category : CircuitComponentCategory.values()) {
			if (!hasPaletteItems(category)) {
				continue;
			}

			graphics.drawString(font, category.displayName(), 10, y, 0xff8fb4d8, false);
			y += 14;

			for (CircuitComponentType type : CircuitComponentType.MENU_ORDER) {
				if (!isPaletteType(type) || type.category() != category) {
					continue;
				}

				boolean selected = type == selectedType;
				boolean hovered = mouseX < PANEL_WIDTH && mouseY >= y && mouseY < y + 18;
				graphics.fill(8, y - 2, PANEL_WIDTH - 8, y + 16, selected ? 0xff31506f : hovered ? 0xff26303c : 0x00000000);
				graphics.drawString(font, type.displayName(), 16, y + 3, selected ? 0xffffffff : 0xffc7d0dc, false);
				y += 20;
			}

			y += 6;
		}
		graphics.disableScissor();
	}

	private void selectFromPalette(int mouseY) {
		paletteScroll = clampPaletteScroll(paletteScroll);
		int y = 29 - paletteScroll;
		y += 14;

		for (EditorTool tool : EditorTool.values()) {
			if (mouseY >= y && mouseY < y + 18) {
				selectedTool = tool;
				return;
			}
			y += 20;
		}
		y += 8;

		for (CircuitComponentCategory category : CircuitComponentCategory.values()) {
			if (!hasPaletteItems(category)) {
				continue;
			}

			y += 14;

			for (CircuitComponentType type : CircuitComponentType.MENU_ORDER) {
				if (!isPaletteType(type) || type.category() != category) {
					continue;
				}

				if (mouseY >= y && mouseY < y + 18) {
					selectedType = type;
					selectedTool = EditorTool.PLACE;
					return;
				}

				y += 20;
			}

			y += 6;
		}
	}

	private int paletteIndexAt(int mouseY) {
		paletteScroll = clampPaletteScroll(paletteScroll);
		int y = 29 - paletteScroll;
		int paletteIndex = 0;
		y += 14;
		for (EditorTool ignored : EditorTool.values()) {
			if (mouseY >= y && mouseY < y + 18) {
				return paletteIndex;
			}
			paletteIndex++;
			y += 20;
		}
		y += 8;

		for (CircuitComponentCategory category : CircuitComponentCategory.values()) {
			if (!hasPaletteItems(category)) {
				continue;
			}

			y += 14;
			for (CircuitComponentType type : CircuitComponentType.MENU_ORDER) {
				if (!isPaletteType(type) || type.category() != category) {
					continue;
				}
				if (mouseY >= y && mouseY < y + 18) {
					return paletteIndex;
				}
				paletteIndex++;
				y += 20;
			}
			y += 6;
		}

		return -1;
	}

	private int clampPaletteScroll(int scroll) {
		return Math.max(0, Math.min(scroll, Math.max(0, paletteContentHeight() - (height - 29))));
	}

	private int paletteContentHeight() {
		int contentHeight = 14 + EditorTool.values().length * 20 + 8;
		for (CircuitComponentCategory category : CircuitComponentCategory.values()) {
			if (!hasPaletteItems(category)) {
				continue;
			}

			contentHeight += 14;
			for (CircuitComponentType type : CircuitComponentType.MENU_ORDER) {
				if (isPaletteType(type) && type.category() == category) {
					contentHeight += 20;
				}
			}
			contentHeight += 6;
		}
		return contentHeight;
	}

	private static boolean hasPaletteItems(CircuitComponentCategory category) {
		for (CircuitComponentType type : CircuitComponentType.MENU_ORDER) {
			if (isPaletteType(type) && type.category() == category) {
				return true;
			}
		}
		return false;
	}

	private static boolean isPaletteType(CircuitComponentType type) {
		return type.hasBundledSchematic() && type != CircuitComponentType.FOUR_BIT_CALCULATOR_MEMORY;
	}

	private void renderGrid(GuiGraphics graphics) {
		int originX = gridOriginX();
		int originY = gridOriginY();
		int left = PANEL_WIDTH;
		int top = TOOLBAR_HEIGHT;
		int right = width - 10;
		int bottom = height - 10;
		int firstX = originX + Math.floorDiv(left - originX, cellSize) * cellSize;
		int firstY = originY + Math.floorDiv(top - originY, cellSize) * cellSize;

		for (int x = firstX; x < right; x += cellSize) {
			graphics.vLine(x, top, bottom, 0xff28313b);
		}

		for (int y = firstY; y < bottom; y += cellSize) {
			graphics.hLine(left, right, y, 0xff28313b);
		}
	}

	private void renderPlacedComponents(GuiGraphics graphics) {
		for (PlacedComponent placed : placedComponents) {
			if (placed.plane() >= activePlane) {
				continue;
			}
			if (detailedView) {
				renderDetailedComponent(graphics, placed, true);
			} else {
				renderSimpleComponent(graphics, placed, true);
			}
		}
		for (PlacedComponent placed : placedComponents) {
			if (placed.plane() != activePlane) {
				continue;
			}
			if (detailedView) {
				renderDetailedComponent(graphics, placed, false);
			} else {
				renderSimpleComponent(graphics, placed, false);
			}
		}
		if (!detailedView) {
			renderWireGroupLabels(graphics);
		}
	}

	private void renderSimpleComponent(GuiGraphics graphics, PlacedComponent placed, boolean ghost) {
		LitematicSchematic schematic = placed.schematic();
		int x = gridOriginX() + placed.gridX() * cellSize;
		int y = gridOriginY() + placed.gridZ() * cellSize;
		int w = Math.max(cellSize, rotatedSizeX(placed) * cellSize);
		int h = Math.max(cellSize, rotatedSizeZ(placed) * cellSize);

		int componentIndex = placedComponents.indexOf(placed);
		graphics.fill(x, y, x + w, y + h, ghost ? ghostColor(simpleColor()) : simpleColor());
		boolean selected = !ghost && (componentIndex == selectedComponentIndex || selectedComponentIndexes.contains(componentIndex));
		int outline = selected ? 0xffffe08a : 0xffd7e6f8;
		if (ghost) {
			outline = 0x665c6f82;
		}
		if (!ghost && schematic.type() == CircuitComponentType.CUSTOM_MODULE && !selected) {
			outline = 0xff72d4ff;
		}
		graphics.renderOutline(x, y, w, h, outline);
		if (!ghost && !isWirePart(placed)) {
			String label = schematic.displayName() + (placed.rotation() == 0 ? "" : " R" + placed.rotation() * 90);
			graphics.drawCenteredString(font, label, x + w / 2, y + Math.max(5, h / 2 - 4), 0xffffffff);
		}
		if (!ghost && schematic.type() == CircuitComponentType.REPEATER_DELAY) {
			renderDirectionArrow(graphics, x, y, w, h, placed.rotation(), 0xffffffff);
		}
		if (!ghost && schematic.type() == CircuitComponentType.OBSERVER_WIRE) {
			renderDirectionArrow(graphics, x, y, w, h, placed.rotation(), 0xff7ee8ff);
		}

		for (SchematicPort port : schematic.ports()) {
			int color = port.role() == PortRole.INPUT ? 0xff68d96f : 0xffff74d4;
			GridPoint rotated = rotatePoint(port.markerPosition().x(), port.markerPosition().z(), schematic.size().x(), schematic.size().z(), placed.rotation());
			int px = x + rotated.x() * cellSize + cellSize / 2;
			int py = y + rotated.z() * cellSize + cellSize / 2;
			graphics.fill(px - 3, py - 3, px + 4, py + 4, color);
		}
	}

	private void renderWireGroupLabels(GuiGraphics graphics) {
		for (Map.Entry<Long, List<PlacedComponent>> entry : wireGroups().entrySet()) {
			if (entry.getKey() <= 0 || entry.getValue().isEmpty()) {
				continue;
			}
			Bounds bounds = boundsOf(entry.getValue());
			int x = gridOriginX() + bounds.centerX() * cellSize;
			int y = gridOriginY() + bounds.centerZ() * cellSize;
			String label = wireGroupName(entry.getValue().getFirst()) + " (" + entry.getValue().size() + ")";
			graphics.drawCenteredString(font, label, x, y - 5, 0xfff0f3f7);
		}
	}

	private void renderDetailedComponent(GuiGraphics graphics, PlacedComponent placed, boolean ghost) {
		int baseX = gridOriginX() + placed.gridX() * cellSize;
		int baseY = gridOriginY() + placed.gridZ() * cellSize;

		for (SchematicBlock block : placed.schematic().blocks()) {
			GridPoint rotated = rotatePoint(block.position().x(), block.position().z(), placed.schematic().size().x(), placed.schematic().size().z(), placed.rotation());
			int x = baseX + rotated.x() * cellSize;
			int y = baseY + rotated.z() * cellSize;
			int inset = Math.min(cellSize / 3, block.position().y() * 2);
			int color = block.position().y() == layer ? colorFor(block.blockName()) : dimColor(colorFor(block.blockName()));
			if (ghost) {
				color = ghostColor(color);
			}
			graphics.fill(x + 1 + inset, y + 1 + inset, x + cellSize - 1, y + cellSize - 1, color);
			graphics.renderOutline(x + 1 + inset, y + 1 + inset, Math.max(2, cellSize - 2 - inset), Math.max(2, cellSize - 2 - inset), ghost ? 0x55394450 : block.position().y() == layer ? 0xffffffff : 0xff15191f);
			if (!ghost && cellSize >= 12 && block.position().y() > 0) {
				graphics.drawString(font, Integer.toString(block.position().y()), x + 2, y + 2, 0xffffffff, false);
			}
		}
		if (!ghost && placed.schematic().type() == CircuitComponentType.REPEATER_DELAY) {
			int w = Math.max(cellSize, rotatedSizeX(placed) * cellSize);
			int h = Math.max(cellSize, rotatedSizeZ(placed) * cellSize);
			renderDirectionArrow(graphics, baseX, baseY, w, h, placed.rotation(), 0xffffffff);
		}
		if (!ghost && placed.schematic().type() == CircuitComponentType.OBSERVER_WIRE) {
			int w = Math.max(cellSize, rotatedSizeX(placed) * cellSize);
			int h = Math.max(cellSize, rotatedSizeZ(placed) * cellSize);
			renderDirectionArrow(graphics, baseX, baseY, w, h, placed.rotation(), 0xff7ee8ff);
		}
	}

	private void renderDirectionArrow(GuiGraphics graphics, int x, int y, int w, int h, int rotation, int color) {
		int cx = x + w / 2;
		int cy = y + h / 2;
		int length = Math.max(5, Math.min(w, h) / 2 - 2);
		switch (Math.floorMod(rotation, 4)) {
			case 1 -> {
				graphics.vLine(cx, cy - length, cy + length, color);
				graphics.fill(cx - 3, cy + length - 3, cx + 4, cy + length + 1, color);
			}
			case 2 -> {
				graphics.hLine(cx - length, cx + length, cy, color);
				graphics.fill(cx - length - 1, cy - 3, cx - length + 4, cy + 4, color);
			}
			case 3 -> {
				graphics.vLine(cx, cy - length, cy + length, color);
				graphics.fill(cx - 3, cy - length - 1, cx + 4, cy - length + 4, color);
			}
			default -> {
				graphics.hLine(cx - length, cx + length, cy, color);
				graphics.fill(cx + length - 3, cy - 3, cx + length + 1, cy + 4, color);
			}
		}
	}

	private void renderStatus(GuiGraphics graphics) {
		String mode = detailedView ? "Detailed" : "Simple";
		String tool = switch (selectedTool) {
			case POINTER -> "Pointer";
			case SELECT -> "Selection box";
			case ROTATE -> "Rotate";
			case PLACE -> isWireTool(selectedType) ? "Draw/edit wire" : "Place " + selectedType.displayName();
		};
		String text = mode + " | Plane " + activePlane + " | Detail Y=" + layer + " | Pan " + panX + "," + panZ + " | Tool: " + tool + " | Wheel = zoom, middle-drag = pan, right-click = delete";
		int y = height - 13;
		graphics.fill(PANEL_WIDTH, y - 3, width, height, 0xcc101318);
		graphics.drawString(font, text, PANEL_WIDTH + 8, y, 0xffd7dee8, false);
	}

	private void renderInspector(GuiGraphics graphics, int mouseX, int mouseY) {
		int x = width - INSPECTOR_WIDTH;
		graphics.fill(x, TOOLBAR_HEIGHT, width, height, 0xff171d24);
		graphics.drawString(font, "Inspector", x + 10, TOOLBAR_HEIGHT + 10, 0xffffffff, false);

		int hovered = componentAt(mouseX, mouseY);
		int index = selectedComponentIndex >= 0 && selectedComponentIndex < placedComponents.size() ? selectedComponentIndex : hovered;
		int y = TOOLBAR_HEIGHT + 30;
		if (index < 0) {
			graphics.drawWordWrap(font, Component.literal("Select a component to inspect signal, delay, rotation and ports."), x + 10, y, INSPECTOR_WIDTH - 20, 0xffb8c2ce);
			y += 46;
		} else {
			PlacedComponent component = placedComponents.get(index);
			if (isWirePart(component) && component.groupId() > 0L) {
				List<PlacedComponent> group = wireGroup(component.groupId());
				Bounds bounds = boundsOf(group);
				drawInspectorLine(graphics, x, y, wireGroupName(component) + " #" + component.groupId(), 0xffffe08a);
				y += 14;
				drawInspectorLine(graphics, x, y, "Segments: " + group.size(), 0xffb8c2ce);
				y += 12;
				drawInspectorLine(graphics, x, y, "Bounds: " + bounds.width() + " x " + bounds.height(), 0xffb8c2ce);
				y += 12;
				drawInspectorLine(graphics, x, y, "Grid: " + bounds.minX() + "," + bounds.minZ() + " -> " + bounds.maxX() + "," + bounds.maxZ() + " | Plane " + component.plane(), 0xffb8c2ce);
				y += 20;
			} else {
				drawInspectorLine(graphics, x, y, component.schematic().displayName(), 0xffffe08a);
				y += 14;
				drawInspectorLine(graphics, x, y, "Delay: " + delayText(component.schematic().type()), 0xffb8c2ce);
				y += 12;
				drawInspectorLine(graphics, x, y, "Direction: " + directionText(component.rotation()), 0xffb8c2ce);
				y += 12;
				drawInspectorLine(graphics, x, y, "Grid: " + component.gridX() + ", " + component.gridZ() + " | Plane " + component.plane(), 0xffb8c2ce);
				y += 12;
				drawInspectorLine(graphics, x, y, "Ports: " + inputCount(component) + " in / " + outputCount(component) + " out", 0xffb8c2ce);
				y += 20;
			}
		}

		graphics.drawString(font, "Tip", x + 10, y, 0xffff5555, false);
		y += 14;
		graphics.drawWordWrap(font, Component.literal(RedstoneTipProvider.loadingTip()), x + 10, y, INSPECTOR_WIDTH - 20, 0xffcbd4df);
	}

	private void drawInspectorLine(GuiGraphics graphics, int panelX, int y, String text, int color) {
		graphics.drawString(font, text, panelX + 10, y, color, false);
	}

	private void renderWirePreview(GuiGraphics graphics) {
		if (!drawingWire) {
			return;
		}

		for (GridPoint point : wirePathForTool(selectedType, wireStartGridX, wireStartGridZ, wireEndGridX, wireEndGridZ)) {
			int screenX = gridOriginX() + point.x() * cellSize;
			int screenY = gridOriginY() + point.z() * cellSize;
			graphics.fill(screenX + 3, screenY + 3, screenX + cellSize - 3, screenY + cellSize - 3, 0x99d43a32);
		}
	}

	private void renderSelectionBox(GuiGraphics graphics) {
		if (!selectingArea) {
			return;
		}
		int minX = Math.min(selectionStartGridX, selectionEndGridX);
		int maxX = Math.max(selectionStartGridX, selectionEndGridX) + 1;
		int minZ = Math.min(selectionStartGridZ, selectionEndGridZ);
		int maxZ = Math.max(selectionStartGridZ, selectionEndGridZ) + 1;
		int x = gridOriginX() + minX * cellSize;
		int y = gridOriginY() + minZ * cellSize;
		int w = Math.max(cellSize, (maxX - minX) * cellSize);
		int h = Math.max(cellSize, (maxZ - minZ) * cellSize);
		graphics.fill(x, y, x + w, y + h, 0x335aa7ff);
		graphics.renderOutline(x, y, w, h, 0xff8fc7ff);
	}

	private void renderWireSegment(GuiGraphics graphics, int startX, int startZ, int endX, int endZ, int color) {
		int minX = Math.min(startX, endX);
		int maxX = Math.max(startX, endX);
		int minZ = Math.min(startZ, endZ);
		int maxZ = Math.max(startZ, endZ);

		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				int screenX = gridOriginX() + x * cellSize;
				int screenY = gridOriginY() + z * cellSize;
				graphics.fill(screenX + 3, screenY + 3, screenX + cellSize - 3, screenY + cellSize - 3, color);
			}
		}
	}

	private void addWirePath(int startX, int startZ, int endX, int endZ) {
		addWirePath(selectedType, startX, startZ, endX, endZ);
	}

	private static int inputCount(PlacedComponent component) {
		int count = 0;
		for (SchematicPort port : component.schematic().ports()) {
			if (port.role() == PortRole.INPUT) {
				count++;
			}
		}
		return count;
	}

	private static int outputCount(PlacedComponent component) {
		int count = 0;
		for (SchematicPort port : component.schematic().ports()) {
			if (port.role() == PortRole.OUTPUT) {
				count++;
			}
		}
		return count;
	}

	private static String directionText(int rotation) {
		return switch (Math.floorMod(rotation, 4)) {
			case 1 -> "south";
			case 2 -> "west";
			case 3 -> "north";
			default -> "east";
		};
	}

	private static String delayText(CircuitComponentType type) {
		return switch (type) {
			case REPEATER_DELAY -> "1-4 redstone ticks";
			case OBSERVER_WIRE, OBSERVER_CLOCK -> "1 redstone tick pulse";
			case BUTTON -> "10-15 redstone ticks";
			case LAMP -> "0 on / 2 redstone ticks off";
			case NOT -> "1 redstone tick torch delay";
			case CLOCK, ONE_TICK_PULSER -> "timed pulse source";
			default -> "logic-level";
		};
	}

	private void addWirePath(CircuitComponentType wireType, int startX, int startZ, int endX, int endZ) {
		CircuitComponentType effectiveWireType = isWireTool(wireType) ? wireType : CircuitComponentType.WIRE;
		LitematicSchematic wire = schematics.get(effectiveWireType);
		if (wire == null) {
			return;
		}

		List<GridPoint> path = wirePathForTool(effectiveWireType, startX, startZ, endX, endZ);
		List<RoutedPoint> routedPath = routeWithBridges(effectiveWireType, path);
		long groupId = nextGroupId++;
		int wireRotation = wireRotation(effectiveWireType, startX, startZ, endX, endZ);
		for (int index = 0; index < routedPath.size(); index++) {
			RoutedPoint routedPoint = routedPath.get(index);
			GridPoint point = routedPoint.point();
			int plane = routedPoint.plane();
			if (effectiveWireType == CircuitComponentType.WIRE && shouldPlaceRepeater(path, index)) {
				addRepeaterIfMissing(point.x(), point.z(), groupId, repeaterRotation(path, index), plane);
			} else {
				addWireIfMissing(effectiveWireType, wire, point.x(), point.z(), groupId, wireRotation, plane);
			}
		}
	}

	private List<RoutedPoint> routeWithBridges(CircuitComponentType wireType, List<GridPoint> path) {
		Set<Integer> elevatedIndexes = new LinkedHashSet<>();
		if (wireType == CircuitComponentType.WIRE) {
			for (int index = 0; index < path.size(); index++) {
				if (shouldElevateWirePoint(path.get(index))) {
					elevatedIndexes.add(index);
					if (index > 0) {
						elevatedIndexes.add(index - 1);
					}
					if (index + 1 < path.size()) {
						elevatedIndexes.add(index + 1);
					}
				}
			}
		}

		List<RoutedPoint> routed = new ArrayList<>();
		for (int index = 0; index < path.size(); index++) {
			int plane = elevatedIndexes.contains(index) ? activePlane + 1 : activePlane;
			routed.add(new RoutedPoint(path.get(index), plane));
		}
		return routed;
	}

	private boolean shouldElevateWirePoint(GridPoint point) {
		for (PlacedComponent component : placedComponents) {
			if (component.plane() != activePlane || !isWirePart(component)) {
				continue;
			}
			int distance = Math.abs(component.gridX() - point.x()) + Math.abs(component.gridZ() - point.z());
			if (distance <= 1) {
				return true;
			}
		}
		return false;
	}

	private static int repeaterRotation(List<GridPoint> path, int index) {
		GridPoint previous = path.get(index - 1);
		GridPoint next = path.get(index + 1);
		int deltaX = Integer.compare(next.x(), previous.x());
		int deltaZ = Integer.compare(next.z(), previous.z());
		if (Math.abs(deltaX) >= Math.abs(deltaZ)) {
			return deltaX < 0 ? 2 : 0;
		}
		return deltaZ < 0 ? 3 : 1;
	}

	private static int wireRotation(CircuitComponentType wireType, int startX, int startZ, int endX, int endZ) {
		if (wireType != CircuitComponentType.OBSERVER_WIRE) {
			return 0;
		}

		int deltaX = endX - startX;
		int deltaZ = endZ - startZ;
		if (Math.abs(deltaX) >= Math.abs(deltaZ)) {
			return deltaX < 0 ? 2 : 0;
		}

		return deltaZ < 0 ? 3 : 1;
	}

	private List<GridPoint> wirePath(int startX, int startZ, int endX, int endZ) {
		List<GridPoint> routed = routedOrthogonalPath(new GridPoint(startX, startZ), new GridPoint(endX, endZ));
		if (!routed.isEmpty()) {
			return routed;
		}
		return elbowPath(startX, startZ, endX, endZ);
	}

	private List<GridPoint> wirePathForTool(CircuitComponentType wireType, int startX, int startZ, int endX, int endZ) {
		if (wireType != CircuitComponentType.OBSERVER_WIRE) {
			return wirePath(startX, startZ, endX, endZ);
		}

		if (Math.abs(endX - startX) >= Math.abs(endZ - startZ)) {
			return straightWirePath(startX, startZ, endX, startZ);
		}

		return straightWirePath(startX, startZ, startX, endZ);
	}

	private List<GridPoint> routedOrthogonalPath(GridPoint start, GridPoint end) {
		int directDistance = manhattan(start, end);
		int margin = Math.max(8, Math.min(32, directDistance / 2 + 6));
		int minX = Math.min(start.x(), end.x()) - margin;
		int maxX = Math.max(start.x(), end.x()) + margin;
		int minZ = Math.min(start.z(), end.z()) - margin;
		int maxZ = Math.max(start.z(), end.z()) + margin;

		PriorityQueue<PathNode> open = new PriorityQueue<>((left, right) -> Integer.compare(left.priority(), right.priority()));
		Map<GridPoint, Integer> bestCost = new HashMap<>();
		Map<GridPoint, GridPoint> previous = new HashMap<>();
		open.add(new PathNode(start, 0, manhattan(start, end)));
		bestCost.put(start, 0);

		while (!open.isEmpty()) {
			PathNode current = open.poll();
			if (current.cost() != bestCost.getOrDefault(current.point(), Integer.MAX_VALUE)) {
				continue;
			}
			if (current.point().equals(end)) {
				return reconstructPath(previous, end);
			}

			for (GridPoint neighbor : neighbors(current.point())) {
				if (neighbor.x() < minX || neighbor.x() > maxX || neighbor.z() < minZ || neighbor.z() > maxZ) {
					continue;
				}
				int stepCost = routeCost(neighbor, start, end);
				if (stepCost >= 10_000) {
					continue;
				}
				int newCost = current.cost() + stepCost;
				if (newCost < bestCost.getOrDefault(neighbor, Integer.MAX_VALUE)) {
					bestCost.put(neighbor, newCost);
					previous.put(neighbor, current.point());
					open.add(new PathNode(neighbor, newCost, newCost + manhattan(neighbor, end)));
				}
			}
		}
		return List.of();
	}

	private int routeCost(GridPoint point, GridPoint start, GridPoint end) {
		if (point.equals(start) || point.equals(end)) {
			return 1;
		}

		int cost = 10;
		for (PlacedComponent component : placedComponents) {
			if (component.plane() != activePlane) {
				continue;
			}

			boolean overlaps = occupiesGrid(component, point);
			if (overlaps && !isWirePart(component)) {
				return 10_000;
			}
			if (overlaps) {
				cost += 35;
			}
			if (isWirePart(component) && manhattan(point, new GridPoint(component.gridX(), component.gridZ())) == 1) {
				cost += 18;
			}
			if (!isWirePart(component) && nearComponentBounds(component, point)) {
				cost += 12;
			}
		}
		return cost;
	}

	private static List<GridPoint> reconstructPath(Map<GridPoint, GridPoint> previous, GridPoint end) {
		ArrayList<GridPoint> path = new ArrayList<>();
		GridPoint current = end;
		path.add(current);
		while (previous.containsKey(current)) {
			current = previous.get(current);
			path.add(0, current);
		}
		return path;
	}

	private static List<GridPoint> neighbors(GridPoint point) {
		return List.of(
				new GridPoint(point.x() + 1, point.z()),
				new GridPoint(point.x() - 1, point.z()),
				new GridPoint(point.x(), point.z() + 1),
				new GridPoint(point.x(), point.z() - 1)
		);
	}

	private static int manhattan(GridPoint a, GridPoint b) {
		return Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z());
	}

	private static List<GridPoint> elbowPath(int startX, int startZ, int endX, int endZ) {
		List<GridPoint> points = new ArrayList<>();
		int x = startX;
		int z = startZ;
		points.add(new GridPoint(x, z));
		while (x != endX) {
			x += Integer.compare(endX, x);
			points.add(new GridPoint(x, z));
		}
		while (z != endZ) {
			z += Integer.compare(endZ, z);
			points.add(new GridPoint(x, z));
		}
		return points;
	}

	private static List<GridPoint> straightWirePath(int startX, int startZ, int endX, int endZ) {
		List<GridPoint> points = new ArrayList<>();
		int stepX = Integer.compare(endX, startX);
		int stepZ = Integer.compare(endZ, startZ);
		int x = startX;
		int z = startZ;

		while (true) {
			points.add(new GridPoint(x, z));
			if (x == endX && z == endZ) {
				return points;
			}
			x += stepX;
			z += stepZ;
		}
	}

	private void addWireIfMissing(CircuitComponentType wireType, LitematicSchematic wire, int gridX, int gridZ, long groupId, int rotation, int plane) {
		boolean exists = placedComponents.stream()
				.anyMatch(component -> component.schematic().type() == wireType
						&& component.gridX() == gridX
						&& component.gridZ() == gridZ
						&& component.plane() == plane);

		if (!exists) {
			placedComponents.add(new PlacedComponent(wire, gridX, gridZ, rotation, groupId, plane));
		}
	}

	private void addRepeaterIfMissing(int gridX, int gridZ, long groupId, int rotation, int plane) {
		LitematicSchematic repeater = schematics.get(CircuitComponentType.REPEATER_DELAY);
		if (repeater == null) {
			return;
		}

		boolean exists = placedComponents.stream()
				.anyMatch(component -> component.gridX() == gridX && component.gridZ() == gridZ && component.plane() == plane);

		if (!exists) {
			placedComponents.add(new PlacedComponent(repeater, gridX, gridZ, rotation, groupId, plane));
		}
	}

	private void selectComponentsInBox() {
		selectedComponentIndexes.clear();
		int minX = Math.min(selectionStartGridX, selectionEndGridX);
		int maxX = Math.max(selectionStartGridX, selectionEndGridX);
		int minZ = Math.min(selectionStartGridZ, selectionEndGridZ);
		int maxZ = Math.max(selectionStartGridZ, selectionEndGridZ);

		for (int index = 0; index < placedComponents.size(); index++) {
			PlacedComponent component = placedComponents.get(index);
			if (component.plane() != activePlane) {
				continue;
			}
			int componentMaxX = component.gridX() + rotatedSizeX(component) - 1;
			int componentMaxZ = component.gridZ() + rotatedSizeZ(component) - 1;
			if (component.gridX() <= maxX && componentMaxX >= minX && component.gridZ() <= maxZ && componentMaxZ >= minZ) {
				selectedComponentIndexes.add(index);
			}
		}

		expandWireGroupsInSelection();
		selectedComponentIndex = selectedComponentIndexes.size() == 1 ? selectedComponentIndexes.iterator().next() : -1;
		if (!selectedComponentIndexes.isEmpty()) {
		}
	}

	private void createModuleFromSelection() {
		if (selectedComponentIndexes.size() < 2) {
			saveModule();
			return;
		}

		List<Integer> indexes = selectedComponentIndexes.stream().sorted().toList();
		List<PlacedComponent> selected = indexes.stream().map(placedComponents::get).toList();
		int minX = selected.stream().mapToInt(PlacedComponent::gridX).min().orElse(0);
		int minZ = selected.stream().mapToInt(PlacedComponent::gridZ).min().orElse(0);
		List<PlacedComponent> localChildren = selected.stream()
				.map(component -> component.moveBy(-minX, -minZ))
				.toList();
		String name = "module_" + MODULE_NAME_FORMAT.format(LocalDateTime.now());
		LitematicSchematic module = SchaltplanPlanStorage.createModuleSchematic(name, localChildren);

		pushUndo();
		for (int index = indexes.size() - 1; index >= 0; index--) {
			placedComponents.remove((int) indexes.get(index));
		}
		placedComponents.add(new PlacedComponent(module, minX, minZ).onPlane(activePlane));
		moduleChildren.put(module.regionName(), List.copyOf(localChildren));
		selectedComponentIndexes.clear();
		selectedComponentIndex = placedComponents.size() - 1;
		selectedComponentIndexes.add(selectedComponentIndex);
	}

	private boolean handleModuleDoubleRightClick(int index) {
		long now = net.minecraft.Util.getMillis();
		boolean doubleClick = index == lastRightClickedIndex && now - lastRightClickMillis < 450L;
		lastRightClickedIndex = index;
		lastRightClickMillis = now;
		if (!doubleClick) {
			return false;
		}

		if (editingModuleName != null) {
			finishModuleEditing();
			return true;
		}

		return enterModuleEditing(index);
	}

	private boolean handlePointerDoubleClick(int index, int mouseX, int mouseY) {
		long now = net.minecraft.Util.getMillis();
		boolean doubleClick = index == lastLeftClickedIndex && now - lastLeftClickMillis < 450L;
		lastLeftClickedIndex = index;
		lastLeftClickMillis = now;
		if (!doubleClick) {
			return false;
		}

		if (editingModuleName != null && !isInsideEditingModule(mouseX, mouseY)) {
			finishModuleEditing();
			return true;
		}

		return enterModuleEditing(index);
	}

	private boolean enterModuleEditing(int index) {
		if (index < 0 || index >= placedComponents.size()) {
			return false;
		}

		PlacedComponent module = placedComponents.get(index);
		if (module.schematic().type() != CircuitComponentType.CUSTOM_MODULE) {
			return false;
		}
		List<PlacedComponent> children = moduleChildren.get(module.schematic().regionName());
		if (children == null || children.isEmpty()) {
			if (minecraft != null && minecraft.player != null) {
				minecraft.player.displayClientMessage(Component.literal("This module can only be edited after it was created in this editor session."), false);
			}
			return true;
		}

		pushUndo();
		editingModuleName = module.schematic().regionName();
		editingModuleMinX = module.gridX();
		editingModuleMinZ = module.gridZ();
		editingModuleMaxX = module.gridX() + rotatedSizeX(module) - 1;
		editingModuleMaxZ = module.gridZ() + rotatedSizeZ(module) - 1;
		placedComponents.remove(index);
		for (PlacedComponent child : children) {
			placedComponents.add(child.moveBy(module.gridX(), module.gridZ()));
		}
		selectedComponentIndexes.clear();
		selectedComponentIndex = -1;
		return true;
	}

	private boolean isInsideEditingModule(int mouseX, int mouseY) {
		int gridX = screenToGridX(mouseX);
		int gridZ = screenToGridZ(mouseY);
		return gridX >= editingModuleMinX && gridX <= editingModuleMaxX
				&& gridZ >= editingModuleMinZ && gridZ <= editingModuleMaxZ;
	}

	private void finishModuleEditing() {
		List<Integer> inside = new ArrayList<>();
		for (int index = 0; index < placedComponents.size(); index++) {
			PlacedComponent component = placedComponents.get(index);
			int maxX = component.gridX() + rotatedSizeX(component) - 1;
			int maxZ = component.gridZ() + rotatedSizeZ(component) - 1;
			if (component.gridX() <= editingModuleMaxX && maxX >= editingModuleMinX
					&& component.gridZ() <= editingModuleMaxZ && maxZ >= editingModuleMinZ) {
				inside.add(index);
			}
		}
		if (inside.isEmpty()) {
			editingModuleName = null;
			return;
		}

		List<PlacedComponent> selected = inside.stream().map(placedComponents::get).toList();
		int minX = selected.stream().mapToInt(PlacedComponent::gridX).min().orElse(editingModuleMinX);
		int minZ = selected.stream().mapToInt(PlacedComponent::gridZ).min().orElse(editingModuleMinZ);
		List<PlacedComponent> localChildren = selected.stream()
				.map(component -> component.moveBy(-minX, -minZ))
				.toList();
		LitematicSchematic module = SchaltplanPlanStorage.createModuleSchematic(editingModuleName, localChildren);

		pushUndo();
		for (int remove = inside.size() - 1; remove >= 0; remove--) {
			placedComponents.remove((int) inside.get(remove));
		}
		placedComponents.add(new PlacedComponent(module, minX, minZ).onPlane(activePlane));
		moduleChildren.put(module.regionName(), List.copyOf(localChildren));
		selectedComponentIndexes.clear();
		selectedComponentIndex = placedComponents.size() - 1;
		selectedComponentIndexes.add(selectedComponentIndex);
		editingModuleName = null;
	}

	private void pushUndo() {
		undoStack.addLast(List.copyOf(placedComponents));
		while (undoStack.size() > HISTORY_LIMIT) {
			undoStack.removeFirst();
		}
		redoStack.clear();
	}

	private void undo() {
		if (undoStack.isEmpty()) {
			return;
		}
		redoStack.addLast(List.copyOf(placedComponents));
		restoreHistory(undoStack.removeLast());
	}

	private void redo() {
		if (redoStack.isEmpty()) {
			return;
		}
		undoStack.addLast(List.copyOf(placedComponents));
		restoreHistory(redoStack.removeLast());
	}

	private static boolean isUndoShortcut(int keyCode, int modifiers) {
		return hasControlModifier(modifiers)
				&& !hasShiftModifier(modifiers)
				&& keyCode == GLFW.GLFW_KEY_Z;
	}

	private static boolean isRedoShortcut(int keyCode, int modifiers) {
		return hasControlModifier(modifiers)
				&& (keyCode == GLFW.GLFW_KEY_Y || (hasShiftModifier(modifiers) && keyCode == GLFW.GLFW_KEY_Z));
	}

	private static boolean hasControlModifier(int modifiers) {
		return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 || Screen.hasControlDown();
	}

	private static boolean hasShiftModifier(int modifiers) {
		return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 || Screen.hasShiftDown();
	}

	private void restoreHistory(List<PlacedComponent> snapshot) {
		placedComponents.clear();
		placedComponents.addAll(snapshot);
		selectedComponentIndexes.clear();
		selectedComponentIndex = -1;
		refreshNextGroupId();
	}

	private void clearSelection() {
		selectedComponentIndexes.clear();
		selectedComponentIndex = -1;
		draggedComponentIndex = -1;
		drawingWire = false;
		selectingArea = false;
	}

	private void clearEditorAndWorld() {
		Map<BlockPos, String> previousBlocks = new LinkedHashMap<>(WorldPlacementState.lastPlacedBlocks());
		placedComponents.clear();
		selectedComponentIndexes.clear();
		selectedComponentIndex = -1;
		draggedComponentIndex = -1;
		drawingWire = false;
		selectingArea = false;
		panning = false;
		editingModuleName = null;
		refreshNextGroupId();

		int clearedBlocks = clearPreviouslySyncedWorldBlocks(previousBlocks);
		WorldPlacementState.clearPlacedBlocks();
		boolean savedEmptyPlan = SchaltplanPlanStorage.saveCurrent(placedComponents);
		if (minecraft != null && minecraft.player != null) {
			minecraft.player.displayClientMessage(Component.literal("Circuit cleared"
					+ (clearedBlocks > 0 ? ": " + clearedBlocks + " world blocks removed." : ".")
					+ (savedEmptyPlan ? "" : " Could not update saved plan.")), false);
		}
	}

	private int clearPreviouslySyncedWorldBlocks(Map<BlockPos, String> previousBlocks) {
		if (previousBlocks.isEmpty() || minecraft == null || minecraft.getConnection() == null) {
			return 0;
		}

		minecraft.getConnection().sendCommand("gamerule sendCommandFeedback false");
		minecraft.getConnection().sendCommand("gamerule commandBlockOutput false");
		List<Map.Entry<BlockPos, String>> blocksToClear = previousBlocks.entrySet().stream()
				.sorted(clearOrder())
				.toList();
		for (Map.Entry<BlockPos, String> entry : blocksToClear) {
			sendSetBlock(entry.getKey(), "minecraft:air");
		}
		return blocksToClear.size();
	}

	private static boolean shouldPlaceRepeater(List<GridPoint> path, int index) {
		if (index == 0 || index == path.size() - 1 || isCorner(path, index)) {
			return false;
		}

		int runLength = 0;
		for (int currentIndex = index; currentIndex > 0; currentIndex--) {
			if (isCorner(path, currentIndex)) {
				break;
			}
			runLength++;
		}

		return runLength % REPEATER_SPACING == 0;
	}

	private static boolean isCorner(List<GridPoint> path, int index) {
		GridPoint previous = path.get(index - 1);
		GridPoint current = path.get(index);
		GridPoint next = path.get(index + 1);
		int previousX = Integer.compare(current.x(), previous.x());
		int previousZ = Integer.compare(current.z(), previous.z());
		int nextX = Integer.compare(next.x(), current.x());
		int nextZ = Integer.compare(next.z(), current.z());

		return previousX != nextX || previousZ != nextZ;
	}

	private void savePlan() {
		boolean saved = SchaltplanPlanStorage.save(placedComponents);
		if (minecraft != null && minecraft.player != null) {
			minecraft.player.displayClientMessage(Component.literal(saved
					? "Circuit saved: " + SchaltplanPlanStorage.planFile()
					: "Could not save circuit."), false);
		}
	}

	private void saveModule() {
		if (minecraft == null || minecraft.player == null) {
			return;
		}

		if (placedComponents.isEmpty()) {
			minecraft.player.displayClientMessage(Component.literal("Place components before saving a module."), false);
			return;
		}

		try {
			java.nio.file.Path module = SchaltplanPlanStorage.saveModule(placedComponents);
			minecraft.player.displayClientMessage(Component.literal("Module saved: " + module), false);
		} catch (IOException exception) {
			SchaltplanMod.LOGGER.warn("Could not save module.", exception);
			minecraft.player.displayClientMessage(Component.literal("Could not save module."), false);
		}
	}

	@Override
	public void addGeneratedComponent(CircuitComponentType type, int gridX, int gridZ) {
		addGeneratedComponent(type, gridX, gridZ, 0);
	}

	@Override
	public void addGeneratedComponent(CircuitComponentType type, int gridX, int gridZ, int rotation) {
		LitematicSchematic schematic = schematics.get(type);
		if (schematic != null) {
			placedComponents.add(new PlacedComponent(schematic, gridX + panX, gridZ + panZ, Math.floorMod(rotation, 4), 0L, activePlane));
		}
	}

	public void savePlanNamed(String name) {
		boolean saved = SchaltplanPlanStorage.saveNamed(name, placedComponents);
		if (minecraft != null && minecraft.player != null) {
			minecraft.player.displayClientMessage(Component.literal(saved
					? "Circuit saved as: " + name
					: "Could not save circuit."), false);
		}
	}

	private void loadDefaultPlanIfEditorIsEmpty() {
		if (!placedComponents.isEmpty()) {
			return;
		}
		List<PlacedComponent> loaded = SchaltplanPlanStorage.load(schematics);
		if (!loaded.isEmpty()) {
			placedComponents.addAll(loaded);
		}
	}

	@Override
	public void addGeneratedWire(int startX, int startZ, int endX, int endZ) {
		addWirePath(CircuitComponentType.WIRE, startX + panX, startZ + panZ, endX + panX, endZ + panZ);
	}

	@Override
	public void addGeneratedSchematic(LitematicSchematic schematic, int gridX, int gridZ) {
		placedComponents.add(new PlacedComponent(schematic, gridX + panX, gridZ + panZ).onPlane(activePlane));
	}

	public void loadPlanFromFile(java.nio.file.Path path, boolean append) {
		List<PlacedComponent> loaded = SchaltplanPlanStorage.loadFile(path, schematics);
		if (append) {
			loaded = remapImportedPlanesToActivePlane(loaded);
		}

		if (!append) {
			placedComponents.clear();
			SchaltplanPlanStorage.rememberOpenedPlan(path);
		}

		placedComponents.addAll(loaded);
		refreshNextGroupId();
		if (minecraft != null && minecraft.player != null) {
			minecraft.player.displayClientMessage(Component.literal((append ? "Imported: " : "Loaded: ") + path.getFileName() + " (" + loaded.size() + " components)"), false);
		}
	}

	private List<PlacedComponent> remapImportedPlanesToActivePlane(List<PlacedComponent> components) {
		int lowestPlane = components.stream().mapToInt(PlacedComponent::plane).min().orElse(0);
		return components.stream()
				.map(component -> component.onPlane(activePlane + Math.max(0, component.plane() - lowestPlane)))
				.toList();
	}

	private void removeComponentOrGroup(int index) {
		long groupId = placedComponents.get(index).groupId();
		if (groupId == 0L) {
			placedComponents.remove(index);
			return;
		}

		placedComponents.removeIf(component -> component.groupId() == groupId);
	}

	private void removeSelectedComponents() {
		List<Integer> indexes = selectedComponentIndexes.stream()
				.filter(index -> index >= 0 && index < placedComponents.size())
				.sorted(Comparator.reverseOrder())
				.toList();
		for (int index : indexes) {
			placedComponents.remove(index);
		}
		selectedComponentIndex = -1;
		selectedComponentIndexes.clear();
	}

	private void moveComponentOrGroup(int index, int deltaX, int deltaZ) {
		if (deltaX == 0 && deltaZ == 0) {
			return;
		}

		long groupId = placedComponents.get(index).groupId();
		if (groupId == 0L) {
			placedComponents.set(index, placedComponents.get(index).moveBy(deltaX, deltaZ));
			return;
		}

		for (int componentIndex = 0; componentIndex < placedComponents.size(); componentIndex++) {
			PlacedComponent component = placedComponents.get(componentIndex);
			if (component.groupId() == groupId) {
				placedComponents.set(componentIndex, component.moveBy(deltaX, deltaZ));
			}
		}
	}

	private void rotateComponentOrGroup(int index) {
		if (index < 0 || index >= placedComponents.size()) {
			return;
		}

		long groupId = placedComponents.get(index).groupId();
		if (groupId == 0L) {
			placedComponents.set(index, placedComponents.get(index).rotateClockwise());
			return;
		}

		for (int componentIndex = 0; componentIndex < placedComponents.size(); componentIndex++) {
			PlacedComponent component = placedComponents.get(componentIndex);
			if (component.groupId() == groupId) {
				placedComponents.set(componentIndex, component.rotateClockwise());
			}
		}
	}

	private void moveSelectedComponents(int deltaX, int deltaZ) {
		if (deltaX == 0 && deltaZ == 0) {
			return;
		}

		Set<Long> movedGroups = new LinkedHashSet<>();
		for (int componentIndex : new ArrayList<>(selectedComponentIndexes)) {
			if (componentIndex < 0 || componentIndex >= placedComponents.size()) {
				continue;
			}

			long groupId = placedComponents.get(componentIndex).groupId();
			if (groupId > 0L) {
				if (movedGroups.add(groupId)) {
					moveComponentOrGroup(componentIndex, deltaX, deltaZ);
				}
			} else {
				placedComponents.set(componentIndex, placedComponents.get(componentIndex).moveBy(deltaX, deltaZ));
			}
		}
	}

	private void selectComponentOrGroup(int index) {
		selectedComponentIndexes.clear();
		if (index < 0 || index >= placedComponents.size()) {
			selectedComponentIndex = -1;
			return;
		}

		PlacedComponent selected = placedComponents.get(index);
		if (isWirePart(selected) && selected.groupId() > 0L) {
			for (int componentIndex = 0; componentIndex < placedComponents.size(); componentIndex++) {
				PlacedComponent component = placedComponents.get(componentIndex);
				if (component.groupId() == selected.groupId()) {
					selectedComponentIndexes.add(componentIndex);
				}
			}
		} else {
			selectedComponentIndexes.add(index);
		}
		selectedComponentIndex = index;
	}

	private void expandWireGroupsInSelection() {
		Set<Long> selectedWireGroups = new LinkedHashSet<>();
		for (int componentIndex : selectedComponentIndexes) {
			if (componentIndex < 0 || componentIndex >= placedComponents.size()) {
				continue;
			}
			PlacedComponent component = placedComponents.get(componentIndex);
			if (isWirePart(component) && component.groupId() > 0L) {
				selectedWireGroups.add(component.groupId());
			}
		}

		if (selectedWireGroups.isEmpty()) {
			return;
		}

		for (int componentIndex = 0; componentIndex < placedComponents.size(); componentIndex++) {
			if (selectedWireGroups.contains(placedComponents.get(componentIndex).groupId())) {
				selectedComponentIndexes.add(componentIndex);
			}
		}
	}

	private void refreshNextGroupId() {
		nextGroupId = placedComponents.stream()
				.mapToLong(PlacedComponent::groupId)
				.max()
				.orElse(0L) + 1L;
	}

	private Map<Long, List<PlacedComponent>> wireGroups() {
		Map<Long, List<PlacedComponent>> groups = new LinkedHashMap<>();
		for (PlacedComponent component : placedComponents) {
			if (component.plane() == activePlane && isWirePart(component) && component.groupId() > 0L) {
				groups.computeIfAbsent(component.groupId(), ignored -> new ArrayList<>()).add(component);
			}
		}
		return groups;
	}

	private List<PlacedComponent> wireGroup(long groupId) {
		if (groupId <= 0L) {
			return List.of();
		}

		List<PlacedComponent> group = new ArrayList<>();
		for (PlacedComponent component : placedComponents) {
			if (isWirePart(component) && component.groupId() == groupId) {
				group.add(component);
			}
		}
		return group;
	}

	private static Bounds boundsOf(List<PlacedComponent> components) {
		if (components.isEmpty()) {
			return new Bounds(0, 0, 0, 0);
		}

		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (PlacedComponent component : components) {
			minX = Math.min(minX, component.gridX());
			minZ = Math.min(minZ, component.gridZ());
			maxX = Math.max(maxX, component.gridX() + rotatedSizeX(component) - 1);
			maxZ = Math.max(maxZ, component.gridZ() + rotatedSizeZ(component) - 1);
		}
		return new Bounds(minX, minZ, maxX, maxZ);
	}

	private static String wireGroupName(PlacedComponent component) {
		if (component.schematic().type() == CircuitComponentType.OBSERVER_WIRE) {
			return "Observer line";
		}
		return "Redstone line";
	}

	private GridPoint farthestWireEndpoint(long groupId, int clickedX, int clickedZ) {
		List<GridPoint> endpoints = wireEndpoints(groupId);
		if (endpoints.isEmpty()) {
			return new GridPoint(clickedX, clickedZ);
		}

		return endpoints.stream()
				.max((a, b) -> Integer.compare(distanceSquared(a, clickedX, clickedZ), distanceSquared(b, clickedX, clickedZ)))
				.orElse(endpoints.getFirst());
	}

	private List<GridPoint> wireEndpoints(long groupId) {
		Set<GridPoint> points = placedComponents.stream()
				.filter(component -> component.groupId() == groupId)
				.map(component -> new GridPoint(component.gridX(), component.gridZ()))
				.collect(Collectors.toSet());

		return points.stream()
				.filter(point -> neighborCount(point, points) <= 1)
				.toList();
	}

	private static int neighborCount(GridPoint point, Set<GridPoint> points) {
		int count = 0;
		if (points.contains(new GridPoint(point.x() + 1, point.z()))) count++;
		if (points.contains(new GridPoint(point.x() - 1, point.z()))) count++;
		if (points.contains(new GridPoint(point.x(), point.z() + 1))) count++;
		if (points.contains(new GridPoint(point.x(), point.z() - 1))) count++;
		return count;
	}

	private static int distanceSquared(GridPoint point, int x, int z) {
		int deltaX = point.x() - x;
		int deltaZ = point.z() - z;
		return deltaX * deltaX + deltaZ * deltaZ;
	}

	private static boolean isWireTool(CircuitComponentType type) {
		return type == CircuitComponentType.WIRE || type == CircuitComponentType.OBSERVER_WIRE;
	}

	private static boolean isWirePart(PlacedComponent component) {
		CircuitComponentType type = component.schematic().type();
		return isWireTool(type) || type == CircuitComponentType.REPEATER_DELAY;
	}

	private int componentAt(int mouseX, int mouseY) {
		for (int index = placedComponents.size() - 1; index >= 0; index--) {
			PlacedComponent placed = placedComponents.get(index);
			if (placed.plane() != activePlane) {
				continue;
			}
			int x = gridOriginX() + placed.gridX() * cellSize;
			int y = gridOriginY() + placed.gridZ() * cellSize;
			int w = Math.max(cellSize, rotatedSizeX(placed) * cellSize);
			int h = Math.max(cellSize, rotatedSizeZ(placed) * cellSize);

			if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
				return index;
			}
		}

		return -1;
	}

	private boolean isEditorArea(double mouseX, double mouseY) {
		return mouseY > TOOLBAR_HEIGHT && mouseX > PANEL_WIDTH && mouseX < width - INSPECTOR_WIDTH;
	}

	private static boolean occupiesGrid(PlacedComponent component, GridPoint point) {
		return point.x() >= component.gridX()
				&& point.x() < component.gridX() + rotatedSizeX(component)
				&& point.z() >= component.gridZ()
				&& point.z() < component.gridZ() + rotatedSizeZ(component);
	}

	private static boolean nearComponentBounds(PlacedComponent component, GridPoint point) {
		return point.x() >= component.gridX() - 1
				&& point.x() <= component.gridX() + rotatedSizeX(component)
				&& point.z() >= component.gridZ() - 1
				&& point.z() <= component.gridZ() + rotatedSizeZ(component);
	}

	private int screenToGridX(int mouseX) {
		return Math.floorDiv(mouseX - gridOriginX(), cellSize);
	}

	private int screenToGridZ(int mouseY) {
		return Math.floorDiv(mouseY - gridOriginY(), cellSize);
	}

	private void zoomAt(int mouseX, int mouseY, double amount) {
		if (amount == 0.0D) {
			return;
		}

		int beforeX = screenToGridX(mouseX);
		int beforeZ = screenToGridZ(mouseY);
		int oldCellSize = cellSize;
		cellSize = Math.max(6, Math.min(30, cellSize + (amount > 0.0D ? 2 : -2)));
		if (cellSize == oldCellSize) {
			return;
		}

		int afterX = screenToGridX(mouseX);
		int afterZ = screenToGridZ(mouseY);
		panX += beforeX - afterX;
		panZ += beforeZ - afterZ;
	}

	private void syncToWorld() {
		if (minecraft == null || minecraft.player == null || minecraft.getConnection() == null) {
			return;
		}

		BlockPos origin = WorldPlacementState.originOrSet(minecraft.player.blockPosition().offset(2, 0, 2));
		Map<BlockPos, String> desiredBlocks = buildDesiredWorldBlocks(origin);
		Map<BlockPos, String> previousBlocks = WorldPlacementState.lastPlacedBlocks();
		List<Map.Entry<BlockPos, String>> blocksToClear = previousBlocks.entrySet().stream()
				.filter(entry -> !desiredBlocks.containsKey(entry.getKey()))
				.sorted(clearOrder())
				.toList();
		List<Map.Entry<BlockPos, String>> blocksToPlace = desiredBlocks.entrySet().stream()
				.filter(entry -> !entry.getValue().equals(previousBlocks.get(entry.getKey())))
				.sorted(placeOrder())
				.toList();

		if (!blocksToClear.isEmpty() || !blocksToPlace.isEmpty()) {
			minecraft.getConnection().sendCommand("gamerule sendCommandFeedback false");
			minecraft.getConnection().sendCommand("gamerule commandBlockOutput false");
		}

		for (Map.Entry<BlockPos, String> entry : blocksToClear) {
			sendSetBlock(entry.getKey(), "minecraft:air");
		}

		for (Map.Entry<BlockPos, String> entry : blocksToPlace) {
			sendSetBlock(entry.getKey(), entry.getValue());
		}

		WorldPlacementState.replace(desiredBlocks);
		int commandCount = blocksToClear.size() + blocksToPlace.size();
		minecraft.player.displayClientMessage(Component.literal("Circuit synced: " + commandCount + " changed blocks (" + blocksToClear.size() + " cleared, " + blocksToPlace.size() + " placed)."), false);
	}

	private void scanWorldRedstoneChanges() {
		if (minecraft == null || minecraft.player == null || minecraft.level == null) {
			return;
		}

		BlockPos origin = WorldPlacementState.origin();
		if (origin == null) {
			origin = inferWorldOriginFromPlan();
			if (origin == null) {
				origin = minecraft.player.blockPosition().offset(2, 0, 2);
			}
			origin = WorldPlacementState.originOrSet(origin);
		}
		BlockPos scanOrigin = origin;

		ScanBounds bounds = scanBounds(scanOrigin);
		Map<Integer, Integer> planeOffsets = planeYOffsetByPlane();
		List<ScannedRedstone> scanned = new ArrayList<>();
		List<ScannedRedstone> recognizedComponents = scanKnownCircuitComponents(bounds, scanOrigin, planeOffsets);
		Set<BlockPos> recognizedComponentBlocks = recognizedComponentBlocks(recognizedComponents, scanOrigin, planeOffsets);
		scanned.addAll(recognizedComponents);
		int dustCount = 0;
		int repeaterCount = 0;
		int observerCount = 0;
		int sourceCount = 0;
		int rawDustCount = 0;
		int skippedComponentOverlap = 0;
		int componentCount = recognizedComponents.size();
		for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
			for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
				for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (recognizedComponentBlocks.contains(pos)) {
						continue;
					}
					BlockState state = minecraft.level.getBlockState(pos);
					if (state.is(Blocks.REDSTONE_WIRE) || state.getBlock() == Blocks.REDSTONE_WIRE) {
						rawDustCount++;
					}
					if (!isWorldRedstoneComponent(state)) {
						continue;
					}
					CircuitComponentType type = redstoneTypeFromWorldState(state);
					LitematicSchematic schematic;
					int rotation = rotationFromWorldState(state);
					int plane;
					int gridX;
					int gridZ;
					if (type == CircuitComponentType.CUSTOM_MODULE) {
						schematic = scannedWorldComponentSchematic(state);
						plane = planeAtOrBelowWorldY(pos.getY() - scanOrigin.getY(), planeOffsets);
						gridX = pos.getX() - scanOrigin.getX();
						gridZ = pos.getZ() - scanOrigin.getZ();
					} else {
						schematic = schematics.get(type);
						if (schematic == null) {
							continue;
						}
						GridPoint gridPoint = gridPointForScannedBlock(pos, scanOrigin, schematic, type, rotation);
						plane = planeForScannedBlock(pos, scanOrigin, schematic, type, planeOffsets);
						gridX = gridPoint.x();
						gridZ = gridPoint.z();
					}
					if (type != CircuitComponentType.WIRE && nonWireComponentAt(gridX, gridZ, plane)) {
						skippedComponentOverlap++;
						continue;
					}
					scanned.add(new ScannedRedstone(type, gridX, gridZ, rotation, plane, schematic));
					if (type == CircuitComponentType.WIRE) {
						dustCount++;
					} else if (type == CircuitComponentType.REPEATER_DELAY) {
						repeaterCount++;
					} else if (type == CircuitComponentType.OBSERVER_WIRE) {
						observerCount++;
					} else if (type == CircuitComponentType.VCC) {
						sourceCount++;
					}
				}
			}
		}

		if (scanned.isEmpty()) {
			minecraft.player.displayClientMessage(Component.literal("World scan found no imported redstone in the Circuit area. Raw dust blocks seen: " + rawDustCount + ". Existing editor wires were kept."), false);
			return;
		}

		pushUndo();
		placedComponents.removeIf(component -> bounds.containsGrid(component.gridX(), component.gridZ(), scanOrigin)
				&& (isWorldScannablePart(component) || isKnownCircuitComponent(component.schematic().type())));
		addScannedRedstone(scanned);
		refreshNextGroupId();
		SchaltplanPlanStorage.saveCurrent(placedComponents);
		minecraft.player.displayClientMessage(Component.literal("World scan imported " + scanned.size()
				+ " parts (" + componentCount + " components, " + dustCount + " dust, " + repeaterCount + " repeaters, " + observerCount + " observers, " + sourceCount + " sources, " + skippedComponentOverlap + " overlaps skipped)."), false);
	}

	private BlockPos inferWorldOriginFromPlan() {
		if (minecraft == null || minecraft.level == null || minecraft.player == null || placedComponents.isEmpty()) {
			return null;
		}

		Map<Integer, Integer> planeOffsets = planeYOffsetByPlane();
		Map<BlockPos, Integer> candidateScores = new LinkedHashMap<>();
		BlockPos playerPos = minecraft.player.blockPosition();
		Map<String, List<BlockPos>> worldAnchors = nearbyUsefulWorldAnchors(playerPos, 96);

		for (PlacedComponent component : placedComponents) {
			List<SchematicBlock> anchors = component.schematic().blocks().stream()
					.filter(block -> isUsefulOriginAnchor(block.blockName()))
					.limit(4)
					.toList();
			if (anchors.isEmpty()) {
				continue;
			}

			for (SchematicBlock anchor : anchors) {
				for (BlockPos worldPos : worldAnchors.getOrDefault(anchor.blockName(), List.of())) {
					GridPoint rotated = rotatePoint(anchor.position().x(), anchor.position().z(), component.schematic().size().x(), component.schematic().size().z(), component.rotation());
					int planeOffset = planeOffsets.getOrDefault(component.plane(), 0);
					BlockPos candidateOrigin = worldPos.offset(
							-(component.gridX() + rotated.x()),
							-(planeOffset + anchor.position().y()),
							-(component.gridZ() + rotated.z())
					);
					int score = scoreWorldOrigin(candidateOrigin, planeOffsets);
					if (score > 0) {
						candidateScores.merge(candidateOrigin, score, Math::max);
					}
				}
			}
		}

		return candidateScores.entrySet().stream()
				.filter(entry -> entry.getValue() >= 4)
				.max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.orElse(null);
	}

	private Map<String, List<BlockPos>> nearbyUsefulWorldAnchors(BlockPos center, int radius) {
		Map<String, List<BlockPos>> anchors = new LinkedHashMap<>();
		for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
			for (int y = center.getY() - 16; y <= center.getY() + 32; y++) {
				for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
					BlockPos pos = new BlockPos(x, y, z);
					BlockState state = minecraft.level.getBlockState(pos);
					String blockName = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
					if (isUsefulOriginAnchor(blockName)) {
						anchors.computeIfAbsent(blockName, ignored -> new ArrayList<>()).add(pos);
					}
				}
			}
		}
		return anchors;
	}

	private int scoreWorldOrigin(BlockPos origin, Map<Integer, Integer> planeOffsets) {
		int score = 0;
		int checked = 0;
		for (PlacedComponent component : placedComponents) {
			for (SchematicBlock block : component.schematic().blocks()) {
				if (!isFunctionalCircuitBlock(block.blockName())) {
					continue;
				}
				GridPoint rotated = rotatePoint(block.position().x(), block.position().z(), component.schematic().size().x(), component.schematic().size().z(), component.rotation());
				BlockPos worldPos = origin.offset(
						component.gridX() + rotated.x(),
						planeOffsets.getOrDefault(component.plane(), 0) + block.position().y(),
						component.gridZ() + rotated.z()
				);
				if (worldBlockMatches(block, component.rotation(), minecraft.level.getBlockState(worldPos))) {
					score++;
				}
				checked++;
				if (checked >= 48) {
					return score;
				}
			}
		}
		return score;
	}

	private static boolean isUsefulOriginAnchor(String blockName) {
		return isWorldRedstoneComponentName(blockName)
				|| blockName.equals("minecraft:yellow_concrete")
				|| blockName.equals("minecraft:lime_concrete")
				|| blockName.equals("minecraft:pink_concrete")
				|| blockName.equals("minecraft:iron_block");
	}

	private List<ScannedRedstone> scanKnownCircuitComponents(ScanBounds bounds, BlockPos origin, Map<Integer, Integer> planeOffsets) {
		List<ScannedRedstone> found = new ArrayList<>();
		Set<BlockPos> occupied = new LinkedHashSet<>();
		int minGridX = bounds.minX() - origin.getX();
		int maxGridX = bounds.maxX() - origin.getX();
		int minGridZ = bounds.minZ() - origin.getZ();
		int maxGridZ = bounds.maxZ() - origin.getZ();
		List<CircuitComponentType> componentTypes = CircuitComponentType.MENU_ORDER.stream()
				.filter(SchaltplanEditorScreen::isKnownCircuitComponent)
				.sorted((left, right) -> Integer.compare(
						schematics.get(right) == null ? 0 : schematics.get(right).blocks().size(),
						schematics.get(left) == null ? 0 : schematics.get(left).blocks().size()
				))
				.toList();

		for (Map.Entry<Integer, Integer> planeEntry : planeOffsets.entrySet()) {
			int plane = planeEntry.getKey();
			for (CircuitComponentType type : componentTypes) {
				LitematicSchematic schematic = schematics.get(type);
				if (schematic == null) {
					continue;
				}
				for (int rotation = 0; rotation < 4; rotation++) {
					int sizeX = rotation % 2 == 0 ? schematic.size().x() : schematic.size().z();
					int sizeZ = rotation % 2 == 0 ? schematic.size().z() : schematic.size().x();
					for (int gridX = minGridX - sizeX; gridX <= maxGridX; gridX++) {
						for (int gridZ = minGridZ - sizeZ; gridZ <= maxGridZ; gridZ++) {
							if (matchesKnownCircuitComponent(schematic, gridX, gridZ, rotation, planeEntry.getValue(), origin, occupied)) {
								ScannedRedstone component = new ScannedRedstone(type, gridX, gridZ, rotation, plane, schematic);
								found.add(component);
								occupied.addAll(worldBlocksFor(component, origin, planeOffsets));
							}
						}
					}
				}
			}
		}
		return found;
	}

	private boolean matchesKnownCircuitComponent(LitematicSchematic schematic, int gridX, int gridZ, int rotation, int planeYOffset, BlockPos origin, Set<BlockPos> occupied) {
		int matchedFunctionalBlocks = 0;
		int requiredFunctionalBlocks = 0;
		for (SchematicBlock block : schematic.blocks()) {
			if (!isFunctionalCircuitBlock(block.blockName())) {
				continue;
			}
			requiredFunctionalBlocks++;
			GridPoint rotated = rotatePoint(block.position().x(), block.position().z(), schematic.size().x(), schematic.size().z(), rotation);
			BlockPos worldPos = origin.offset(gridX + rotated.x(), planeYOffset + block.position().y(), gridZ + rotated.z());
			if (occupied.contains(worldPos)) {
				return false;
			}
			if (!worldBlockMatches(block, rotation, minecraft.level.getBlockState(worldPos))) {
				return false;
			}
			matchedFunctionalBlocks++;
		}
		return requiredFunctionalBlocks > 0 && matchedFunctionalBlocks == requiredFunctionalBlocks;
	}

	private static boolean worldBlockMatches(SchematicBlock expected, int rotation, BlockState actual) {
		String actualName = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(actual.getBlock()).toString();
		if (!expected.blockName().equals(actualName)) {
			return false;
		}
		if ("minecraft:redstone_wire".equals(expected.blockName())) {
			return true;
		}
		if (expected.properties().containsKey("facing")) {
			String expectedFacing = rotateFacing(expected.properties().get("facing"), rotation);
			return (actual.hasProperty(BlockStateProperties.FACING)
					&& expectedFacing.equals(actual.getValue(BlockStateProperties.FACING).getName()))
					|| (actual.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
					&& expectedFacing.equals(actual.getValue(BlockStateProperties.HORIZONTAL_FACING).getName()));
		}
		return true;
	}

	private Set<BlockPos> recognizedComponentBlocks(List<ScannedRedstone> components, BlockPos origin, Map<Integer, Integer> planeOffsets) {
		Set<BlockPos> blocks = new LinkedHashSet<>();
		for (ScannedRedstone component : components) {
			blocks.addAll(worldBlocksFor(component, origin, planeOffsets));
		}
		return blocks;
	}

	private static Set<BlockPos> worldBlocksFor(ScannedRedstone component, BlockPos origin, Map<Integer, Integer> planeOffsets) {
		Set<BlockPos> blocks = new LinkedHashSet<>();
		int planeYOffset = planeOffsets.getOrDefault(component.plane(), 0);
		for (SchematicBlock block : component.schematic().blocks()) {
			GridPoint rotated = rotatePoint(block.position().x(), block.position().z(), component.schematic().size().x(), component.schematic().size().z(), component.rotation());
			blocks.add(origin.offset(component.gridX() + rotated.x(), planeYOffset + block.position().y(), component.gridZ() + rotated.z()));
		}
		return blocks;
	}

	private static boolean isKnownCircuitComponent(CircuitComponentType type) {
		return type.hasBundledSchematic()
				&& type != CircuitComponentType.WIRE
				&& type != CircuitComponentType.OBSERVER_WIRE
				&& type != CircuitComponentType.REPEATER_DELAY
				&& type != CircuitComponentType.FOUR_BIT_CALCULATOR_MEMORY;
	}

	private static int redstoneBlockCount(LitematicSchematic schematic) {
		int count = 0;
		for (SchematicBlock block : schematic.blocks()) {
			if (isFunctionalCircuitBlock(block.blockName())) {
				count++;
			}
		}
		return count;
	}

	private static boolean isFunctionalCircuitBlock(String blockName) {
		return isWorldRedstoneComponentName(blockName);
	}

	private static boolean isWorldRedstoneComponentName(String blockName) {
		return blockName.equals("minecraft:redstone_wire")
				|| blockName.equals("minecraft:repeater")
				|| blockName.equals("minecraft:comparator")
				|| blockName.equals("minecraft:observer")
				|| blockName.equals("minecraft:redstone_block")
				|| blockName.equals("minecraft:redstone_torch")
				|| blockName.equals("minecraft:redstone_wall_torch")
				|| blockName.equals("minecraft:lever")
				|| blockName.endsWith("_button")
				|| blockName.equals("minecraft:dispenser")
				|| blockName.equals("minecraft:dropper")
				|| blockName.equals("minecraft:piston")
				|| blockName.equals("minecraft:sticky_piston")
				|| blockName.equals("minecraft:redstone_lamp");
	}

	private ScanBounds scanBounds(BlockPos origin) {
		if (!WorldPlacementState.lastPlacedBlocks().isEmpty()) {
			int minX = Integer.MAX_VALUE;
			int minY = Integer.MAX_VALUE;
			int minZ = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			int maxY = Integer.MIN_VALUE;
			int maxZ = Integer.MIN_VALUE;
			for (BlockPos pos : WorldPlacementState.lastPlacedBlocks().keySet()) {
				minX = Math.min(minX, pos.getX());
				minY = Math.min(minY, pos.getY());
				minZ = Math.min(minZ, pos.getZ());
				maxX = Math.max(maxX, pos.getX());
				maxY = Math.max(maxY, pos.getY());
				maxZ = Math.max(maxZ, pos.getZ());
			}
			BlockPos center = minecraft.player.blockPosition();
			return new ScanBounds(
					Math.min(minX - 8, center.getX() - 24),
					Math.min(minY - 4, center.getY() - 8),
					Math.min(minZ - 8, center.getZ() - 24),
					Math.max(maxX + 8, center.getX() + 24),
					Math.max(maxY + 6, center.getY() + 16),
					Math.max(maxZ + 8, center.getZ() + 24)
			);
		}

		if (!placedComponents.isEmpty()) {
			Bounds bounds = boundsOf(placedComponents);
			return new ScanBounds(
					origin.getX() + bounds.minX() - 8,
					origin.getY() - 4,
					origin.getZ() + bounds.minZ() - 8,
					origin.getX() + bounds.maxX() + 8,
					origin.getY() + 32,
					origin.getZ() + bounds.maxZ() + 8
			);
		}

		BlockPos center = minecraft.player.blockPosition();
		return new ScanBounds(center.getX() - 24, center.getY() - 8, center.getZ() - 24, center.getX() + 24, center.getY() + 16, center.getZ() + 24);
	}

	private void addScannedRedstone(List<ScannedRedstone> scanned) {
		Map<GridPlanePoint, ScannedRedstone> wireCells = new LinkedHashMap<>();
		for (ScannedRedstone redstone : scanned) {
			if (redstone.type() == CircuitComponentType.WIRE || redstone.type() == CircuitComponentType.OBSERVER_WIRE) {
				wireCells.put(new GridPlanePoint(redstone.gridX(), redstone.gridZ(), redstone.plane()), redstone);
			} else {
				placedComponents.add(new PlacedComponent(redstone.schematic(), redstone.gridX(), redstone.gridZ(), redstone.rotation(), nextGroupId++, redstone.plane()));
			}
		}

		Set<GridPlanePoint> visited = new LinkedHashSet<>();
		for (Map.Entry<GridPlanePoint, ScannedRedstone> entry : wireCells.entrySet()) {
			if (!visited.add(entry.getKey())) {
				continue;
			}
			long groupId = nextGroupId++;
			List<GridPlanePoint> queue = new ArrayList<>();
			queue.add(entry.getKey());
			for (int cursor = 0; cursor < queue.size(); cursor++) {
				GridPlanePoint point = queue.get(cursor);
				ScannedRedstone redstone = wireCells.get(point);
				placedComponents.add(new PlacedComponent(redstone.schematic(), redstone.gridX(), redstone.gridZ(), redstone.rotation(), groupId, redstone.plane()));
				for (GridPlanePoint neighbor : point.neighbors()) {
					ScannedRedstone neighborRedstone = wireCells.get(neighbor);
					if (neighborRedstone != null && neighborRedstone.type() == redstone.type() && visited.add(neighbor)) {
						queue.add(neighbor);
					}
				}
			}
		}
	}

	private CircuitComponentType redstoneTypeFromWorldState(BlockState state) {
		if (state.is(Blocks.REDSTONE_WIRE) || state.getBlock() == Blocks.REDSTONE_WIRE) {
			return CircuitComponentType.WIRE;
		}
		if (state.is(Blocks.REPEATER) || state.getBlock() == Blocks.REPEATER) {
			return CircuitComponentType.REPEATER_DELAY;
		}
		if (state.is(Blocks.OBSERVER) || state.getBlock() == Blocks.OBSERVER) {
			return CircuitComponentType.OBSERVER_WIRE;
		}
		if (state.is(Blocks.REDSTONE_BLOCK) || state.getBlock() == Blocks.REDSTONE_BLOCK) {
			return CircuitComponentType.VCC;
		}
		return CircuitComponentType.CUSTOM_MODULE;
	}

	private static boolean isWorldRedstoneComponent(BlockState state) {
		return state.is(Blocks.REDSTONE_WIRE)
				|| state.is(Blocks.REPEATER)
				|| state.is(Blocks.COMPARATOR)
				|| state.is(Blocks.OBSERVER)
				|| state.is(Blocks.REDSTONE_BLOCK)
				|| state.is(Blocks.REDSTONE_TORCH)
				|| state.is(Blocks.REDSTONE_WALL_TORCH)
				|| state.is(Blocks.LEVER)
				|| state.is(Blocks.STONE_BUTTON)
				|| state.is(Blocks.OAK_BUTTON)
				|| state.is(Blocks.SPRUCE_BUTTON)
				|| state.is(Blocks.BIRCH_BUTTON)
				|| state.is(Blocks.JUNGLE_BUTTON)
				|| state.is(Blocks.ACACIA_BUTTON)
				|| state.is(Blocks.CHERRY_BUTTON)
				|| state.is(Blocks.DARK_OAK_BUTTON)
				|| state.is(Blocks.MANGROVE_BUTTON)
				|| state.is(Blocks.BAMBOO_BUTTON)
				|| state.is(Blocks.CRIMSON_BUTTON)
				|| state.is(Blocks.WARPED_BUTTON)
				|| state.is(Blocks.POLISHED_BLACKSTONE_BUTTON)
				|| state.is(Blocks.TRIPWIRE_HOOK)
				|| state.is(Blocks.TARGET)
				|| state.is(Blocks.PISTON)
				|| state.is(Blocks.STICKY_PISTON)
				|| state.is(Blocks.PISTON_HEAD)
				|| state.is(Blocks.DISPENSER)
				|| state.is(Blocks.DROPPER)
				|| state.is(Blocks.HOPPER)
				|| state.is(Blocks.NOTE_BLOCK)
				|| state.is(Blocks.TNT)
				|| state.is(Blocks.REDSTONE_LAMP)
				|| state.is(Blocks.DAYLIGHT_DETECTOR)
				|| state.is(Blocks.SCULK_SENSOR)
				|| state.is(Blocks.CALIBRATED_SCULK_SENSOR);
	}

	private static LitematicSchematic scannedWorldComponentSchematic(BlockState state) {
		String blockName = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
		String displayName = "World " + blockName.substring(blockName.indexOf(':') + 1).replace('_', ' ');
		SchematicBlock block = new SchematicBlock(new de.jesse.schaltplanmod.circuit.SchematicPoint(0, 0, 0), blockName, propertiesFromState(state));
		return new LitematicSchematic(CircuitComponentType.CUSTOM_MODULE, blockName, new de.jesse.schaltplanmod.circuit.LitematicSize(1, 1, 1), List.of(block), List.of(), displayName);
	}

	private static Map<String, String> propertiesFromState(BlockState state) {
		Map<String, String> properties = new LinkedHashMap<>();
		for (Property<?> property : state.getProperties()) {
			properties.put(property.getName(), propertyValueName(state, property));
		}
		return Map.copyOf(properties);
	}

	private static <T extends Comparable<T>> String propertyValueName(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}

	private static GridPoint gridPointForScannedBlock(BlockPos pos, BlockPos origin, LitematicSchematic schematic, CircuitComponentType type, int rotation) {
		if (type == CircuitComponentType.WIRE) {
			return new GridPoint(pos.getX() - origin.getX(), pos.getZ() - origin.getZ());
		}

		SchematicBlock anchor = anchorBlockFor(schematic, type);
		GridPoint rotated = rotatePoint(anchor.position().x(), anchor.position().z(), schematic.size().x(), schematic.size().z(), rotation);
		return new GridPoint(pos.getX() - origin.getX() - rotated.x(), pos.getZ() - origin.getZ() - rotated.z());
	}

	private int planeForScannedBlock(BlockPos pos, BlockPos origin, LitematicSchematic schematic, CircuitComponentType type, Map<Integer, Integer> planeOffsets) {
		if (type == CircuitComponentType.WIRE) {
			int desiredOffset = pos.getY() - origin.getY() - redstoneWireAnchorY(schematic);
			return planeAtOrBelowWorldY(desiredOffset, planeOffsets);
		}

		SchematicBlock anchor = anchorBlockFor(schematic, type);
		return planeAtOrBelowWorldY(pos.getY() - origin.getY() - anchor.position().y(), planeOffsets);
	}

	private static int redstoneWireAnchorY(LitematicSchematic schematic) {
		return schematic.blocks().stream()
				.filter(block -> block.blockName().equals("minecraft:redstone_wire"))
				.mapToInt(block -> block.position().y())
				.min()
				.orElse(0);
	}

	private static int rotationFromWorldState(BlockState state) {
		if (state.hasProperty(BlockStateProperties.FACING)) {
			return rotationFromFacing(state.getValue(BlockStateProperties.FACING));
		}
		if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			return rotationFromFacing(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
		}
		return 0;
	}

	private static int rotationFromFacing(net.minecraft.core.Direction facing) {
		return switch (facing) {
			case SOUTH -> 1;
			case WEST -> 2;
			case NORTH -> 3;
			default -> 0;
		};
	}

	private static SchematicBlock anchorBlockFor(LitematicSchematic schematic, CircuitComponentType type) {
		String blockName = switch (type) {
			case REPEATER_DELAY -> "minecraft:repeater";
			case OBSERVER_WIRE -> "minecraft:observer";
			case VCC -> "minecraft:redstone_block";
			default -> "minecraft:redstone_wire";
		};
		return schematic.blocks().stream()
				.filter(block -> block.blockName().equals(blockName))
				.findFirst()
				.orElseGet(() -> schematic.blocks().getFirst());
	}

	private static int planeForWorldY(int desiredOffset, Map<Integer, Integer> planeOffsets) {
		return planeOffsets.entrySet().stream()
				.min(Comparator.comparingInt(entry -> Math.abs(entry.getValue() - desiredOffset)))
				.map(Map.Entry::getKey)
				.orElse(0);
	}

	private int planeAtOrBelowWorldY(int desiredOffset, Map<Integer, Integer> planeOffsets) {
		return planeOffsets.entrySet().stream()
				.filter(entry -> entry.getValue() <= desiredOffset)
				.max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.orElse(activePlane);
	}

	private boolean nonWireComponentAt(int gridX, int gridZ, int plane) {
		GridPoint point = new GridPoint(gridX, gridZ);
		for (PlacedComponent component : placedComponents) {
			if (component.plane() == plane && !isWorldScannablePart(component) && occupiesGrid(component, point)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isWorldScannablePart(PlacedComponent component) {
		return isWirePart(component) || component.schematic().type() == CircuitComponentType.VCC;
	}

	private Map<BlockPos, String> buildDesiredWorldBlocks(BlockPos origin) {
		Map<BlockPos, String> desiredBlocks = new LinkedHashMap<>();
		Map<Integer, Integer> planeOffsets = planeYOffsetByPlane();
		for (PlacedComponent placed : placedComponents) {
			int planeYOffset = planeOffsets.getOrDefault(placed.plane(), 0);
			for (SchematicBlock block : placed.schematic().blocks()) {
				GridPoint rotated = rotatePoint(block.position().x(), block.position().z(), placed.schematic().size().x(), placed.schematic().size().z(), placed.rotation());
				BlockPos target = origin.offset(
						placed.gridX() + rotated.x(),
						planeYOffset + block.position().y(),
						placed.gridZ() + rotated.z()
				);
				desiredBlocks.put(target, rotatedBlockStateString(block, placed.rotation()));
			}
		}
		return desiredBlocks;
	}

	private Map<Integer, Integer> planeYOffsetByPlane() {
		Map<Integer, Integer> offsets = new LinkedHashMap<>();
		int maxPlane = placedComponents.stream()
				.mapToInt(PlacedComponent::plane)
				.max()
				.orElse(0);
		int yOffset = 0;
		for (int planeIndex = 0; planeIndex <= maxPlane; planeIndex++) {
			offsets.put(planeIndex, yOffset);
			yOffset += Math.max(1, planeHeight(planeIndex));
		}
		return offsets;
	}

	private int planeHeight(int planeIndex) {
		int height = 0;
		for (PlacedComponent component : placedComponents) {
			if (component.plane() == planeIndex) {
				height = Math.max(height, component.schematic().size().y());
			}
		}
		return height;
	}

	private void sendSetBlock(BlockPos target, String blockState) {
		minecraft.getConnection().sendCommand("setblock "
				+ target.getX() + " " + target.getY() + " " + target.getZ() + " "
				+ blockState + " replace");
	}

	private static Comparator<Map.Entry<BlockPos, String>> clearOrder() {
		return Comparator
				.<Map.Entry<BlockPos, String>>comparingInt(entry -> fragileBlockPriority(entry.getValue()))
				.thenComparing((left, right) -> Integer.compare(right.getKey().getY(), left.getKey().getY()));
	}

	private static Comparator<Map.Entry<BlockPos, String>> placeOrder() {
		return Comparator
				.<Map.Entry<BlockPos, String>>comparingInt(entry -> placePriority(entry.getValue()))
				.thenComparingInt(entry -> entry.getKey().getY());
	}

	private static int fragileBlockPriority(String blockState) {
		return isFragileRedstone(blockState) ? 0 : 1;
	}

	private static int placePriority(String blockState) {
		return isFragileRedstone(blockState) ? 1 : 0;
	}

	private static boolean isFragileRedstone(String blockState) {
		return blockState.startsWith("minecraft:redstone_wire")
				|| blockState.startsWith("minecraft:redstone_torch")
				|| blockState.startsWith("minecraft:redstone_wall_torch")
				|| blockState.startsWith("minecraft:repeater")
				|| blockState.startsWith("minecraft:comparator")
				|| blockState.startsWith("minecraft:observer");
	}

	private int gridOriginX() {
		return PANEL_WIDTH + GRID_ORIGIN_X_PADDING - panX * cellSize;
	}

	private int gridOriginY() {
		return TOOLBAR_HEIGHT + GRID_ORIGIN_Y_PADDING - panZ * cellSize;
	}

	private void rotateHoveredOrLast() {
		int index = componentAt(lastMouseX, lastMouseY);
		if (index < 0 && !placedComponents.isEmpty()) {
			index = placedComponents.size() - 1;
		}

		if (index >= 0) {
			rotateComponentOrGroup(index);
		}
	}

	private int lastMouseX;
	private int lastMouseY;

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		lastMouseX = (int) mouseX;
		lastMouseY = (int) mouseY;
		int paletteIndex = mouseX < PANEL_WIDTH ? paletteIndexAt((int) mouseY) : -1;
		if (paletteIndex >= 0 && paletteIndex != hoveredPaletteIndex) {
		}
		hoveredPaletteIndex = paletteIndex;
		super.mouseMoved(mouseX, mouseY);
	}

	private static int rotatedSizeX(PlacedComponent placed) {
		return placed.rotation() % 2 == 0 ? placed.schematic().size().x() : placed.schematic().size().z();
	}

	private static int rotatedSizeZ(PlacedComponent placed) {
		return placed.rotation() % 2 == 0 ? placed.schematic().size().z() : placed.schematic().size().x();
	}

	private static GridPoint rotatePoint(int x, int z, int sizeX, int sizeZ, int rotation) {
		return switch (rotation) {
			case 1 -> new GridPoint(sizeZ - 1 - z, x);
			case 2 -> new GridPoint(sizeX - 1 - x, sizeZ - 1 - z);
			case 3 -> new GridPoint(z, sizeX - 1 - x);
			default -> new GridPoint(x, z);
		};
	}

	private static String rotatedBlockStateString(SchematicBlock block, int rotation) {
		if (block.properties().isEmpty()) {
			return block.blockName();
		}

		Map<String, String> properties = block.properties().entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> {
					if ("facing".equals(entry.getKey())) {
						return rotateFacing(entry.getValue(), rotation);
					}
					return entry.getValue();
				}));

		return block.blockName() + properties.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.map(entry -> entry.getKey() + "=" + entry.getValue())
				.collect(Collectors.joining(",", "[", "]"));
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

	private static int simpleColor() {
		return 0xff223348;
	}

	private static int colorFor(String blockName) {
		return switch (blockName) {
			case "minecraft:lime_concrete" -> 0xff4fcf63;
			case "minecraft:pink_concrete" -> 0xffff69c9;
			case "minecraft:yellow_concrete" -> 0xffe8d75a;
			case "minecraft:redstone_wire" -> 0xffb12222;
			case "minecraft:redstone_torch", "minecraft:redstone_wall_torch" -> 0xffff563f;
			case "minecraft:repeater" -> 0xffb7aca0;
			case "minecraft:redstone_lamp", "minecraft:waxed_copper_bulb" -> 0xffffb84a;
			case "minecraft:lever", "minecraft:stone_button" -> 0xff9ca6ad;
			case "minecraft:redstone_block" -> 0xffd3162c;
			default -> 0xff8793a0;
		};
	}

	private static int dimColor(int color) {
		return (color & 0x00ffffff) | 0x88000000;
	}

	private static int ghostColor(int color) {
		return (color & 0x00ffffff) | 0x55000000;
	}

	private enum EditorTool {
		POINTER("Pointer", ">"),
		SELECT("Select", "[]"),
		ROTATE("Rotate", "R"),
		PLACE("Place", "+");

		private final String displayName;
		private final String icon;

		EditorTool(String displayName, String icon) {
			this.displayName = displayName;
			this.icon = icon;
		}

		private String displayName() {
			return displayName;
		}

		private String icon() {
			return icon;
		}
	}

	private record Bounds(int minX, int minZ, int maxX, int maxZ) {
		private int centerX() {
			return (minX + maxX) / 2;
		}

		private int centerZ() {
			return (minZ + maxZ) / 2;
		}

		private int width() {
			return maxX - minX + 1;
		}

		private int height() {
			return maxZ - minZ + 1;
		}
	}

	private enum Direction {
		EAST(1, 0),
		SOUTH(0, 1),
		WEST(-1, 0),
		NORTH(0, -1);

		private final int deltaX;
		private final int deltaZ;

		Direction(int deltaX, int deltaZ) {
			this.deltaX = deltaX;
			this.deltaZ = deltaZ;
		}

		private GridPoint offset(int x, int z) {
			return new GridPoint(x + deltaX, z + deltaZ);
		}

		private Direction opposite() {
			return switch (this) {
				case EAST -> WEST;
				case SOUTH -> NORTH;
				case WEST -> EAST;
				case NORTH -> SOUTH;
			};
		}

		private static Direction fromRotation(int rotation) {
			return switch (Math.floorMod(rotation, 4)) {
				case 1 -> SOUTH;
				case 2 -> WEST;
				case 3 -> NORTH;
				default -> EAST;
			};
		}
	}

	private record GridPoint(int x, int z) {
	}

	private record RoutedPoint(GridPoint point, int plane) {
	}

	private record PathNode(GridPoint point, int cost, int priority) {
	}

	private record ScannedRedstone(CircuitComponentType type, int gridX, int gridZ, int rotation, int plane, LitematicSchematic schematic) {
	}

	private record GridPlanePoint(int x, int z, int plane) {
		private List<GridPlanePoint> neighbors() {
			return List.of(
					new GridPlanePoint(x + 1, z, plane),
					new GridPlanePoint(x - 1, z, plane),
					new GridPlanePoint(x, z + 1, plane),
					new GridPlanePoint(x, z - 1, plane)
			);
		}
	}

	private record ScanBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		private boolean containsGrid(int gridX, int gridZ, BlockPos origin) {
			int worldX = origin.getX() + gridX;
			int worldZ = origin.getZ() + gridZ;
			return worldX >= minX && worldX <= maxX && worldZ >= minZ && worldZ <= maxZ;
		}
	}
}
