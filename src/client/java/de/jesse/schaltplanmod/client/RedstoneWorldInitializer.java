package de.jesse.schaltplanmod.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Queue;

public final class RedstoneWorldInitializer {
	private static final int PLATFORM_RADIUS = 32;
	private static final int PLATFORM_DEPTH = 50;
	private static final int FILL_HEIGHT_PER_COMMAND = 7;
	private static final Queue<String> COMMAND_QUEUE = new ArrayDeque<>();
	private static boolean pending;
	private static boolean running;
	private static int startupDelay;

	private RedstoneWorldInitializer() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(RedstoneWorldInitializer::tick);
	}

	public static void prepareNextJoinedWorld() {
		pending = true;
		running = false;
		startupDelay = 40;
		COMMAND_QUEUE.clear();
	}

	public static void prepareCurrentWorld() {
		pending = true;
		running = false;
		startupDelay = 1;
		COMMAND_QUEUE.clear();
	}

	private static void tick(Minecraft client) {
		if (client.player == null || client.getConnection() == null) {
			return;
		}

		if (pending && !running) {
			if (startupDelay-- > 0) {
				return;
			}
			queueSetupCommands(client);
			pending = false;
			running = true;
			client.player.displayClientMessage(Component.literal("Preparing Circuit redstone world..."), false);
		}

		if (running) {
			for (int index = 0; index < 4 && !COMMAND_QUEUE.isEmpty(); index++) {
				client.getConnection().sendCommand(COMMAND_QUEUE.remove());
			}

			if (COMMAND_QUEUE.isEmpty()) {
				running = false;
				client.player.displayClientMessage(Component.literal("Circuit redstone world is ready."), false);
			}
		}
	}

	private static void queueSetupCommands(Minecraft client) {
		int x = client.player.blockPosition().getX();
		int z = client.player.blockPosition().getZ();
		int floorY = client.player.blockPosition().getY() - 1;

		COMMAND_QUEUE.add("gamerule keepInventory true");
		COMMAND_QUEUE.add("gamerule doMobSpawning false");
		COMMAND_QUEUE.add("gamerule doDaylightCycle false");
		COMMAND_QUEUE.add("gamerule doWeatherCycle false");
		COMMAND_QUEUE.add("gamerule doTraderSpawning false");
		COMMAND_QUEUE.add("gamerule doPatrolSpawning false");
		COMMAND_QUEUE.add("gamerule doInsomnia false");
		COMMAND_QUEUE.add("gamerule mobGriefing false");
		COMMAND_QUEUE.add("gamerule commandBlockOutput false");
		COMMAND_QUEUE.add("difficulty peaceful");
		COMMAND_QUEUE.add("time set noon");
		COMMAND_QUEUE.add("weather clear");
		COMMAND_QUEUE.add("gamemode creative @s");
		COMMAND_QUEUE.add("spawnpoint @s " + x + " " + (floorY + 1) + " " + z);

		int minX = x - PLATFORM_RADIUS;
		int maxX = x + PLATFORM_RADIUS;
		int minZ = z - PLATFORM_RADIUS;
		int maxZ = z + PLATFORM_RADIUS;

		for (int y = floorY - PLATFORM_DEPTH + 1; y <= floorY; y += FILL_HEIGHT_PER_COMMAND) {
			int endY = Math.min(y + FILL_HEIGHT_PER_COMMAND - 1, floorY);
			COMMAND_QUEUE.add("fill " + minX + " " + y + " " + minZ + " " + maxX + " " + endY + " " + maxZ + " minecraft:iron_block replace");
		}

		for (int y = floorY + 1; y <= floorY + 21; y += FILL_HEIGHT_PER_COMMAND) {
			int endY = Math.min(y + FILL_HEIGHT_PER_COMMAND - 1, floorY + 21);
			COMMAND_QUEUE.add("fill " + minX + " " + y + " " + minZ + " " + maxX + " " + endY + " " + maxZ + " minecraft:air replace");
		}
	}
}
