package de.jesse.schaltplanmod.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;

import java.util.List;

public final class RedstoneTipProvider {
	private static final List<String> TIPS_EN = List.of(
			"Redstone runs on 20 game ticks per second. One redstone tick is 2 game ticks.",
			"Redstone dust can carry signal strength 0-15. Long wires need repeaters before the signal fades.",
			"Repeaters add 1-4 redstone ticks of delay and isolate signal direction.",
			"Observers emit a short pulse when the watched block changes.",
			"Comparators can compare or subtract signal strengths, which makes them useful for analog logic.",
			"Redstone torches invert signals, but very fast clocks can burn them out.",
			"NAND and NOR gates are universal: every other logic gate can be built from either one.",
			"XOR is essential for adders because it produces the sum bit without the carry.",
			"Use repeaters to prevent backfeed between inputs.",
			"Large circuits become easier when adders, latches, and decoders are saved as modules."
	);
	private static final List<String> TIPS_DE = List.of(
			"Redstone laeuft mit 20 Spielticks pro Sekunde. Ein Redstone-Tick entspricht 2 Spielticks.",
			"Redstone-Staub traegt Signalstaerke 0-15. Lange Leitungen brauchen rechtzeitig Repeater.",
			"Repeater fuegen 1-4 Redstone-Ticks Verzoegerung hinzu und isolieren die Signalrichtung.",
			"Observer senden einen kurzen Puls, wenn sich der beobachtete Block veraendert.",
			"Comparatoren koennen Signalstaerken vergleichen oder subtrahieren und sind wichtig fuer analoge Logik.",
			"Redstone-Fackeln invertieren Signale, aber sehr schnelle Takte koennen sie ausbrennen lassen.",
			"XOR ist fuer Addierer zentral: Es erzeugt das Summenbit ohne Carry.",
			"Speichere Addierer, Latches und Decoder als Module, damit grosse Schaltungen beherrschbar bleiben."
	);

	private RedstoneTipProvider() {
	}

	public static String loadingTip() {
		List<String> tips = tips();
		int index = Math.floorMod((int) (Util.getMillis() / 4500L), tips.size());
		return tips.get(index);
	}

	public static List<String> tips() {
		return isGerman() ? TIPS_DE : TIPS_EN;
	}

	private static boolean isGerman() {
		Minecraft minecraft = Minecraft.getInstance();
		String language = minecraft == null || minecraft.options == null ? "" : minecraft.options.languageCode;
		return language != null && language.toLowerCase(java.util.Locale.ROOT).startsWith("de");
	}
}
