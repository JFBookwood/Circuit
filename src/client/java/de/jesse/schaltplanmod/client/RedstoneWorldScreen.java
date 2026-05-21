package de.jesse.schaltplanmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RedstoneWorldScreen extends Screen {
	private final Screen parent;

	public RedstoneWorldScreen(Screen parent) {
		super(Component.literal("Redstone World"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int center = width / 2;
		addRenderableWidget(Button.builder(Component.literal("Create Redstone World"), button -> {
					RedstoneWorldPresetFlow.open(Minecraft.getInstance(), this);
				})
				.bounds(center - 110, height / 2 + 28, 220, 20)
				.build());
		addRenderableWidget(Button.builder(Component.literal("Prepare Current World"), button -> {
			RedstoneWorldInitializer.prepareCurrentWorld();
			minecraft.setScreen(parent);
		}).bounds(center - 110, height / 2 + 54, 220, 20)
				.build());
		addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
					minecraft.setScreen(parent);
				})
				.bounds(center - 110, height / 2 + 80, 220, 20)
				.build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xff101318);
		graphics.drawCenteredString(font, title, width / 2, height / 2 - 66, 0xffffffff);
		graphics.drawCenteredString(font, "Opens vanilla world creation with Circuit's redstone preset already selected.", width / 2, height / 2 - 36, 0xffd7dee8);
		graphics.drawCenteredString(font, "Creative, peaceful, flat world, structures off, cheats on, and redstone-friendly game rules.", width / 2, height / 2 - 20, 0xffaeb8c5);
		graphics.drawCenteredString(font, "Use Prepare Current World only for older worlds that already exist.", width / 2, height / 2 - 4, 0xffffd27a);
		super.render(graphics, mouseX, mouseY, partialTick);
	}
}
