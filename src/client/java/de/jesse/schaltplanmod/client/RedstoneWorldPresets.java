package de.jesse.schaltplanmod.client;

import de.jesse.schaltplanmod.SchaltplanMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPreset;

public final class RedstoneWorldPresets {
	public static final ResourceKey<FlatLevelGeneratorPreset> REDSTONE_LAB = ResourceKey.create(
			Registries.FLAT_LEVEL_GENERATOR_PRESET,
			ResourceLocation.fromNamespaceAndPath(SchaltplanMod.MOD_ID, "redstone_lab"));

	private RedstoneWorldPresets() {
	}
}
