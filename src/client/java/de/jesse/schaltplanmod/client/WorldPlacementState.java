package de.jesse.schaltplanmod.client;

import net.minecraft.core.BlockPos;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class WorldPlacementState {
	private static final Map<BlockPos, String> LAST_PLACED_BLOCKS = new LinkedHashMap<>();
	private static BlockPos origin;

	private WorldPlacementState() {
	}

	public static Map<BlockPos, String> lastPlacedBlocks() {
		return Collections.unmodifiableMap(LAST_PLACED_BLOCKS);
	}

	public static void replace(Set<BlockPos> placedBlocks) {
		LAST_PLACED_BLOCKS.clear();
		for (BlockPos placedBlock : placedBlocks) {
			LAST_PLACED_BLOCKS.put(placedBlock, "");
		}
	}

	public static void replace(Map<BlockPos, String> placedBlocks) {
		LAST_PLACED_BLOCKS.clear();
		LAST_PLACED_BLOCKS.putAll(placedBlocks);
	}

	public static BlockPos originOrSet(BlockPos newOrigin) {
		if (origin == null) {
			origin = newOrigin;
		}
		return origin;
	}

	public static BlockPos origin() {
		return origin;
	}

	public static void resetOrigin() {
		origin = null;
	}

	public static void clearPlacedBlocks() {
		LAST_PLACED_BLOCKS.clear();
	}

	public static void clear() {
		LAST_PLACED_BLOCKS.clear();
		origin = null;
	}
}
