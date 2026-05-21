package de.jesse.schaltplanmod.circuit;

public record SchematicPoint(int x, int y, int z) {
	public SchematicPoint above() {
		return new SchematicPoint(x, y + 1, z);
	}
}
