package de.jesse.schaltplanmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class GeneratorWarningScreen extends Screen {
	private final Screen parent;
	private final GeneratedCircuitReceiver receiver;
	private boolean dontShowAgain;

	public GeneratorWarningScreen(Screen parent, GeneratedCircuitReceiver receiver) {
		super(Component.literal(isGerman() ? "Experimentelles Feature" : "Experimental Feature"));
		this.parent = parent;
		this.receiver = receiver;
	}

	@Override
	protected void init() {
		int center = width / 2;
		addRenderableWidget(Button.builder(Component.literal(isGerman() ? "Weiter" : "Continue"), button -> {
			if (dontShowAgain) {
				GeneratorWarningState.hidePermanently();
			}
			minecraft.setScreen(new EncoderDecoderGeneratorScreen(parent, receiver));
		}).bounds(center - 104, height / 2 + 44, 96, 20).build());
		addRenderableWidget(Button.builder(Component.literal(isGerman() ? "Zurueck" : "Back"), button -> {
			minecraft.setScreen(parent);
		}).bounds(center + 8, height / 2 + 44, 96, 20).build());
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xcc101318);
		int left = width / 2 - 150;
		int top = height / 2 - 82;
		graphics.fill(left, top, left + 300, top + 148, 0xff18202a);
		graphics.renderOutline(left, top, 300, 148, 0xff596779);
		graphics.drawCenteredString(font, title, width / 2, top + 14, 0xffffffff);
		graphics.drawWordWrap(font, Component.literal(bodyText()), left + 18, top + 38, 264, 0xffd7dee8);

		int checkX = left + 18;
		int checkY = top + 96;
		graphics.renderOutline(checkX, checkY, 10, 10, 0xffd7dee8);
		if (dontShowAgain) {
			graphics.fill(checkX + 2, checkY + 2, checkX + 9, checkY + 9, 0xff82d982);
		}
		graphics.drawString(font, isGerman() ? "Nicht wieder anzeigen" : "Don't show again", checkX + 16, checkY + 1, 0xffd7dee8, false);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int left = width / 2 - 150;
		int checkX = left + 18;
		int checkY = height / 2 + 14;
		if (mouseX >= checkX && mouseX <= checkX + 145 && mouseY >= checkY - 4 && mouseY <= checkY + 15) {
			dontShowAgain = !dontShowAgain;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private static String bodyText() {
		if (isGerman()) {
			return "Der Encoder/Decoder-Generator ist noch experimentell. Er kann unvollstaendige oder falsche Redstone-Strukturen erzeugen. Bitte pruefe generierte Schaltungen, bevor du sie in groesseren Builds benutzt.";
		}
		return "The encoder/decoder generator is still experimental. It may create incomplete or incorrect redstone structures. Please verify generated circuits before using them in larger builds.";
	}

	private static boolean isGerman() {
		Minecraft minecraft = Minecraft.getInstance();
		String language = minecraft == null || minecraft.options == null ? "" : minecraft.options.languageCode;
		return language != null && language.toLowerCase(java.util.Locale.ROOT).startsWith("de");
	}
}
