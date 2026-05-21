package de.jesse.schaltplanmod.client;

import de.jesse.schaltplanmod.circuit.LitematicSchematic;

public record PlacedComponent(LitematicSchematic schematic, int gridX, int gridZ, int rotation, long groupId, int plane) {
	public PlacedComponent(LitematicSchematic schematic, int gridX, int gridZ) {
		this(schematic, gridX, gridZ, 0, 0L);
	}

	public PlacedComponent(LitematicSchematic schematic, int gridX, int gridZ, long groupId) {
		this(schematic, gridX, gridZ, 0, groupId);
	}

	public PlacedComponent(LitematicSchematic schematic, int gridX, int gridZ, int rotation, long groupId) {
		this(schematic, gridX, gridZ, rotation, groupId, 0);
	}

	public PlacedComponent moveTo(int gridX, int gridZ) {
		return new PlacedComponent(schematic, gridX, gridZ, rotation, groupId, plane);
	}

	public PlacedComponent moveBy(int deltaX, int deltaZ) {
		return moveTo(gridX + deltaX, gridZ + deltaZ);
	}

	public PlacedComponent rotateClockwise() {
		return new PlacedComponent(schematic, gridX, gridZ, (rotation + 1) % 4, groupId, plane);
	}

	public PlacedComponent onPlane(int plane) {
		return new PlacedComponent(schematic, gridX, gridZ, rotation, groupId, Math.max(0, plane));
	}
}
