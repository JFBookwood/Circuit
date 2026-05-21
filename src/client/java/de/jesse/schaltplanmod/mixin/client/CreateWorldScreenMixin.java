package de.jesse.schaltplanmod.mixin.client;

import de.jesse.schaltplanmod.client.RedstoneWorldPresetFlow;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {
	@Shadow
	public abstract WorldCreationUiState getUiState();

	@Inject(method = "init", at = @At("TAIL"))
	private void circuit$applyRedstoneWorldPreset(CallbackInfo info) {
		if (RedstoneWorldPresetFlow.consumePending()) {
			RedstoneWorldPresetFlow.apply(getUiState());
		}
	}
}
