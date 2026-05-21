package de.jesse.schaltplanmod.circuit;

public enum CircuitComponentCategory {
	GATES("Gates"),
	WIRING("Wires & Timing"),
	ENCODERS("Encoder / Decoder"),
	MEMORY("Memory"),
	IO("Input / Output"),
	MODULES("Custom Modules");

	private final String displayName;

	CircuitComponentCategory(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}
}
