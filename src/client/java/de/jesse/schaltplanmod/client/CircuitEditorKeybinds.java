package de.jesse.schaltplanmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class CircuitEditorKeybinds {
	private static KeyMapping openEditor;

	private CircuitEditorKeybinds() {
	}

	public static void register() {
		openEditor = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.schaltplanmod.open_editor",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_PERIOD,
				"key.categories.schaltplanmod"
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openEditor.consumeClick()) {
				if (client.screen == null) {
					openEditor(null);
				}
			}
		});
	}

	public static void openFromCurrentScreen() {
		Minecraft client = Minecraft.getInstance();
		openEditor(client.screen);
	}

	private static void openEditor(net.minecraft.client.gui.screens.Screen parent) {
		Minecraft client = Minecraft.getInstance();
		client.setScreen(CircuitTutorialState.shouldShowTutorial()
				? new CircuitTutorialScreen(parent)
				: new SchaltplanEditorScreen(parent));
	}
}
