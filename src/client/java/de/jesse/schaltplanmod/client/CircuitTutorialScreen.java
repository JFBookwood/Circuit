package de.jesse.schaltplanmod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;

import java.util.List;

public class CircuitTutorialScreen extends Screen {
	private static final List<Page> PAGES = List.of(
			new Page("Welcome to Circuit", Icon.GRID, 0xff56d8ff, List.of(
					"Circuit is a top-down redstone planning editor.",
					"Place gates, inputs, outputs, memory parts, and wires on a grid.",
					"When the plan is ready, press Sync to place it in the world."
			)),
			new Page("Editing Basics", Icon.CURSOR, 0xffffd166, List.of(
					"Left click places the selected component.",
					"Drag existing components to move them.",
					"Right click deletes a component or a whole wire group.",
					"Press R to rotate the hovered component."
			)),
			new Page("Navigation", Icon.PAN, 0xff9dff8a, List.of(
					"Hold the middle mouse button and drag to pan the editor.",
					"Use the zoom buttons to change grid size.",
					"Use Simple View for labels and Detailed View for exact blocks."
			)),
			new Page("Wires and Modules", Icon.WIRE, 0xffff6ea8, List.of(
					"Select Wire or Observer Wire, then drag to draw a connection.",
					"Redstone wires can bend; observer wires stay straight.",
					"Open Load or Import to use your saved circuits and modules."
			)),
			new Page("Saving and World Sync", Icon.SAVE, 0xffb79cff, List.of(
					"Save writes a .bcdi circuit plan into the config folder.",
					"Generated structures are embedded so they load correctly later.",
					"Sync replaces the previous editor placement instead of duplicating it."
			))
	);

	private final Screen parent;
	private int page;

	public CircuitTutorialScreen(Screen parent) {
		super(Component.literal("Circuit Tutorial"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int center = width / 2;
		addRenderableWidget(Button.builder(Component.literal(page == PAGES.size() - 1 ? "Start Editing" : "Next"), button -> {
			if (page < PAGES.size() - 1) {
				page++;
				rebuildWidgets();
				return;
			}
			CircuitTutorialState.markSeen();
			minecraft.setScreen(new SchaltplanEditorScreen(parent));
		}).bounds(center + 18, height - 44, 128, 22).build());

		addRenderableWidget(Button.builder(Component.literal("Skip"), button -> {
			CircuitTutorialState.markSeen();
			minecraft.setScreen(new SchaltplanEditorScreen(parent));
		}).bounds(center - 146, height - 44, 128, 22).build());

		if (page > 0) {
			addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
				page--;
				rebuildWidgets();
			}).bounds(center - 64, height - 72, 128, 20).build());
		}
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
		graphics.fill(0, 0, width, height, 0xff0c1117);
		drawBackgroundGrid(graphics);

