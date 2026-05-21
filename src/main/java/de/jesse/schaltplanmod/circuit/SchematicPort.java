package de.jesse.schaltplanmod.circuit;

public record SchematicPort(
		PortRole role,
		SchematicPoint markerPosition,
		SchematicPoint connectionPosition,
		PortSide side
) {
}
