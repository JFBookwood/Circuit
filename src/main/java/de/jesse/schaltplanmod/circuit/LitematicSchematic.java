package de.jesse.schaltplanmod.circuit;

import java.util.List;

public record LitematicSchematic(
		CircuitComponentType type,
		String regionName,
		LitematicSize size,
		List<SchematicBlock> blocks,
		List<SchematicPort> ports,
		String customDisplayName
) {
	public LitematicSchematic(CircuitComponentType type, String regionName, LitematicSize size, List<SchematicBlock> blocks, List<SchematicPort> ports) {
		this(type, regionName, size, blocks, ports, null);
	}

	public String displayName() {
		return customDisplayName == null || customDisplayName.isBlank() ? type.displayName() : customDisplayName;
	}

	public List<SchematicPort> inputs() {
		return ports.stream().filter(port -> port.role() == PortRole.INPUT).toList();
	}

	public List<SchematicPort> outputs() {
		return ports.stream().filter(port -> port.role() == PortRole.OUTPUT).toList();
	}
}
