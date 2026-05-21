package de.jesse.schaltplanmod.mixin.client;

import de.jesse.schaltplanmod.client.RedstoneLoadingTipRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReceivingLevelScreen.class)
public abstract class ReceivingLevelScreenMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void circuit$renderRedstoneTip(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo info) {
		RedstoneLoadingTipRenderer.render(graphics);
	}
}