		int panelWidth = Math.min(500, width - 32);
		int panelHeight = Math.min(250, height - 84);
		int panelLeft = width / 2 - panelWidth / 2;
		int panelTop = Math.max(24, height / 2 - panelHeight / 2 - 8);
		graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xee161d27);
		graphics.renderOutline(panelLeft, panelTop, panelWidth, panelHeight, 0xff3b4654);

		Page current = PAGES.get(page);
		float pulse = pulse(partialTick);
		int iconLeft = panelLeft + 26;
		int iconTop = panelTop + 24;
		drawIconCard(graphics, iconLeft, iconTop, current, pulse);

		int textLeft = panelLeft + 104;
		graphics.drawString(font, current.title(), textLeft, panelTop + 26, 0xffffffff, false);
		graphics.drawString(font, "Step " + (page + 1) + " of " + PAGES.size(), textLeft, panelTop + 44, 0xff98a7b8, false);
		drawProgress(graphics, textLeft, panelTop + 62, panelLeft + panelWidth - 28);

		int y = panelTop + 88;
		for (String line : current.lines()) {
			graphics.drawString(font, line, textLeft, y, 0xffd7dee8, false);
			y += 17;
		}

		drawPreview(graphics, panelLeft + 24, panelTop + panelHeight - 84, panelWidth - 48, 54, current, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void drawBackgroundGrid(GuiGraphics graphics) {
		int spacing = 18;
		int offset = (int) ((System.currentTimeMillis() / 60L) % spacing);
		for (int x = -offset; x < width; x += spacing) {
			graphics.fill(x, 0, x + 1, height, 0x221f2b36);
		}
		for (int y = -offset; y < height; y += spacing) {
			graphics.fill(0, y, width, y + 1, 0x221f2b36);
		}
	}

	private void drawIconCard(GuiGraphics graphics, int x, int y, Page page, float pulse) {
		int glow = 40 + (int) (35 * pulse);
		graphics.fill(x - 3, y - 3, x + 67, y + 67, (glow << 24) | (page.color() & 0x00ffffff));
		graphics.fill(x, y, x + 64, y + 64, 0xff202a36);
		graphics.renderOutline(x, y, 64, 64, page.color());
		drawIcon(graphics, page.icon(), x + 16, y + 16, 32, page.color());
	}

	private void drawProgress(GuiGraphics graphics, int x, int y, int right) {
		graphics.fill(x, y, right, y + 4, 0xff27313d);
		int filled = x + (int) ((right - x) * ((page + 1) / (float) PAGES.size()));
		graphics.fill(x, y, filled, y + 4, PAGES.get(page).color());
		for (int index = 0; index < PAGES.size(); index++) {
			int dotX = x + index * 16;
			int color = index <= page ? PAGES.get(index).color() : 0xff506070;
			graphics.fill(dotX, y + 10, dotX + 8, y + 18, color);
		}
	}

	private void drawPreview(GuiGraphics graphics, int x, int y, int width, int height, Page page, float partialTick) {
		graphics.fill(x, y, x + width, y + height, 0xff0f151d);
		graphics.renderOutline(x, y, width, height, 0xff314050);
		float t = ((System.currentTimeMillis() % 2400L) + partialTick * 50.0f) / 2400.0f;
		int left = x + 18;
		int centerY = y + height / 2;
		int right = x + width - 22;
		int signalX = Mth.lerpInt(t, left + 8, right - 8);

		for (int px = left; px < right; px += 12) {
			graphics.fill(px, centerY - 1, Math.min(px + 8, right), centerY + 1, 0xff7b2727);
		}
		graphics.fill(signalX - 3, centerY - 4, signalX + 4, centerY + 5, page.color());
		drawMiniGate(graphics, x + width / 2 - 14, centerY - 14, page.color());
		drawIcon(graphics, page.icon(), left - 5, centerY - 10, 18, page.color());
		drawIcon(graphics, Icon.SAVE, right - 14, centerY - 10, 18, 0xffd7dee8);
	}

	private void drawMiniGate(GuiGraphics graphics, int x, int y, int color) {
		graphics.fill(x, y, x + 28, y + 28, 0xff27313d);
		graphics.renderOutline(x, y, 28, 28, color);
		graphics.drawCenteredString(font, "XOR", x + 14, y + 10, 0xffffffff);
	}

	private void drawIcon(GuiGraphics graphics, Icon icon, int x, int y, int size, int color) {
		int mid = x + size / 2;
		switch (icon) {
			case GRID -> {
				for (int i = 0; i <= 3; i++) {
					int p = x + i * size / 3;
					graphics.fill(p, y, p + 1, y + size, color);
					graphics.fill(x, p, x + size, p + 1, color);
				}
			}
			case CURSOR -> {
				graphics.fill(x + 4, y + 2, x + 8, y + size - 4, color);
				graphics.fill(x + 8, y + size - 8, x + size - 4, y + size - 4, color);
				graphics.fill(x + 8, y + 8, x + size - 6, y + 12, color);
				graphics.fill(x + size - 10, y + 12, x + size - 6, y + size - 4, color);
			}
			case PAN -> {
				graphics.fill(mid - 2, y + 2, mid + 2, y + size - 2, color);
				graphics.fill(x + 2, y + size / 2 - 2, x + size - 2, y + size / 2 + 2, color);
				graphics.fill(mid - 6, y + 2, mid + 6, y + 6, color);
				graphics.fill(mid - 6, y + size - 6, mid + 6, y + size - 2, color);
				graphics.fill(x + 2, y + size / 2 - 6, x + 6, y + size / 2 + 6, color);
				graphics.fill(x + size - 6, y + size / 2 - 6, x + size - 2, y + size / 2 + 6, color);
			}
			case WIRE -> {
				graphics.fill(x + 2, y + size / 2 - 2, x + size / 2, y + size / 2 + 2, color);
				graphics.fill(x + size / 2 - 2, y + 6, x + size / 2 + 2, y + size - 6, color);
				graphics.fill(x + size / 2, y + 6, x + size - 2, y + 10, color);
				graphics.fill(x + size / 2, y + size - 10, x + size - 2, y + size - 6, color);
			}
			case SAVE -> {
				graphics.fill(x + 3, y + 2, x + size - 3, y + size - 2, color);
				graphics.fill(x + 7, y + 5, x + size - 8, y + 11, 0xff0f151d);
				graphics.fill(x + 8, y + size - 10, x + size - 8, y + size - 4, 0xff0f151d);
			}
		}
	}

	private float pulse(float partialTick) {
		return (Mth.sin((System.currentTimeMillis() + partialTick * 50.0f) / 260.0f) + 1.0f) * 0.5f;
	}

	private enum Icon {
		GRID,
		CURSOR,
		PAN,
		WIRE,
		SAVE
	}

	private record Page(String title, Icon icon, int color, List<String> lines) {
	}
}
