package de.jesse.schaltplanmod.circuit;

import java.util.Arrays;
import java.util.List;

public enum CircuitComponentType {
	AND("AND", "and.litematic", CircuitComponentCategory.GATES),
	OR("OR", "or.litematic", CircuitComponentCategory.GATES),
	NOT("NOT", "not.litematic", CircuitComponentCategory.GATES),
	NAND("NAND", "nand.litematic", CircuitComponentCategory.GATES),
	XOR("XOR", "xor.litematic", CircuitComponentCategory.GATES),
	COMPACT_FULL_ADDER("Compact Full Adder", "compact_full_adder.litematic", CircuitComponentCategory.GATES),
	WIRE("Wire", "wire_redstone_dust.litematic", CircuitComponentCategory.WIRING),
	OBSERVER_WIRE("Observer Wire", "wire_observer.litematic", CircuitComponentCategory.WIRING),
	REPEATER_DELAY("Repeater / Delay", "repeater_delay_element.litematic", CircuitComponentCategory.WIRING),
	ONE_TICK_PULSER("Pulser (1 Tick)", "one_tick_pulser.litematic", CircuitComponentCategory.WIRING),
	CLOCK("Clock", "clock.litematic", CircuitComponentCategory.WIRING),
	OBSERVER_CLOCK("Observer Clock", "observer_clock.litematic", CircuitComponentCategory.WIRING),
	SIMPLE_4_TO_3_ENCODER("Simple 4->3 Encoder", "simple_4_to_3_encoder.litematic", CircuitComponentCategory.ENCODERS),
	SMART_4_TO_2_ENCODER("Smart 4->2 Encoder", "smart_4_to_2_encoder.litematic", CircuitComponentCategory.ENCODERS),
	SMART_4_TO_3_DECODER("Smart Decoder (3->4)", "smart_4_to_3_decoder.litematic", CircuitComponentCategory.ENCODERS),
	SMART_3_TO_8_DECODER("Smart Decoder (3->8)", "smart_3_to_8_decoder.litematic", CircuitComponentCategory.ENCODERS),
	LATCH("Latch", "latch.litematic", CircuitComponentCategory.MEMORY),
	D_FLIPFLOP("D-FlipFlop", "d_flipflop.litematic", CircuitComponentCategory.MEMORY),
	T_FLIPFLOP_COPPER_BULB("T-Flip-Flop Lamp", "t_flipflop_copper_bulb.litematic", CircuitComponentCategory.MEMORY),
	BUTTON("Button", "button.litematic", CircuitComponentCategory.IO),
	SWITCH("Switch", "switch.litematic", CircuitComponentCategory.IO),
	VCC("VCC Constant Source", "vcc_permanent_source.litematic", CircuitComponentCategory.IO),
	LAMP("Lamp", "lamp.litematic", CircuitComponentCategory.IO),
	FOUR_BIT_CALCULATOR_MEMORY("4-bit Calculator + Memory", "4bit_calculator.litematic", CircuitComponentCategory.MODULES),
	CUSTOM_MODULE("Custom Module", "", CircuitComponentCategory.MODULES);

	public static final List<CircuitComponentType> MENU_ORDER = Arrays.asList(values());
	public static final String SCHEMATIC_RESOURCE_FOLDER = "assets/schaltplanmod/schematics/";

	private final String displayName;
	private final String fileName;
	private final CircuitComponentCategory category;

	CircuitComponentType(String displayName, String fileName, CircuitComponentCategory category) {
		this.displayName = displayName;
		this.fileName = fileName;
		this.category = category;
	}

	public String displayName() {
		return displayName;
	}

	public String fileName() {
		return fileName;
	}

	public CircuitComponentCategory category() {
		return category;
	}

	public String resourcePath() {
		return SCHEMATIC_RESOURCE_FOLDER + fileName;
	}

	public boolean hasBundledSchematic() {
		return !fileName.isBlank();
	}
}
