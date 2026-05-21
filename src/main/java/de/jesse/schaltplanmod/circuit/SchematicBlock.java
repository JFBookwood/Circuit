package de.jesse.schaltplanmod.circuit;

import java.util.Map;
import java.util.stream.Collectors;

public record SchematicBlock(SchematicPoint position, String blockName, Map<String, String> properties) {
	public String blockStateString() {
		if (properties.isEmpty()) {
			return blockName;
		}

		return blockName + properties.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.map(entry -> entry.getKey() + "=" + entry.getValue())
				.collect(Collectors.joining(",", "[", "]"));
	}
}
