package de.jesse.schaltplanmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

public final class RedstoneWorldPresetFlow {
	private static boolean pending;

	private RedstoneWorldPresetFlow() {
	}

	public static void open(Minecraft minecraft, Screen parent) {
		pending = true;
		CreateWorldScreen.openFresh(minecraft, parent);
	}

	public static boolean consumePending() {
		if (!pending) {
			return false;
		}
		pending = false;
		return true;
	}

	public static boolean isPending() {
		return pending;
	}

	public static void apply(WorldCreationUiState uiState) {
		uiState.setName("Circuit Redstone Lab");
		uiState.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
		uiState.setDifficulty(Difficulty.PEACEFUL);
		uiState.setAllowCommands(true);
		uiState.setGenerateStructures(false);
		uiState.setBonusChest(false);
		uiState.setWorldType(uiState.getNormalPresetList().stream()
				.filter(entry -> entry.preset().is(WorldPresets.FLAT))
				.findFirst()
				.orElse(uiState.getWorldType()));

		GameRules rules = uiState.getGameRules().copy();
		set(rules, GameRules.RULE_KEEPINVENTORY, true);
		set(rules, GameRules.RULE_DOMOBSPAWNING, false);
		set(rules, GameRules.RULE_DAYLIGHT, false);
		set(rules, GameRules.RULE_WEATHER_CYCLE, false);
		set(rules, GameRules.RULE_DO_TRADER_SPAWNING, false);
		set(rules, GameRules.RULE_DO_PATROL_SPAWNING, false);
		set(rules, GameRules.RULE_DOINSOMNIA, false);
		set(rules, GameRules.RULE_MOBGRIEFING, false);
		set(rules, GameRules.RULE_COMMANDBLOCKOUTPUT, false);
		set(rules, GameRules.RULE_RANDOMTICKING, 0);
		set(rules, GameRules.RULE_SPAWN_RADIUS, 0);
		uiState.setGameRules(rules);
	}

	private static void set(GameRules rules, GameRules.Key<GameRules.BooleanValue> key, boolean value) {
		rules.getRule(key).set(value, null);
	}

	private static void set(GameRules rules, GameRules.Key<GameRules.IntegerValue> key, int value) {
		rules.getRule(key).set(value, null);
	}
}
