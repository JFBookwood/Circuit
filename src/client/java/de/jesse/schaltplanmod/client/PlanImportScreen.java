package de.jesse.schaltplanmod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;

public class PlanImportScreen extends Screen {
	private final SchaltplanEditorScreen editor;
	private final Screen parent;
	private final boolean append;
	private List<Path> files = List.of();

	public PlanImportScreen(SchaltplanEditorScreen editor, Screen parent, boolean append) {
		super(Component.literal(append ? "Import Circuit" : "Load Circuit"));
		this.editor = editor;
		this.parent = parent;
		this.append = append;
	}

	@Override
	protected void init() {
		files = SchaltplanPlanStorage.listPlanFiles();
		int listX = width / 2 - 150;
		int y = 54;
		int maxVisible = Math.min(files.size(), 12);

		for (int index = 0; index < maxVisible; index++) {
			Path file = files.get(index);
			String label = displayLabel(file);
			addRenderableWidget(Button.builder(Component.literal(label), button -> {
				editor.loadPlanFromFile(file, append);
				minecraft.setScreen(parent);
			}).bounds(listX, y, 300, 20).build());
			y += 24;
		}

		addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
					minecraft.setScreen(parent);
				})
				.bounds(width / 2 - 60, height - 34, 120, 20)
				.build());
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xff101318);
		graphics.drawCenteredString(font, title, width / 2, 24, 0xffffffff);
		if (files.isEmpty()) {
			graphics.drawCenteredString(font, "No .bcdi circuit files found.", width / 2, 70, 0xffd7dee8);
		}
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private static String displayLabel(Path file) {
		Path parent = file.getParent();
		if (parent != null && parent.endsWith("modules")) {
			return "module/" + file.getFileName();
		}
		if (parent != null && parent.endsWith("presets")) {
			return "preset/" + file.getFileName();
		}
		return file.getFileName().toString();
	}
}
