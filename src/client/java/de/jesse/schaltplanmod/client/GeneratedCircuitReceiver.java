package de.jesse.schaltplanmod.client;

import de.jesse.schaltplanmod.circuit.CircuitComponentType;
import de.jesse.schaltplanmod.circuit.LitematicSchematic;

public interface GeneratedCircuitReceiver {
	void addGeneratedComponent(CircuitComponentType type, int gridX, int gridZ);

	void addGeneratedComponent(CircuitComponentType type, int gridX, int gridZ, int rotation);

	void addGeneratedWire(int startX, int startZ, int endX, int endZ);

	void addGeneratedSchematic(LitematicSchematic schematic, int gridX, int gridZ);
}
