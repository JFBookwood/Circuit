package de.jesse.schaltplanmod.client;

import de.jesse.schaltplanmod.circuit.CircuitComponentType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;

public class EncoderDecoderGeneratorScreen extends Screen {
	private final Screen parent;
	private final GeneratedCircuitReceiver receiver;
	private EditBox inputCount;
	private EditBox outputCount;
	private boolean decoder;
	private String message = "Uses smart templates when available, otherwise generates a structured NBT schematic.";

	public EncoderDecoderGeneratorScreen(Screen parent, GeneratedCircuitReceiver receiver) {
		super(Component.literal("Encoder / Decoder Generator"));
		this.parent = parent;
		this.receiver = receiver;
	}

	@Override
	protected void init() {
		int center = width / 2;
		inputCount = new EditBox(font, center - 76, height / 2 - 42, 54, 20, Component.literal("Inputs"));
		inputCount.setValue("4");
		inputCount.setFilter(value -> value.matches("\\d{0,2}"));
		addRenderableWidget(inputCount);

		outputCount = new EditBox(font, center + 22, height / 2 - 42, 54, 20, Component.literal("Outputs"));
		outputCount.setValue("2");
		outputCount.setFilter(value -> value.matches("\\d{0,2}"));
		addRenderableWidget(outputCount);

		addRenderableWidget(Button.builder(Component.literal("Encoder"), button -> {
			decoder = false;
			outputCount.setValue("2");
			message = "Encoder: 4->2 Smart and 4->3 Simple templates are available.";
		}).bounds(center - 96, height / 2 - 10, 92, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Decoder"), button -> {
			decoder = true;
			inputCount.setValue("3");
			outputCount.setValue("8");
			message = "Decoder: Smart 3->4 and 3->8 templates are available.";
		}).bounds(center + 4, height / 2 - 10, 92, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Generate"), button -> generate()).bounds(center - 96, height / 2 + 22, 92, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
			minecraft.setScreen(parent);
		}).bounds(center + 4, height / 2 + 22, 92, 20).build());
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xff101318);
		graphics.fill(width / 2 - 128, height / 2 - 100, width / 2 + 128, height / 2 + 88, 0xff18202a);
		graphics.renderOutline(width / 2 - 128, height / 2 - 100, 256, 188, 0xff3b4654);
		graphics.drawCenteredString(font, title, width / 2, height / 2 - 82, 0xffffffff);
		graphics.drawString(font, "Inputs", width / 2 - 76, height / 2 - 56, 0xffd7dee8, false);
		graphics.drawString(font, "Outputs", width / 2 + 22, height / 2 - 56, 0xffd7dee8, false);
		graphics.drawCenteredString(font, message, width / 2, height / 2 + 56, 0xffd7dee8);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void generate() {
		int inputs = parse(inputCount.getValue());
		int outputs = parse(outputCount.getValue());
		String error = decoder ? validateDecoder(inputs, outputs) : validateEncoder(inputs, outputs);
		if (error != null) {
			message = error;
			return;
		}

		try {
			CircuitComponentType template = templateFor(inputs, outputs, decoder);
			if (template != null) {
				receiver.addGeneratedComponent(template, 0, 0);
				minecraft.setScreen(parent);
				return;
			}

			Path generated = decoder
					? SmartMatrixStructureGenerator.writeDecoder(inputs, outputs)
					: SmartMatrixStructureGenerator.writeEncoder(inputs, outputs);
			receiver.addGeneratedSchematic(StructureNbtSchematicReader.read(
					generated,
					decoder ? CircuitComponentType.SMART_4_TO_3_DECODER : CircuitComponentType.SMART_4_TO_2_ENCODER,
					(decoder ? "Generated Decoder " : "Generated Encoder ") + inputs + "->" + outputs
			), 0, 0);
			minecraft.setScreen(parent);
		} catch (IOException exception) {
			message = "Could not generate NBT: " + exception.getMessage();
		}
	}

	private static CircuitComponentType templateFor(int inputs, int outputs, boolean decoder) {
		if (decoder && inputs == 3 && outputs == 4) {
			return CircuitComponentType.SMART_4_TO_3_DECODER;
		}
		if (decoder && inputs == 3 && outputs == 8) {
			return CircuitComponentType.SMART_3_TO_8_DECODER;
		}
		if (!decoder && inputs == 4 && outputs == 2) {
			return CircuitComponentType.SMART_4_TO_2_ENCODER;
		}
		if (!decoder && inputs == 4 && outputs == 3) {
			return CircuitComponentType.SIMPLE_4_TO_3_ENCODER;
		}
		return null;
	}

	private static String validateEncoder(int inputs, int outputs) {
		if (inputs < 2) {
			return "Encoder needs at least 2 inputs.";
		}
		if (outputs < 1) {
			return "Encoder needs at least 1 output.";
		}
		if (inputs <= outputs) {
			return "Encoder inputs must be greater than outputs.";
		}
		if (inputs > (1 << outputs)) {
			return "Encoder outputs cannot represent " + inputs + " inputs.";
		}
		return null;
	}

	private static String validateDecoder(int inputs, int outputs) {
		if (inputs < 1) {
			return "Decoder needs at least 1 input.";
		}
		if (outputs < 1) {
			return "Decoder needs at least 1 output.";
		}
		if (outputs > (1 << inputs)) {
			return "Decoder outputs cannot exceed 2^inputs.";
		}
		return null;
	}

	private static int parse(String value) {
		if (value.isBlank()) {
			return 0;
		}

		return Integer.parseInt(value);
	}
}
