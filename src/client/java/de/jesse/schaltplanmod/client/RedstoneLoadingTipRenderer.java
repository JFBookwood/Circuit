package de.jesse.schaltplanmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class RedstoneLoadingTipRenderer {
	private RedstoneLoadingTipRenderer() {
	}

	public static void render(GuiGraphics graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		String tip = RedstoneTipProvider.loadingTip();
		int maxWidth = Math.max(120, graphics.guiWidth() - 80);
		int x = 40;
		int y = Math.max(18, graphics.guiHeight() - 42);
		graphics.fill(x - 8, y - 8, graphics.guiWidth() - 32, y + 22, 0x99000000);
		graphics.drawString(minecraft.font, "Redstone tip", x, y - 1, 0xffff5555, false);
		graphics.drawWordWrap(minecraft.font, Component.literal(tip), x + 78, y - 1, maxWidth - 78, 0xffffffff);
	}
}
