package de.jesse.schaltplanmod.client;

import java.util.ArrayList;
import java.util.List;

public final class SchaltplanEditorState {
	private static final List<PlacedComponent> PLACED_COMPONENTS = new ArrayList<>();

	private SchaltplanEditorState() {
	}

	public static List<PlacedComponent> placedComponents() {
		return PLACED_COMPONENTS;
	}
}
