package de.jesse.schaltplanmod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;

public class PlanSaveScreen extends Screen {
	private final SchaltplanEditorScreen editor;
	private final Screen parent;
	private EditBox nameBox;
	private List<Path> files = List.of();
	private String message = "";

	public PlanSaveScreen(SchaltplanEditorScreen editor, Screen parent) {
		super(Component.literal("Save Circuit"));
		this.editor = editor;
		this.parent = parent;
	}

	@Override
	protected void init() {
		files = SchaltplanPlanStorage.listPlanFiles();
		int center = width / 2;
		nameBox = new EditBox(font, center - 154, 48, 180, 20, Component.literal("Name"));
		nameBox.setValue("default");
		nameBox.setMaxLength(40);
		addRenderableWidget(nameBox);

		addRenderableWidget(Button.builder(Component.literal("Save"), button -> {
			editor.savePlanNamed(nameBox.getValue());
			message = "Saved: " + nameBox.getValue();
			rebuildWidgets();
		}).bounds(center + 34, 48, 58, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
			minecraft.setScreen(parent);
		}).bounds(center + 98, 48, 58, 20).build());

		int y = 88;
		int maxVisible = Math.min(files.size(), 9);
		for (int index = 0; index < maxVisible; index++) {
			Path file = files.get(index);
			addRenderableWidget(Button.builder(Component.literal(displayLabel(file)), button -> {
				nameBox.setValue(stripExtension(file.getFileName().toString()));
			}).bounds(center - 154, y, 212, 20).build());
			addRenderableWidget(Button.builder(Component.literal("Delete"), button -> {
				SchaltplanPlanStorage.deletePlanFile(file);
				message = "Deleted: " + file.getFileName();
				rebuildWidgets();
			}).bounds(center + 66, y, 90, 20).build());
			y += 24;
		}
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xff101318);
		graphics.drawCenteredString(font, title, width / 2, 22, 0xffffffff);
		graphics.drawString(font, "Name", width / 2 - 154, 36, 0xffd7dee8, false);
		if (files.isEmpty()) {
			graphics.drawCenteredString(font, "No saved circuits yet.", width / 2, 90, 0xffd7dee8);
		}
		if (!message.isBlank()) {
			graphics.drawCenteredString(font, message, width / 2, height - 28, 0xff82d982);
		}
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private static String displayLabel(Path file) {
		Path parent = file.getParent();
		if (parent != null && parent.endsWith("modules")) {
			return "module/" + file.getFileName();
		}
		return file.getFileName().toString();
	}

	private static String stripExtension(String fileName) {
		return fileName.endsWith(".bcdi") ? fileName.substring(0, fileName.length() - 5) : fileName;
	}
}
